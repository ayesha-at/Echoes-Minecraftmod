package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;

import java.util.Collections;
import java.util.List;

/**
 * Plays a recording relative to the point where an Echo was summoned, and
 * re-bases the whole loop forward by a whole-block {@link LoopStride} each
 * time it wraps -- so a mining recording keeps extending its tunnel loop after
 * loop instead of re-digging the same one.
 *
 * <h2>Tick contract</h2>
 * Read first, advance last. Each server tick the caller does:
 * <pre>
 *   snapshot = getCurrentSnapshot();   // apply position/rotation
 *   actions  = getCurrentActions();    // run block interactions
 *   advance(SECONDS_PER_TICK);         // move on to the next tick
 * </pre>
 * The previous version advanced first, which meant snapshot index 0 -- and any
 * action attached to it -- was skipped on the FIRST loop but replayed on every
 * later one, so loop 1 quietly differed from loops 2..n.
 *
 * Contains no Minecraft types on purpose: all the looping, striding and
 * coordinate translation is plain Java, so it is covered by ordinary unit
 * tests rather than only by in-game trial and error.
 */
public final class EchoPlayback {

    /**
     * Safety cap on how many times the recording can re-loop before the Echo
     * retires itself, even if nothing else ever stops it (for instance a
     * zero-stride recording that would otherwise run forever). Deliberately
     * huge -- a backstop, not a design limit.
     */
    public static final int MAX_LOOPS = 100_000;

    private final EchoRecording recording;

    /** Feet position of the FIRST frame of loop 0. */
    private final double originX;
    private final double originY;
    private final double originZ;

    /** Block the recorded EchoAction offsets are measured from, for loop 0. */
    private final int originBlockX;
    private final int originBlockY;
    private final int originBlockZ;

    private final LoopStride stride;

    private double tickProgress = 0.0;
    private int completedLoops = 0;

    public EchoPlayback(EchoRecording recording, double originX, double originY, double originZ) {
        if (recording == null || recording.isEmpty()) {
            throw new IllegalArgumentException("EchoPlayback requires a non-empty recording");
        }
        this.recording = recording;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        // The same rule BlockPos.containing() uses: floor, not truncate, so
        // this stays correct either side of zero. Truncating would map -0.5 to
        // 0 and shift every action by a block in the negative quadrants.
        this.originBlockX = (int) Math.floor(originX);
        this.originBlockY = (int) Math.floor(originY);
        this.originBlockZ = (int) Math.floor(originZ);
        this.stride = LoopStride.of(recording);
    }

    public LoopStride getStride() { return stride; }
    public int getCompletedLoops() { return completedLoops; }
    public boolean hasExceededMaxLoops() { return completedLoops >= MAX_LOOPS; }

    /** Index into the recording for the tick currently being replayed. */
    public int getCurrentTickIndex() {
        return Math.min((int) Math.floor(tickProgress), recording.getSnapshotCount() - 1);
    }

    /**
     * Monotonically increasing tick counter across every loop. Used to fire
     * each tick's actions exactly once: a plain per-loop index wraps, so a
     * one-snapshot recording would have reported index 0 forever and never
     * replayed its action again after the very first tick.
     */
    public long getAbsoluteTick() {
        return (long) completedLoops * recording.getSnapshotCount() + getCurrentTickIndex();
    }

    public void reset() {
        tickProgress = 0.0;
        completedLoops = 0;
    }

    /** Origin block for THIS loop -- shifts by the stride on every wrap. */
    public int getCurrentOriginBlockX() { return originBlockX + completedLoops * stride.x; }
    public int getCurrentOriginBlockY() { return originBlockY + completedLoops * stride.y; }
    public int getCurrentOriginBlockZ() { return originBlockZ + completedLoops * stride.z; }

    /**
     * Steps forward by {@code deltaSeconds}. Returns true if the recording
     * wrapped (a loop completed) during this call.
     */
    public boolean advance(double deltaSeconds) {
        int count = recording.getSnapshotCount();
        tickProgress += deltaSeconds * EchoRecording.TICKS_PER_SECOND;
        boolean wrapped = false;
        // A while loop, not an if: one huge delta (a lag spike, or a recording
        // shorter than the step) must not leave tickProgress stranded past the
        // end of the recording.
        while (tickProgress >= count) {
            tickProgress -= count;
            completedLoops++;
            wrapped = true;
        }
        if (tickProgress < 0.0) {
            tickProgress = 0.0;
        }
        return wrapped;
    }

    /** Current frame, translated into world space for this loop. */
    public EchoSnapshot getCurrentSnapshot() {
        List<EchoSnapshot> snapshots = recording.getSnapshots();
        if (snapshots.isEmpty()) {
            return null;
        }
        EchoSnapshot raw;
        if (snapshots.size() == 1) {
            raw = snapshots.get(0);
        } else {
            int lowerIndex = Math.min((int) Math.floor(tickProgress), snapshots.size() - 1);
            int upperIndex = Math.min(lowerIndex + 1, snapshots.size() - 1);
            float t = (float) (tickProgress - lowerIndex);
            raw = interpolate(snapshots.get(lowerIndex), snapshots.get(upperIndex), t);
        }
        return translate(raw);
    }

    /**
     * Every discrete action attached to the current tick -- usually empty,
     * occasionally one, rarely more than one.
     */
    public List<EchoAction> getCurrentActions() {
        List<EchoSnapshot> snapshots = recording.getSnapshots();
        if (snapshots.isEmpty()) {
            return Collections.emptyList();
        }
        return snapshots.get(getCurrentTickIndex()).actions;
    }

    private EchoSnapshot translate(EchoSnapshot raw) {
        EchoSnapshot start = recording.getFirst();
        double advanceX = (double) completedLoops * stride.x;
        double advanceY = (double) completedLoops * stride.y;
        double advanceZ = (double) completedLoops * stride.z;
        return new EchoSnapshot(
                originX + advanceX + (raw.x - start.x),
                originY + advanceY + (raw.y - start.y),
                originZ + advanceZ + (raw.z - start.z),
                raw.yaw, raw.pitch,
                raw.velocityX, raw.velocityY, raw.velocityZ,
                raw.sprinting, raw.sneaking, raw.jumping, raw.hotbarSlot, raw.actions
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
        // Discrete state cannot be blended -- snap it to whichever snapshot is
        // nearer rather than inventing a half-sneaking Echo.
        EchoSnapshot nearer = t < 0.5f ? a : b;
        return new EchoSnapshot(x, y, z, yaw, pitch, vx, vy, vz,
                nearer.sprinting, nearer.sneaking, nearer.jumping, nearer.hotbarSlot, nearer.actions);
    }

    private static double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /**
     * Shortest-arc angle lerp. Equivalent to Mth.wrapDegrees, inlined to keep
     * this class free of Minecraft imports. A naive lerp from 170 to -170
     * would sweep 340 degrees the wrong way round.
     */
    private static float lerpAngleDegrees(float a, float b, float t) {
        float delta = (b - a) % 360.0f;
        if (delta >= 180.0f) delta -= 360.0f;
        if (delta < -180.0f) delta += 360.0f;
        return a + delta * t;
    }
}
