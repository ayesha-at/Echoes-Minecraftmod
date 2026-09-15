package com.ayesha.echoes.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecordingManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("echoes");
    private static final RecordingManager INSTANCE = new RecordingManager();

    public static RecordingManager getInstance() { return INSTANCE; }

    private boolean recording = false;
    private EchoRecording currentRecording = null;
    private EchoRecording lastCompletedRecording = null;
    private boolean attackWasDown;
    private boolean useWasDown;
    // Position the recording started at. EchoAction offsets are stored
    // relative to THIS, not to wherever the player happened to be standing
    // at the moment of the break/place -- that has to match the basis
    // EchoPlayback uses to translate snapshot positions (relative to
    // snapshot 0 / the summon point), or actions target the wrong block
    // entirely as soon as the player has moved since recording started.
    private BlockPos recordingOrigin;

    private RecordingManager() {}

    public boolean isRecording() { return recording; }
    public EchoRecording getCurrentRecording() { return currentRecording; }
    public EchoRecording getLastCompletedRecording() { return lastCompletedRecording; }

    public void toggle(Player player) {
        if (recording) stop(player); else start(player);
    }

    public void start(Player player) {
        if (recording) return;
        recording = true;
        currentRecording = new EchoRecording();
        recordingOrigin = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        attackWasDown = false;
        useWasDown = false;
        LOGGER.info("Echo recording started.");
        player.sendSystemMessage(Component.literal("§b👁 Echo recording started"));
    }

    public void stop(Player player) {
        if (!recording) return;
        recording = false;
        lastCompletedRecording = currentRecording;
        currentRecording = null;
        attackWasDown = false;
        useWasDown = false;
        LOGGER.info("Echo recording stopped: {}", lastCompletedRecording);
        player.sendSystemMessage(Component.literal(String.format(
                "§b👁 Echo recording saved — %.1fs (%d snapshots)",
                lastCompletedRecording.getDurationSeconds(), lastCompletedRecording.getSnapshotCount())));
    }

    public void tick(Minecraft client) {
        if (!recording || client.player == null) return;

        EchoAction action = captureAction(client);
        currentRecording.addSnapshot(EchoSnapshot.capture(client.player, action));

        if (currentRecording.isAtMaxLength()) stop(client.player);
    }

    private EchoAction captureAction(Minecraft client) {
        boolean attackDown = client.options.keyAttack.isDown();
        boolean useDown = client.options.keyUse.isDown();
        EchoAction action = null;

        if (client.hitResult instanceof BlockHitResult hit) {
            var pos = hit.getBlockPos();
            var state = client.level.getBlockState(pos);

            if (attackDown && !attackWasDown) {
                action = new EchoAction(
                        EchoAction.Type.BREAK_BLOCK,
                        pos.getX() - recordingOrigin.getX(),
                        pos.getY() - recordingOrigin.getY(),
                        pos.getZ() - recordingOrigin.getZ(),
                        EchoAction.blockId(state),
                        ""
                );
            } else if (useDown && !useWasDown) {
                ItemStack held = client.player.getMainHandItem();
                if (held.getItem() instanceof BlockItem) {
                    var target = pos.relative(hit.getDirection());
                    var targetState = client.level.getBlockState(target);
                    action = new EchoAction(
                            EchoAction.Type.PLACE_BLOCK,
                            target.getX() - recordingOrigin.getX(),
                            target.getY() - recordingOrigin.getY(),
                            target.getZ() - recordingOrigin.getZ(),
                            EchoAction.blockId(targetState),
                            held.getItem().builtInRegistryHolder().key().identifier().toString()
                    );
                }
            }
        }

        attackWasDown = attackDown;
        useWasDown = useDown;
        return action;
    }
}
