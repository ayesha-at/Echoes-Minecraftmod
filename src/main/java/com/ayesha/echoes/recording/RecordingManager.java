package com.ayesha.echoes.recording;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class RecordingManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("echoes");
    private static final RecordingManager INSTANCE = new RecordingManager();

    public static RecordingManager getInstance() {
        return INSTANCE;
    }

    private boolean recording = false;
    private EchoRecording currentRecording = null;
    private EchoRecording lastCompletedRecording = null;

    private RecordingManager() {
    }

    public boolean isRecording() {
        return recording;
    }

    public EchoRecording getCurrentRecording() {
        return currentRecording;
    }

    public EchoRecording getLastCompletedRecording() {
        return lastCompletedRecording;
    }

    public void toggle(Player player) {
        if (recording) {
            stop(player);
        } else {
            start(player);
        }
    }

    public void start(Player player) {
        if (recording) {
            return;
        }
        recording = true;
        currentRecording = new EchoRecording();
        LOGGER.info("Echo recording started.");
        player.sendSystemMessage(Component.literal("§b👁 Echo recording started"));
    }

    public void stop(Player player) {
        if (!recording) {
            return;
        }
        recording = false;
        lastCompletedRecording = currentRecording;
        currentRecording = null;

        LOGGER.info("Echo recording stopped: {}", lastCompletedRecording);
        player.sendSystemMessage(Component.literal(String.format(
                "§b👁 Echo recording saved — %.1fs (%d snapshots)",
                lastCompletedRecording.getDurationSeconds(),
                lastCompletedRecording.getSnapshotCount()
        )));
    }

    public void tick(Minecraft client) {
        if (!recording || client.player == null) {
            return;
        }

        boolean added = currentRecording.addSnapshot(EchoSnapshot.capture(client.player));

        if (!added || currentRecording.isAtMaxLength()) {
            stop(client.player);
        }
    }
}
