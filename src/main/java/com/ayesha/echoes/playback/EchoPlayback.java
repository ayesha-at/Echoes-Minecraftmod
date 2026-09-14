package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Plays back an {@link EchoRecording} over time, independent of recording.
 *
 * Deliberately knows nothing about RecordingManager, EchoEntity, or how it's
 * being rendered -- it just turns "how much time has passed" into "here's
 * the (interpolated) state at that point," and loops per the locked V1
 * scope. EchoEntity (Phase 3) will own one of these per active Echo and
 * read getCurrentSnapshot() each frame/tick.
 */
public final class EchoPlayback {

    private final EchoRecording recording;

    /** Fractional position within the recording, in ticks (20/sec). */
    private double tickProgress = 0.0;

    public EchoPlayback(EchoRecording recording) {
        this.recording = recording;
    }

    public void reset() {
        tickProgress = 0.0;
    }

    public int getCurrentTick() {
        return (int) Math.floor(tickProgress);
    }

    /**
     * Advances playback by real elapsed time. Loops back to the start once
     * it runs past the end of the recording, per the locked "Echoes loop"
     * V1 scope.
     */
    public void update(double deltaSeconds) {
        int snapshotCount = recording.getSnapshotCount();
        if (snapshotCount < 2) {
            // Nothing (or one frame) to interpolate between -- hold at 0.
            tickProgress = 0.0;
            return;
        }

        tickProgress += deltaSeconds * EchoRecording.TICKS_PER_SECOND;

        double loopLength = snapshotCount - 1; // last valid interpolation span
        if (tickProgress >= loopLength) {
            tickProgress = tickProgress % loopLength;
        }
    }

    /**
     * The (possibly interpolated) snapshot at the current playback position.
     * Returns null if the recording has no data.
     */
    public EchoSnapshot getCurrentSnapshot() {
        List<EchoSnapshot> snapshots = recording.getSnapshots();
        if (snapshots.isEmpty()) {
            return null;
        }
        if (snapshots.size() == 1) {
            return snapshots.get(0);
        }

        int lowerIndex = (int) Math.floor(tickProgress);
        int upperIndex = Math.min(lowerIndex + 1, snapshots.size() - 1);
        float t = (float) (tickProgress - lowerIndex);

        return interpolate(snapshots.get(lowerIndex), snapshots.get(upperIndex), t);
    }

    /**
     * Linearly interpolates continuous state (position/rotation/velocity)
     * between two snapshots. Discrete state (sprint/sneak/jump/hotbar slot)
     * doesn't blend -- it snaps to whichever snapshot t is closer to, since
     * "63% sneaking" isn't meaningful.
     *
     * Yaw uses shortest-path angle interpolation (via Mth.wrapDegrees) so a
     * turn from e.g. 170° to -170° doesn't spin the long way around.
     */
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

        return new EchoSnapshot(
                x, y, z,
                yaw, pitch,
                vx, vy, vz,
                nearer.sprinting, nearer.sneaking, nearer.jumping,
                nearer.hotbarSlot
        );
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float lerpAngleDegrees(float a, float b, float t) {
        float shortestDelta = Mth.wrapDegrees(b - a);
        return a + shortestDelta * t;
    }
}
