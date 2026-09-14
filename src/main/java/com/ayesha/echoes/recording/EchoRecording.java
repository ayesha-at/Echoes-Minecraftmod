package com.ayesha.echoes.recording;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A completed (or in-progress) recording: an ordered list of snapshots taken
 * at 20 Hz (one per client tick), plus timing metadata.
 *
 * Kept intentionally dumb — no logic tied to the live world, no Echo/entity
 * awareness. This is the thing Phase 2 playback and later EchoPlayback read
 * from; they don't write to it.
 */
public final class EchoRecording {

    public static final int TICKS_PER_SECOND = 20;
    public static final int MAX_SECONDS = 60;
    public static final int MAX_SNAPSHOTS = TICKS_PER_SECOND * MAX_SECONDS; // 1200

    private final List<EchoSnapshot> snapshots = new ArrayList<>(MAX_SNAPSHOTS);

    /** Adds a snapshot. Returns false (and does not add) if MAX_SNAPSHOTS reached. */
    public boolean addSnapshot(EchoSnapshot snapshot) {
        if (snapshots.size() >= MAX_SNAPSHOTS) {
            return false;
        }
        snapshots.add(snapshot);
        return true;
    }

    public List<EchoSnapshot> getSnapshots() {
        return Collections.unmodifiableList(snapshots);
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

    @Override
    public String toString() {
        return String.format("EchoRecording[snapshots=%d, duration=%.1fs]",
                getSnapshotCount(), getDurationSeconds());
    }
}
