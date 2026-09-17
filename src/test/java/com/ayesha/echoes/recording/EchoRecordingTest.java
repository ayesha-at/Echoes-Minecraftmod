package com.ayesha.echoes.recording;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.ayesha.echoes.Recordings.at;
import static com.ayesha.echoes.Recordings.breakAt;
import static com.ayesha.echoes.Recordings.of;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Recording data model")
class EchoRecordingTest {

    @Nested
    @DisplayName("EchoRecording")
    class Recording {

        @Test
        @DisplayName("caps at 60 seconds and refuses further snapshots")
        void capsAtSixtySeconds() {
            EchoRecording recording = new EchoRecording();
            for (int i = 0; i < EchoRecording.MAX_SNAPSHOTS; i++) {
                assertTrue(recording.addSnapshot(at(0, 64, 0)), "snapshot " + i + " should fit");
            }
            assertTrue(recording.isAtMaxLength());
            assertFalse(recording.addSnapshot(at(0, 64, 0)), "the cap must actually reject");
            assertEquals(EchoRecording.MAX_SNAPSHOTS, recording.getSnapshotCount());
            assertEquals(EchoRecording.MAX_SECONDS, recording.getDurationSeconds(), 1e-9);
        }

        @Test
        @DisplayName("ignores nulls rather than storing a hole")
        void ignoresNulls() {
            EchoRecording recording = new EchoRecording();
            assertFalse(recording.addSnapshot(null));
            assertTrue(recording.isEmpty());
        }

        @Test
        @DisplayName("an empty recording answers first/last without throwing")
        void emptyRecordingIsSafe() {
            EchoRecording recording = new EchoRecording();
            assertNull(recording.getFirst());
            assertNull(recording.getLast());
            assertEquals(0, recording.getActionCount());
            assertEquals(0.0, recording.getDurationSeconds(), 1e-9);
        }

        @Test
        @DisplayName("counts actions across every snapshot, including multiple in one tick")
        void countsActionsAcrossTicks() {
            EchoRecording recording = of(
                    at(0, 64, 0, breakAt(0, 0, 1), breakAt(0, 1, 1)),
                    at(0, 64, 1),
                    at(0, 64, 2, breakAt(0, 0, 2))
            );
            assertEquals(3, recording.getActionCount());
        }

        @Test
        @DisplayName("hands out a read-only view so playback cannot corrupt the recording")
        void snapshotListIsReadOnly() {
            EchoRecording recording = of(at(0, 64, 0));
            assertThrows(UnsupportedOperationException.class,
                    () -> recording.getSnapshots().add(at(0, 64, 1)));
        }

        @Test
        @DisplayName("first and last are the ends of the recording")
        void firstAndLast() {
            EchoRecording recording = of(at(0, 64, 0), at(0, 64, 1), at(0, 64, 2));
            assertEquals(0.0, recording.getFirst().z, 1e-9);
            assertEquals(2.0, recording.getLast().z, 1e-9);
        }
    }

    @Nested
    @DisplayName("EchoSnapshot")
    class Snapshot {

        @Test
        @DisplayName("a tick can carry more than one action")
        void carriesMultipleActionsPerTick() {
            // Breaking a snow layer exposes and destroys the block beneath it
            // inside the same 50ms window; a single nullable slot dropped one.
            EchoSnapshot snapshot = at(0, 64, 0, breakAt(0, 0, 1), breakAt(0, -1, 1));
            assertEquals(2, snapshot.actions.size());
        }

        @Test
        @DisplayName("actions are never null and never mutable")
        void actionsAreSafe() {
            EchoSnapshot none = new EchoSnapshot(0, 0, 0, 0f, 0f, 0, 0, 0, false, false, false, 0, null);
            assertTrue(none.actions.isEmpty());
            assertThrows(UnsupportedOperationException.class, () -> none.actions.add(breakAt(0, 0, 0)));
        }

        @Test
        @DisplayName("copies the caller's list so later mutation cannot rewrite history")
        void defensivelyCopiesActions() {
            List<EchoAction> live = new ArrayList<>();
            live.add(breakAt(0, 0, 1));
            EchoSnapshot snapshot = new EchoSnapshot(
                    0, 0, 0, 0f, 0f, 0, 0, 0, false, false, false, 0, live);
            live.add(breakAt(0, 0, 2));
            assertEquals(1, snapshot.actions.size());
            assertNotSame(live, snapshot.actions);
        }
    }

    @Nested
    @DisplayName("EchoAction")
    class Action {

        @Test
        @DisplayName("axis() maps 0/1/2 to x/y/z")
        void axisAccessor() {
            EchoAction action = breakAt(3, -4, 5);
            assertEquals(3, action.axis(0));
            assertEquals(-4, action.axis(1));
            assertEquals(5, action.axis(2));
            assertThrows(IllegalArgumentException.class, () -> action.axis(3));
        }

        @Test
        @DisplayName("null ids become empty strings rather than NPE traps")
        void nullIdsAreNormalised() {
            EchoAction action = new EchoAction(EchoAction.Type.BREAK_BLOCK, 0, 0, 0, null, null);
            assertEquals("", action.expectedBlockId);
            assertEquals("", action.itemId);
        }

        @Test
        @DisplayName("a type is required")
        void typeIsRequired() {
            assertThrows(NullPointerException.class,
                    () -> new EchoAction(null, 0, 0, 0, "minecraft:stone", ""));
        }
    }
}
