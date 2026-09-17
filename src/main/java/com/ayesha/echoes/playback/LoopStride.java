package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;

import java.util.List;

/**
 * How far a recording advances each time it loops -- in WHOLE BLOCKS.
 *
 * This is the core of the automation mechanic: a loop that strides forward
 * keeps extending a tunnel/bridge instead of re-digging the same one.
 *
 * <h2>Why whole blocks</h2>
 * The previous implementation used raw {@code lastSnapshot.pos - firstSnapshot.pos}
 * as a double. Real walking is never perfectly axis-aligned (mouse look and
 * strafe always add a hair of off-axis drift), so {@code completedLoops * drift},
 * floored into a BlockPos each loop, eventually crossed an integer boundary
 * and produced a sudden one-block jog in an otherwise straight bridge.
 * Rounding the displacement to integers up front removes that class of bug
 * entirely: an integer stride multiplied by a loop count is exact forever.
 *
 * <h2>Why NOT the action span</h2>
 * The attempted fix for the jog was {@code lastAction - firstAction}. That
 * conflates two different quantities: how far apart the actions are INSIDE
 * one loop, versus how far the loop should move BETWEEN iterations. It is
 * wrong on every axis, but catastrophically so on Y -- mining a 2-high
 * tunnel where the first break is at head height and the last is at foot
 * height yields a Y span of -1, so the Echo sinks one block per loop,
 * tunnels into the ground, and dies on bedrock. That is the "drowning" bug.
 *
 * <h2>The stationary fallback</h2>
 * Rounded player displacement is right whenever the player actually walked.
 * It is {@code (0,0,0)} for a recording made standing still (reach-mining a
 * short tunnel, or digging straight down), which would loop in place and
 * never automate anything. For that case only, the stride is derived from
 * the BOUNDING SPAN of the recorded actions along their single dominant
 * axis -- one axis, so no off-axis sink or jog can sneak back in -- sized
 * {@code span + 1} so consecutive loops sit flush instead of overlapping.
 */
public final class LoopStride {

    public enum Source {
        /** Rounded net displacement of the player. The normal case. */
        PLAYER_DISPLACEMENT,
        /** Dominant-axis span of the recorded block actions. Stationary recordings. */
        ACTION_SPAN,
        /** No advance -- the Echo loops in place. Valid for pure-animation Echoes. */
        NONE
    }

    public static final LoopStride ZERO = new LoopStride(0, 0, 0, Source.NONE);

    public final int x;
    public final int y;
    public final int z;
    public final Source source;

    LoopStride(int x, int y, int z, Source source) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.source = (x == 0 && y == 0 && z == 0) ? Source.NONE : source;
    }

    public boolean isZero() {
        return x == 0 && y == 0 && z == 0;
    }

    public static LoopStride of(EchoRecording recording) {
        if (recording == null || recording.isEmpty()) {
            return ZERO;
        }
        EchoSnapshot first = recording.getFirst();
        EchoSnapshot last = recording.getLast();

        int dx = (int) Math.round(last.x - first.x);
        int dy = (int) Math.round(last.y - first.y);
        int dz = (int) Math.round(last.z - first.z);

        if (dx != 0 || dy != 0 || dz != 0) {
            return new LoopStride(dx, dy, dz, Source.PLAYER_DISPLACEMENT);
        }
        return fromActionSpan(recording.getSnapshots());
    }

    /**
     * Stride for a recording the player made without moving. Picks the axis
     * the actions spread furthest along and advances by that span + 1.
     * Horizontal axes win ties, so a 2-high tunnel dug forward advances
     * forward rather than downward; Y is only chosen when the dig is
     * genuinely deeper than it is long (a straight-down mineshaft).
     */
    private static LoopStride fromActionSpan(List<EchoSnapshot> snapshots) {
        int count = 0;
        EchoAction first = null;
        EchoAction last = null;
        int[] min = { Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE };
        int[] max = { Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE };

        for (EchoSnapshot snapshot : snapshots) {
            for (EchoAction action : snapshot.actions) {
                count++;
                if (first == null) {
                    first = action;
                }
                last = action;
                for (int axis = 0; axis < 3; axis++) {
                    int v = action.axis(axis);
                    if (v < min[axis]) min[axis] = v;
                    if (v > max[axis]) max[axis] = v;
                }
            }
        }

        if (count < 2) {
            return ZERO;
        }

        int spanX = max[0] - min[0];
        int spanY = max[1] - min[1];
        int spanZ = max[2] - min[2];
        if (spanX == 0 && spanY == 0 && spanZ == 0) {
            return ZERO;
        }

        int axis;
        int span;
        if (spanX >= spanZ && spanX >= spanY) {
            axis = 0;
            span = spanX;
        } else if (spanZ >= spanY) {
            axis = 2;
            span = spanZ;
        } else {
            axis = 1;
            span = spanY;
        }

        int direction = Integer.signum(last.axis(axis) - first.axis(axis));
        if (direction == 0) {
            direction = 1;
        }
        int magnitude = direction * (span + 1);

        return new LoopStride(
                axis == 0 ? magnitude : 0,
                axis == 1 ? magnitude : 0,
                axis == 2 ? magnitude : 0,
                Source.ACTION_SPAN
        );
    }

    /** Short human-readable form for the summon message, e.g. "5 blocks north". */
    public String describe() {
        if (isZero()) {
            return "looping in place";
        }
        StringBuilder sb = new StringBuilder("advancing ");
        boolean needsSeparator = false;
        if (x != 0) { sb.append(Math.abs(x)).append(x > 0 ? " east" : " west"); needsSeparator = true; }
        if (z != 0) { if (needsSeparator) sb.append(" + "); sb.append(Math.abs(z)).append(z > 0 ? " south" : " north"); needsSeparator = true; }
        if (y != 0) { if (needsSeparator) sb.append(" + "); sb.append(Math.abs(y)).append(y > 0 ? " up" : " down"); }
        return sb.append(" per loop").toString();
    }

    @Override
    public String toString() {
        return String.format("LoopStride[(%d, %d, %d) via %s]", x, y, z, source);
    }
}
