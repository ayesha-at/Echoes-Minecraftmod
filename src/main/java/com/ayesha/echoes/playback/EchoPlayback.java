package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Plays a recording relative to the point where an Echo was summoned.
 *
 * V1.1 automation change: each time the recording finishes a loop, it does
 * NOT reset back to the summon point. Instead the whole loop is re-based
 * forward by the recording's own net displacement (its last snapshot's
 * position minus its first), so a recording that walked forward while
 * mining keeps extending that tunnel loop after loop instead of re-digging
 * the same one. A recording that ends where it started (net displacement
 * ~0) just keeps looping in place, same as before.
 */
public final class EchoPlayback {

    /** Safety cap on how many times the recording can re-loop before the
     *  Echo is retired on its own, even if nothing else ever kills it
     *  (e.g. a recording with ~0 net displacement that would otherwise
     *  run forever). Deliberately huge -- this is a safety net, not a
     *  design limit. */
    public static final int MAX_LOOPS = 100_000;

    private final EchoRecording recording;
    private final double originX;
    private final double originY;
    private final double originZ;
    private final double netDispX;
    private final double netDispY;
    private final double netDispZ;
    private double tickProgress = 0.0;
    private int completedLoops = 0;

    public EchoPlayback(EchoRecording recording, double originX, double originY, double originZ) {
        this.recording = recording;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;

        List<EchoSnapshot> snaps = recording.getSnapshots();
        EchoSnapshot first = snaps.get(0);
        EchoSnapshot last = snaps.get(snaps.size() - 1);

        // Prefer deriving the per-loop advance from the recorded block
        // actions rather than the player's raw walking position. Raw
        // position is a continuous double and almost never perfectly
        // axis-aligned (mouse look/strafe always adds a hair of off-axis
        // drift, even when it "looks" straight). completedLoops * that
        // drift, floored into a BlockPos every loop, eventually crosses
        // an integer boundary and produces a sudden one-block jog --
        // a long straight bridge that abruptly turns. Block actions are
        // always whole-number offsets, so using two of them gives an
        // exact, drift-free direction instead.
        EchoAction firstAction = null;
        EchoAction lastAction = null;
        for (EchoSnapshot s : snaps) {
            if (s.action != null) {
                if (firstAction == null) firstAction = s.action;
                lastAction = s.action;
            }
        }

        if (firstAction != null && lastAction != null && firstAction != lastAction) {
            this.netDispX = lastAction.dx - firstAction.dx;
            this.netDispY = lastAction.dy - firstAction.dy;
            this.netDispZ = lastAction.dz - firstAction.dz;
        } else {
            this.netDispX = last.x - first.x;
            this.netDispY = last.y - first.y;
            this.netDispZ = last.z - first.z;
        }
    }

    public void reset() { tickProgress = 0.0; completedLoops = 0; }
    public int getCurrentTick() { return (int) Math.floor(tickProgress); }
    public int getCompletedLoops() { return completedLoops; }
    public boolean hasExceededMaxLoops() { return completedLoops >= MAX_LOOPS; }

    /** Block-space origin for THIS loop -- shifts forward each time the
     *  recording wraps, by completedLoops * netDisplacement. EchoActions
     *  are offset from this (not the fixed summon point), so break/place
     *  targets keep pace with wherever this loop has advanced to. */
    public BlockPos getCurrentOriginBlock() {
        return BlockPos.containing(
                originX + completedLoops * netDispX,
                originY + completedLoops * netDispY,
                originZ + completedLoops * netDispZ
        );
    }

    public void update(double deltaSeconds) {
        int count = recording.getSnapshotCount();
        if (count < 2) { tickProgress = 0.0; return; }
        tickProgress += deltaSeconds * EchoRecording.TICKS_PER_SECOND;
        // Loop over all `count` indices (0..count-1) so the last snapshot's
        // tick -- and any EchoAction attached to it -- actually gets read
        // once before wrapping, instead of being modulo'd away the instant
        // tickProgress reaches it.
        double loopLength = count;
        if (tickProgress >= loopLength) {
            tickProgress %= loopLength;
            completedLoops++;
        }
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
        double advanceX = completedLoops * netDispX;
        double advanceY = completedLoops * netDispY;
        double advanceZ = completedLoops * netDispZ;
        return new EchoSnapshot(
                originX + advanceX + (raw.x - start.x),
                originY + advanceY + (raw.y - start.y),
                originZ + advanceZ + (raw.z - start.z),
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
