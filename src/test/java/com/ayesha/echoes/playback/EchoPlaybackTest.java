package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static com.ayesha.echoes.Recordings.at;
import static com.ayesha.echoes.Recordings.breakAt;
import static com.ayesha.echoes.Recordings.of;
import static com.ayesha.echoes.Recordings.walkingTunnel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EchoPlayback — looping, striding and coordinate translation")
class EchoPlaybackTest {

    private static final double TICK = 1.0 / EchoRecording.TICKS_PER_SECOND;
    private static final double EPSILON = 1e-9;

    /** Runs one full loop of a `length`-snapshot recording. */
    private static void runLoops(EchoPlayback playback, int length, int loops) {
        for (int loop = 0; loop < loops; loop++) {
            for (int tick = 0; tick < length; tick++) {
                playback.advance(TICK);
            }
        }
    }

    @Nested
    @DisplayName("the tick contract: read first, advance last")
    class TickContract {

        @Test
        @DisplayName("visits every index exactly once per loop, starting at 0")
        void visitsEveryIndexOncePerLoop() {
            EchoPlayback playback = new EchoPlayback(
                    of(at(0, 64, 0), at(0, 64, 1), at(0, 64, 2), at(0, 64, 3)), 0.5, 64, 0.5);

            List<Integer> visited = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                visited.add(playback.getCurrentTickIndex());
                playback.advance(TICK);
            }
            assertEquals(List.of(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3), visited);
            assertEquals(3, playback.getCompletedLoops());
        }

        @Test
        @DisplayName("the first snapshot's action fires on the first loop, not just later ones")
        void firstSnapshotActionFiresImmediately() {
            // The old code advanced before reading, so index 0 was skipped on
            // loop 1 and replayed on loops 2..n -- loop 1 quietly differed.
            EchoPlayback playback = new EchoPlayback(
                    of(at(0, 64, 0, breakAt(0, 0, 0)), at(0, 64, 1)), 0.5, 64, 0.5);
            assertEquals(1, playback.getCurrentActions().size());
        }

        @Test
        @DisplayName("the absolute tick is unique across loops, so actions cannot double-fire or be skipped")
        void absoluteTickIsUniquePerReplayedTick() {
            EchoPlayback playback = new EchoPlayback(of(at(0, 64, 0), at(0, 64, 1)), 0, 64, 0);
            Set<Long> seen = new LinkedHashSet<>();
            for (int i = 0; i < 20; i++) {
                seen.add(playback.getAbsoluteTick());
                playback.advance(TICK);
            }
            assertEquals(20, seen.size());
        }

        @Test
        @DisplayName("a one-snapshot recording keeps looping instead of freezing")
        void singleSnapshotStillLoops() {
            // Previously `if (count < 2) { tickProgress = 0; return; }` meant a
            // one-tick recording never completed a loop: the Echo froze and its
            // action never fired again.
            EchoPlayback playback = new EchoPlayback(of(at(0.5, 64, 0.5, breakAt(0, 0, 1))), 0.5, 64, 0.5);
            Set<Long> ticks = new LinkedHashSet<>();
            for (int i = 0; i < 5; i++) {
                ticks.add(playback.getAbsoluteTick());
                playback.advance(TICK);
            }
            assertEquals(5, playback.getCompletedLoops());
            assertEquals(5, ticks.size(), "the action must be replayable on every loop");
        }

