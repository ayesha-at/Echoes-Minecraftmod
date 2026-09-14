package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.List;

/** Plays a recording relative to the point where an Echo was summoned. */
public final class EchoPlayback {
    private final EchoRecording recording;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final BlockPos originBlock;
    private double tickProgress = 0.0;

    public EchoPlayback(EchoRecording recording, double originX, double originY, double originZ) {
        this.recording = recording;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.originBlock = BlockPos.containing(originX, originY, originZ);
    }

    public void reset() { tickProgress = 0.0; }
    public int getCurrentTick() { return (int) Math.floor(tickProgress); }
    public BlockPos getOriginBlock() { return originBlock; }

    public void update(double deltaSeconds) {
        int count = recording.getSnapshotCount();
        if (count < 2) { tickProgress = 0.0; return; }
        tickProgress += deltaSeconds * EchoRecording.TICKS_PER_SECOND;
        double loopLength = count - 1;
        if (tickProgress >= loopLength) tickProgress %= loopLength;
    }

    public EchoSnapshot getCurrentSnapshot() {
        List<EchoSnapshot> snapshots = recording.getSnapshots();
        if (snapshots.isEmpty()) return null;
        EchoSnapshot raw;
        if (snapshots.size() == 1) raw = snapshots.get(0);
        else {
            int lowerIndex = (int) Math.floor(tickProgress);
            int upperIndex = Math.min(lowerIndex + 1, snapshots.size() - 1);
            float t = (float) (tickProgress - lowerIndex);
            raw = interpolate(snapshots.get(lowerIndex), snapshots.get(upperIndex), t);
        }
        return translate(raw);
    }

    /** Returns the discrete action attached to the current snapshot tick. */
    public EchoAction getCurrentAction() {
        List<EchoSnapshot> snapshots = recording.getSnapshots();
        if (snapshots.isEmpty()) return null;
        return snapshots.get(Math.min(getCurrentTick(), snapshots.size() - 1)).action;
    }

    private EchoSnapshot translate(EchoSnapshot raw) {
        EchoSnapshot start = recording.getSnapshots().get(0);
        return new EchoSnapshot(
                originX + (raw.x - start.x),
                originY + (raw.y - start.y),
                originZ + (raw.z - start.z),
                raw.yaw, raw.pitch,
                raw.velocityX, raw.velocityY, raw.velocityZ,
                raw.sprinting, raw.sneaking, raw.jumping, raw.hotbarSlot, raw.action
        );
    }

    public static EchoSnapshot interpolate(EchoSnapshot a, EchoSnapshot b, float t) {
        double x = lerp(a.x, b.x, t);
        double y = lerp(a.y, b.y, t);
        double z = lerp(a.z, b.z, t);
        float yaw = lerpAngleDegrees(a.yaw, b.yaw, t);
        float pitch = lerp(a.pitch, b.pitch, t);
        double vx = lerp(a.velocityX, b.velocityX, t);
        double vy = lerp(a.velocityY, b.velocityY, t);
        double vz = lerp(a.velocityZ, b.velocityZ, t);
        EchoSnapshot nearer = t < 0.5f ? a : b;
        return new EchoSnapshot(x, y, z, yaw, pitch, vx, vy, vz,
                nearer.sprinting, nearer.sneaking, nearer.jumping, nearer.hotbarSlot, nearer.action);
    }

    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float lerpAngleDegrees(float a, float b, float t) {
        return a + Mth.wrapDegrees(b - a) * t;
    }
}
