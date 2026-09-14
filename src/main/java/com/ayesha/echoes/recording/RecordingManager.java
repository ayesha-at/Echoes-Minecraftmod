package com.ayesha.echoes.recording;

import net.minecraft.client.Minecraft;
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
                        pos.getX() - floorX(client.player.getX()),
                        pos.getY() - floorY(client.player.getY()),
                        pos.getZ() - floorZ(client.player.getZ()),
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
                            target.getX() - floorX(client.player.getX()),
                            target.getY() - floorY(client.player.getY()),
                            target.getZ() - floorZ(client.player.getZ()),
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

    private static int floorX(double x) { return (int) Math.floor(x); }
    private static int floorY(double y) { return (int) Math.floor(y); }
    private static int floorZ(double z) { return (int) Math.floor(z); }
}