        @Test
        @DisplayName("a lag spike larger than the recording wraps cleanly")
        void lagSpikeWrapsCleanly() {
            // A single `if` would have left tickProgress stranded past the end.
            EchoPlayback playback = new EchoPlayback(
                    of(at(0, 64, 0), at(0, 64, 1), at(0, 64, 2), at(0, 64, 3)), 0, 64, 0);
            playback.advance(10.0); // 200 ticks at once over a 4-tick recording
            assertEquals(50, playback.getCompletedLoops());
            assertEquals(0, playback.getCurrentTickIndex());
        }
    }

    @Nested
    @DisplayName("striding forward forever")
    class Striding {

        @Test
        @DisplayName("the Echo does not sink, however many loops it runs")
        void neverSinks() {
            EchoRecording tunnel = walkingTunnel();
            EchoPlayback playback = new EchoPlayback(tunnel, 100.5, 64.0, 200.5);

            double startY = playback.getCurrentSnapshot().y;
            runLoops(playback, tunnel.getSnapshotCount(), 500);
            double endY = playback.getCurrentSnapshot().y;

            assertEquals(startY, endY, EPSILON, "500 loops must not change the Echo's height");
        }

        @Test
        @DisplayName("the Echo keeps moving forward instead of resetting to the summon point")
        void advancesForwardEveryLoop() {
            EchoRecording tunnel = walkingTunnel();
            EchoPlayback playback = new EchoPlayback(tunnel, 100.5, 64.0, 200.5);

            double startZ = playback.getCurrentSnapshot().z;
            runLoops(playback, tunnel.getSnapshotCount(), 200);

            assertEquals(startZ + 200 * 5, playback.getCurrentSnapshot().z, EPSILON);
        }

        @Test
        @DisplayName("action targets keep pace with the Echo, loop after loop")
        void actionOriginTracksTheLoop() {
            EchoRecording tunnel = walkingTunnel();
            EchoPlayback playback = new EchoPlayback(tunnel, 100.5, 64.0, 200.5);

            assertEquals(200, playback.getCurrentOriginBlockZ());
            assertEquals(64, playback.getCurrentOriginBlockY());

            runLoops(playback, tunnel.getSnapshotCount(), 1);
            assertEquals(205, playback.getCurrentOriginBlockZ(), "loop 1 digs the next five columns");
            assertEquals(64, playback.getCurrentOriginBlockY(), "and at the same height");

            runLoops(playback, tunnel.getSnapshotCount(), 9);
            assertEquals(250, playback.getCurrentOriginBlockZ());
            assertEquals(64, playback.getCurrentOriginBlockY());
        }

        @Test
        @DisplayName("no sideways jog even after ten thousand loops")
        void noSidewaysJogOverManyLoops() {
            EchoRecording noisy = of(
                    at(0.00, 64, 0.0), at(0.13, 64, 2.1), at(0.28, 64, 4.0), at(0.40, 64, 4.9));
            EchoPlayback playback = new EchoPlayback(noisy, 100.5, 64.0, 200.5);

            int baselineX = playback.getCurrentOriginBlockX();
            for (int loop = 0; loop < 10_000; loop++) {
                runLoops(playback, noisy.getSnapshotCount(), 1);
                assertEquals(baselineX, playback.getCurrentOriginBlockX(),
                        "the bridge must stay straight on loop " + loop);
            }
            assertEquals(200 + 10_000 * 5, playback.getCurrentOriginBlockZ());
        }

        @Test
        @DisplayName("a zero-stride recording loops in place without drifting")
        void zeroStrideStaysPut() {
            EchoRecording dance = of(at(0, 64, 0), at(0, 64, 0.3), at(0, 64, 0.1), at(0, 64, 0));
            EchoPlayback playback = new EchoPlayback(dance, 10.5, 64.0, 10.5);

            EchoSnapshot before = playback.getCurrentSnapshot();
            runLoops(playback, dance.getSnapshotCount(), 1000);
            EchoSnapshot after = playback.getCurrentSnapshot();

            assertEquals(before.x, after.x, EPSILON);
            assertEquals(before.y, after.y, EPSILON);
            assertEquals(before.z, after.z, EPSILON);
        }
    }

    @Nested
    @DisplayName("coordinate translation")
    class Translation {

        @Test
        @DisplayName("the first frame lands exactly on the summon point")
        void firstFrameLandsOnSummonPoint() {
            EchoPlayback playback = new EchoPlayback(walkingTunnel(), -42.5, 71.0, 7.5);
            EchoSnapshot first = playback.getCurrentSnapshot();
            assertEquals(-42.5, first.x, EPSILON);
            assertEquals(71.0, first.y, EPSILON);
            assertEquals(7.5, first.z, EPSILON);
        }

        @Test
        @DisplayName("the recording's own shape is preserved, only shifted")
        void preservesRecordingShape() {
            EchoRecording walk = of(at(0, 64, 0), at(0, 66.5, 2), at(0, 64, 4.0));
            EchoPlayback playback = new EchoPlayback(walk, 500.5, 100.0, 500.5);

            playback.advance(TICK);
            EchoSnapshot mid = playback.getCurrentSnapshot();
            assertEquals(102.5, mid.y, EPSILON, "the recorded jump height must survive translation");
            assertEquals(502.5, mid.z, EPSILON);
        }

        @Test
        @DisplayName("origin blocks floor rather than truncate, so negative coordinates are not shifted")
        void negativeOriginsFloor() {
            // BlockPos.containing() floors. Truncating would map -0.5 to 0 and
            // put every action a block out in the negative quadrants.
            EchoPlayback playback = new EchoPlayback(of(at(0, 64, 0), at(0, 64, 1)), -0.5, 64.0, -10.5);
            assertEquals(-1, playback.getCurrentOriginBlockX());
            assertEquals(-11, playback.getCurrentOriginBlockZ());
        }
    }

    @Nested
    @DisplayName("rotation interpolation")
    class Rotation {

        @Test
        @DisplayName("yaw takes the short way round the 180 boundary")
        void yawTakesShortestArc() {
            EchoSnapshot a = new EchoSnapshot(0, 0, 0, 170f, 0f);
            EchoSnapshot b = new EchoSnapshot(0, 0, 0, -170f, 0f);
            float mid = EchoPlayback.interpolate(a, b, 0.5f).yaw;
            // Naive lerp would sweep through 0 (a 340-degree spin); the short
            // arc is 20 degrees through +/-180.
            assertTrue(Math.abs(Math.abs(mid) - 180f) < 0.001f, "expected ~±180 but was " + mid);
        }

        @Test
        @DisplayName("discrete state snaps to the nearer snapshot rather than blending")
        void discreteStateSnaps() {
            EchoSnapshot a = new EchoSnapshot(0, 0, 0, 0f, 0f, 0, 0, 0, false, false, false, 0, List.of());
            EchoSnapshot b = new EchoSnapshot(0, 0, 0, 0f, 0f, 0, 0, 0, true, true, true, 5, List.of());
            assertFalse(EchoPlayback.interpolate(a, b, 0.25f).sprinting);
            assertTrue(EchoPlayback.interpolate(a, b, 0.75f).sprinting);
            assertEquals(5, EchoPlayback.interpolate(a, b, 0.75f).hotbarSlot);
        }
    }

    @Nested
    @DisplayName("safety")
    class Safety {

        @Test
        @DisplayName("an empty recording is rejected with a clear message, not an index error")
        void emptyRecordingRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> new EchoPlayback(new EchoRecording(), 0, 0, 0));
            assertThrows(IllegalArgumentException.class,
                    () -> new EchoPlayback(null, 0, 0, 0));
        }

        @Test
        @DisplayName("the max-loop backstop eventually trips")
        void maxLoopBackstopTrips() {
            EchoPlayback playback = new EchoPlayback(of(at(0, 64, 0)), 0, 64, 0);
            assertFalse(playback.hasExceededMaxLoops());
            playback.advance(EchoPlayback.MAX_LOOPS / (double) EchoRecording.TICKS_PER_SECOND);
            assertTrue(playback.hasExceededMaxLoops());
        }

        @Test
        @DisplayName("reset() returns playback to its summon point")
        void resetReturnsToStart() {
            EchoRecording tunnel = walkingTunnel();
            EchoPlayback playback = new EchoPlayback(tunnel, 100.5, 64.0, 200.5);
            runLoops(playback, tunnel.getSnapshotCount(), 30);
            playback.reset();
            assertEquals(0, playback.getCompletedLoops());
            assertEquals(200, playback.getCurrentOriginBlockZ());
            assertEquals(200.5, playback.getCurrentSnapshot().z, EPSILON);
        }
    }
}
