package com.ayesha.echoes.playback;

import com.ayesha.echoes.recording.EchoRecording;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.ayesha.echoes.Recordings.at;
import static com.ayesha.echoes.Recordings.breakAt;
import static com.ayesha.echoes.Recordings.of;
import static com.ayesha.echoes.Recordings.placeAt;
import static com.ayesha.echoes.Recordings.walkingTunnel;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LoopStride — how far a recording advances per loop")
class LoopStrideTest {

    @Nested
    @DisplayName("regressions")
    class Regressions {

        @Test
        @DisplayName("a flat tunnel never gains a vertical stride (the 'drowning' bug)")
        void flatTunnelHasNoVerticalStride() {
            LoopStride stride = LoopStride.of(walkingTunnel());
            // The old implementation used lastAction.dy - firstAction.dy.
            // Head block broken first (dy=1), foot block last (dy=0) => -1 per
            // loop, so the Echo sank a block every loop until it hit bedrock
            // and died on the "unbreakable block" rule.
            assertEquals(0, stride.y, "a flat tunnel must not drift vertically");
        }

        @Test
        @DisplayName("stride matches the blocks actually dug, not the gap between first and last action")
        void strideMatchesBlocksDug() {
            LoopStride stride = LoopStride.of(walkingTunnel());
            // Five columns dug while walking five blocks => five blocks per loop.
            // lastAction.dz - firstAction.dz would have given 4, so every loop
            // re-dug the column the previous loop had already cleared.
            assertEquals(5, stride.z);
            assertEquals(0, stride.x);
        }

        @Test
        @DisplayName("off-axis strafe noise is rounded away instead of accumulating into a jog")
        void strafeNoiseDoesNotAccumulate() {
            EchoRecording noisy = of(
                    at(0.00, 64, 0.0),
                    at(0.13, 64, 2.1),
                    at(0.28, 64, 4.0),
                    at(0.40, 64, 4.9)
            );
            LoopStride stride = LoopStride.of(noisy);
            // 0.40 of accumulated strafe drift rounds to zero and STAYS zero
            // for every loop, however many there are -- an integer stride times
            // a loop count can never cross a block boundary by surprise.
            assertEquals(0, stride.x, "strafe noise must not become lateral movement");
            assertEquals(5, stride.z);
        }
    }

    @Nested
    @DisplayName("player displacement (the normal case)")
    class PlayerDisplacement {

        @Test
        @DisplayName("rounds fractional walking to whole blocks")
        void roundsToWholeBlocks() {
            LoopStride stride = LoopStride.of(of(at(0, 64, 0), at(0, 64, 7.62)));
            assertEquals(8, stride.z);
            assertEquals(LoopStride.Source.PLAYER_DISPLACEMENT, stride.source);
        }

        @Test
        @DisplayName("keeps a genuinely diagonal path diagonal")
        void keepsDiagonals() {
            LoopStride stride = LoopStride.of(of(at(0, 64, 0), at(4.9, 64, 5.1)));
            assertEquals(5, stride.x);
            assertEquals(5, stride.z);
        }

        @Test
        @DisplayName("keeps a genuine descent, e.g. a staircase mine")
        void keepsDeliberateDescent() {
            LoopStride stride = LoopStride.of(of(at(0, 64, 0), at(0, 61.1, 3.05)));
            assertEquals(-3, stride.y);
            assertEquals(3, stride.z);
        }

        @Test
        @DisplayName("negative directions stride negatively")
        void handlesNegativeDirections() {
            LoopStride stride = LoopStride.of(of(at(0, 64, 0), at(-6.1, 64, 0)));
            assertEquals(-6, stride.x);
        }
    }

    @Nested
    @DisplayName("stationary recordings fall back to the action span")
    class ActionSpanFallback {

