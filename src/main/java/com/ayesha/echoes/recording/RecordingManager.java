package com.ayesha.echoes.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Owns the live-world side of recording: samples the player once per client
 * tick and captures discrete block interactions as they happen.
 *
 * This is also where the live-player capture logic now lives -- the recording
 * and playback data classes were made Minecraft-free so they could be
 * unit-tested, so anything that touches a real Player belongs here.
 */
public final class RecordingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("echoes");
    private static final RecordingManager INSTANCE = new RecordingManager();

    public static RecordingManager getInstance() { return INSTANCE; }

    // Written from the client thread (start/stop, off ClientTickEvents) but
    // READ from the server thread inside recordBreakEvent(). Even in
    // singleplayer the integrated server runs on its own thread, so this is a
    // genuine cross-thread hand-off, not just a single-thread polling loop.
    private volatile boolean recording = false;
    private volatile BlockPos recordingOrigin;
    private volatile UUID recordingOwnerUuid;

    private EchoRecording currentRecording = null;
    private EchoRecording lastCompletedRecording = null;

    // BREAK_BLOCK actions arrive asynchronously from PlayerBlockBreakEvents
    // .AFTER on the server thread; tick() drains them on the client thread. A
    // queue, not a single slot: breaking a snow layer into the sand beneath it
    // destroys two blocks inside the same ~50ms window, and a single slot
    // silently dropped one of them.
    private final ConcurrentLinkedQueue<EchoAction> pendingBreakActions = new ConcurrentLinkedQueue<>();

    // Echoes replay breaks and places as the OWNER, so those events are
    // indistinguishable by UUID from the player doing it by hand. Without this
    // guard, an Echo mining in the background while the player starts a fresh
    // recording leaks its automated work into that new recording.
    private volatile boolean suppressEchoCapture = false;

    // PLACE_BLOCK has no stable "block placed" event in Fabric API, so it is
    // detected by watching whichever position the player is aiming a block item
    // at, then noticing on a later tick that the position turned into that
    // block. The watch is refreshed every tick so it survives a continuously
    // held right-click (bridging). Client thread only.
    private BlockPos watchedPlaceTarget;
    private String watchedPlaceItemId;
    // The actual Block the watched item places. Item and block ids are NOT
    // always the same -- minecraft:redstone places minecraft:redstone_wire --
    // so comparing the Block itself is both exact and cheaper than a registry
    // round-trip on the tick path.
    private Block watchedPlaceBlock;

    private RecordingManager() {}

    public boolean isRecording() { return recording; }
    public EchoRecording getCurrentRecording() { return currentRecording; }
    public EchoRecording getLastCompletedRecording() { return lastCompletedRecording; }

    public void toggle(Player player) {
        if (recording) stop(player); else start(player);
    }

    public void start(Player player) {
        if (recording) return;
        currentRecording = new EchoRecording();
        recordingOrigin = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        recordingOwnerUuid = player.getUUID();
        pendingBreakActions.clear();
        clearPlaceWatch();
        recording = true;
        LOGGER.info("Echo recording started at {}", recordingOrigin);
        player.sendSystemMessage(Component.literal("§b👁 Echo recording started"));
    }

    public void stop(Player player) {
        if (!recording) return;
        recording = false;
        EchoRecording finished = currentRecording;
        currentRecording = null;

        if (finished == null || finished.isEmpty()) {
            // Start and stop inside the same tick -- nothing was sampled. Keep
            // the previous recording rather than replacing a good one with an
            // unusable empty one.
            LOGGER.info("Echo recording discarded: nothing was captured.");
            player.sendSystemMessage(Component.literal("§7👁 Recording was empty — nothing saved."));
            return;
        }

        lastCompletedRecording = finished;
        LOGGER.info("Echo recording stopped: {}", finished);
        player.sendSystemMessage(Component.literal(String.format(
                "§b👁 Echo recording saved — %.1fs (%d snapshots, %d actions)",
                finished.getDurationSeconds(), finished.getSnapshotCount(), finished.getActionCount())));
    }

    /** Set by EchoEntity around its own world mutations during playback. */
    public void setSuppressEchoCapture(boolean suppress) {
        suppressEchoCapture = suppress;
    }

    /**
     * Registered against PlayerBlockBreakEvents.AFTER in EchoesMod. Fires once
     * per real break, on the server thread, regardless of how the mouse was
     * held -- which is what makes held-click mining record correctly.
     */
    public void recordBreakEvent(ServerPlayer player, BlockPos pos, BlockState state) {
        if (suppressEchoCapture || !recording) return;
        UUID owner = recordingOwnerUuid;
        BlockPos origin = recordingOrigin;
        if (owner == null || origin == null || !player.getUUID().equals(owner)) return;

        pendingBreakActions.add(new EchoAction(
                EchoAction.Type.BREAK_BLOCK,
                pos.getX() - origin.getX(),
                pos.getY() - origin.getY(),
                pos.getZ() - origin.getZ(),
                blockId(state),
                ""
        ));
    }

    public void tick(Minecraft client) {
        if (!recording || client.player == null || client.level == null) return;
        EchoRecording target = currentRecording;
        BlockPos origin = recordingOrigin;
        if (target == null || origin == null) return;

        List<EchoAction> actions = null;
        EchoAction breakAction;
        while ((breakAction = pendingBreakActions.poll()) != null) {
            if (actions == null) actions = new ArrayList<>(2);
            actions.add(breakAction);
        }
        EchoAction placeAction = detectPlacement(client, origin);
        if (placeAction != null) {
            if (actions == null) actions = new ArrayList<>(1);
            actions.add(placeAction);
        }

        target.addSnapshot(capture(client.player, actions == null ? Collections.emptyList() : actions));

        if (target.isAtMaxLength()) stop(client.player);
    }

    /** Samples a live player into plain snapshot data. */
    private static EchoSnapshot capture(Player player, List<EchoAction> actions) {
        boolean jumping = !player.onGround() && player.getDeltaMovement().y > 0.0;
        return new EchoSnapshot(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z,
                player.isSprinting(), player.isShiftKeyDown(), jumping,
                player.getInventory().getSelectedSlot(), actions
        );
    }

    private static String blockId(BlockState state) {
        return state.getBlock().builtInRegistryHolder().key().identifier().toString();
    }

    private EchoAction detectPlacement(Minecraft client, BlockPos origin) {
        EchoAction completed = null;

        // Did the position we were watching last tick turn into the block that
        // item places? Checking the specific Block -- rather than merely
        // "something solid appeared" -- means an Echo bridging through the same
        // position on the same tick cannot be misattributed to the player.
        if (watchedPlaceTarget != null && watchedPlaceBlock != null) {
            BlockState now = client.level.getBlockState(watchedPlaceTarget);
            if (now.is(watchedPlaceBlock)) {
                completed = new EchoAction(
                        EchoAction.Type.PLACE_BLOCK,
                        watchedPlaceTarget.getX() - origin.getX(),
                        watchedPlaceTarget.getY() - origin.getY(),
                        watchedPlaceTarget.getZ() - origin.getZ(),
                        blockId(now),
                        watchedPlaceItemId
                );
            }
            clearPlaceWatch();
        }

        // Start (or refresh) a watch on this tick's aim point.
        if (client.options.keyUse.isDown() && client.hitResult instanceof BlockHitResult hit) {
            ItemStack held = client.player.getMainHandItem();
            if (held.getItem() instanceof BlockItem blockItem) {
                BlockPos target = hit.getBlockPos().relative(hit.getDirection());
                if (client.level.getBlockState(target).canBeReplaced()) {
                    watchedPlaceTarget = target;
                    watchedPlaceBlock = blockItem.getBlock();
                    watchedPlaceItemId = held.getItem().builtInRegistryHolder().key().identifier().toString();
                }
            }
        }

        return completed;
    }

    private void clearPlaceWatch() {
        watchedPlaceTarget = null;
        watchedPlaceItemId = null;
        watchedPlaceBlock = null;
    }

    /**
     * Clears in-flight recording state on world (re)join or disconnect -- a
     * half-finished recording from a world you just left is meaningless, and a
     * quit mid-recording should not leave the HUD showing REC into the menu.
     *
     * The last COMPLETED recording is deliberately kept: snapshots are stored
     * as offsets and translated to the summon point, so a recording made in one
     * world replays fine in another, and silently binning it on every world
     * switch would be a nasty surprise.
     */
    public void reset() {
        recording = false;
        currentRecording = null;
        recordingOrigin = null;
        recordingOwnerUuid = null;
        pendingBreakActions.clear();
        clearPlaceWatch();
        suppressEchoCapture = false;
    }
}
