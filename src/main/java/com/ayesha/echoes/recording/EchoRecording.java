package com.ayesha.echoes.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A completed (or in-progress) recording: an ordered list of snapshots taken
 * at 20 Hz (one per client tick), plus timing metadata.
 *
 * Kept intentionally dumb -- no logic tied to the live world, no Echo or
 * entity awareness. This is what EchoPlayback reads from; it never writes back.
 */
public final class EchoRecording {

    public static final int TICKS_PER_SECOND = 20;
    public static final int MAX_SECONDS = 60;
    public static final int MAX_SNAPSHOTS = TICKS_PER_SECOND * MAX_SECONDS; // 1200

    private final List<EchoSnapshot> snapshots = new ArrayList<>();

    /** Adds a snapshot. Returns false (and adds nothing) once the cap is hit. */
    public boolean addSnapshot(EchoSnapshot snapshot) {
        if (snapshot == null || snapshots.size() >= MAX_SNAPSHOTS) {
            return false;
        }
        snapshots.add(snapshot);
        return true;
    }

    public List<EchoSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
    }

    public EchoSnapshot getFirst() {
        return snapshots.isEmpty() ? null : snapshots.get(0);
    }

    public EchoSnapshot getLast() {
        return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    }

    public int getSnapshotCount() {
        return snapshots.size();
    }

    public boolean isEmpty() {
        return snapshots.isEmpty();
    }

    public boolean isAtMaxLength() {
        return snapshots.size() >= MAX_SNAPSHOTS;
    }

    public double getDurationSeconds() {
        return snapshots.size() / (double) TICKS_PER_SECOND;
    }

    /** Total discrete actions across every snapshot, including several in one tick. */
    public int getActionCount() {
        int total = 0;
        for (EchoSnapshot snapshot : snapshots) {
            total += snapshot.actions.size();
        }
        return total;
    }

    @Override
    public String toString() {
        return String.format("EchoRecording[snapshots=%d, duration=%.1fs, actions=%d]",
                getSnapshotCount(), getDurationSeconds(), getActionCount());
    }
}