        @Test
        @DisplayName("reach-mining without moving still advances")
        void reachMiningAdvances() {
            EchoRecording still = of(
                    at(10.5, 64, 10.5, breakAt(0, 1, 1)),
                    at(10.5, 64, 10.5, breakAt(0, 0, 1)),
                    at(10.5, 64, 10.5, breakAt(0, 1, 2), breakAt(0, 0, 2)),
                    at(10.5, 64, 10.5, breakAt(0, 1, 3), breakAt(0, 0, 3))
            );
            LoopStride stride = LoopStride.of(still);
            assertFalse(stride.isZero(), "a stationary dig should still automate");
            assertEquals(LoopStride.Source.ACTION_SPAN, stride.source);
            assertEquals(3, stride.z, "span of 2 (dz 1..3), +1 so loops sit flush");
            assertEquals(0, stride.y, "the 2-high profile must not become a descent");
            assertEquals(0, stride.x);
        }

        @Test
        @DisplayName("digging straight down strides downward — Y wins when it is genuinely deepest")
        void straightDownMineshaft() {
            LoopStride stride = LoopStride.of(of(
                    at(0.5, 64, 0.5, breakAt(0, -1, 0)),
                    at(0.5, 64, 0.5, breakAt(0, -2, 0)),
                    at(0.5, 64, 0.5, breakAt(0, -3, 0))
            ));
            assertEquals(-3, stride.y);
            assertEquals(0, stride.x);
            assertEquals(0, stride.z);
        }

        @Test
        @DisplayName("horizontal axes win ties against vertical")
        void horizontalWinsTies() {
            LoopStride stride = LoopStride.of(of(
                    at(0.5, 64, 0.5, breakAt(0, 0, 0)),
                    at(0.5, 64, 0.5, breakAt(0, 1, 1))
            ));
            assertEquals(0, stride.y, "an equal-span tie must not pick the vertical axis");
            assertEquals(2, stride.z);
        }

        @Test
        @DisplayName("a single action is not enough to infer a direction")
        void singleActionGivesNoStride() {
            LoopStride stride = LoopStride.of(of(at(0.5, 64, 0.5, breakAt(0, 0, 1))));
            assertTrue(stride.isZero());
        }

        @Test
        @DisplayName("repeatedly hitting the same block gives no stride")
        void repeatedSameBlockGivesNoStride() {
            LoopStride stride = LoopStride.of(of(
                    at(0.5, 64, 0.5, placeAt(0, 0, 1)),
                    at(0.5, 64, 0.5, breakAt(0, 0, 1)),
                    at(0.5, 64, 0.5, placeAt(0, 0, 1))
            ));
            assertTrue(stride.isZero(), "a zero span means nowhere to advance to");
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("a round trip loops in place")
        void roundTripLoopsInPlace() {
            LoopStride stride = LoopStride.of(of(at(0, 64, 0), at(0, 64, 3), at(0, 64, 0.2)));
            assertTrue(stride.isZero());
            assertEquals(LoopStride.Source.NONE, stride.source);
        }

        @Test
        @DisplayName("null and empty recordings give zero rather than throwing")
        void nullAndEmptyAreSafe() {
            assertTrue(LoopStride.of(null).isZero());
            assertTrue(LoopStride.of(new EchoRecording()).isZero());
        }

        @Test
        @DisplayName("a one-snapshot recording has nowhere to go")
        void singleSnapshot() {
            assertTrue(LoopStride.of(of(at(5, 64, 5))).isZero());
        }

        @Test
        @DisplayName("describe() reads sensibly in every direction")
        void describeReadsWell() {
            assertEquals("looping in place", LoopStride.ZERO.describe());
            assertTrue(LoopStride.of(of(at(0, 64, 0), at(0, 64, -5.0))).describe().contains("north"));
            assertTrue(LoopStride.of(of(at(0, 64, 0), at(5.0, 64, 0))).describe().contains("east"));
            assertTrue(LoopStride.of(of(at(0, 64, 0), at(0, 60.0, 0))).describe().contains("down"));
        }
    }
}
