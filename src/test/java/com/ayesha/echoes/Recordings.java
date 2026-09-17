package com.ayesha.echoes;

import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;

import java.util.Arrays;
import java.util.List;

/** Small builders so the tests read like the scenarios they describe. */
public final class Recordings {

    private Recordings() {}

    public static EchoSnapshot at(double x, double y, double z) {
        return new EchoSnapshot(x, y, z, 0f, 0f);
    }

    public static EchoSnapshot at(double x, double y, double z, EchoAction... actions) {
        return new EchoSnapshot(x, y, z, 0f, 0f, 0, 0, 0, false, false, false, 0, Arrays.asList(actions));
    }

    public static EchoAction breakAt(int dx, int dy, int dz) {
        return new EchoAction(EchoAction.Type.BREAK_BLOCK, dx, dy, dz, "minecraft:stone", "");
    }

    public static EchoAction placeAt(int dx, int dy, int dz) {
        return new EchoAction(EchoAction.Type.PLACE_BLOCK, dx, dy, dz, "minecraft:cobblestone", "minecraft:cobblestone");
    }

    public static EchoRecording of(EchoSnapshot... snapshots) {
        EchoRecording recording = new EchoRecording();
        for (EchoSnapshot snapshot : snapshots) {
            recording.addSnapshot(snapshot);
        }
        return recording;
    }

    public static EchoRecording of(List<EchoSnapshot> snapshots) {
        return of(snapshots.toArray(new EchoSnapshot[0]));
    }

    /**
     * The scenario that produced the "drowning" bug: walk 5 blocks forward
     * along +Z digging a 2-high tunnel, breaking the HEAD block first and the
     * FOOT block last on each column. Ends at z=4.87 -- real walking never
     * lands on a whole number.
     */
    public static EchoRecording walkingTunnel() {
        return of(
                at(0.5, 64.0, 0.50, breakAt(0, 1, 1), breakAt(0, 0, 1)),
                at(0.5, 64.0, 1.30, breakAt(0, 1, 2), breakAt(0, 0, 2)),
                at(0.5, 64.0, 2.40, breakAt(0, 1, 3), breakAt(0, 0, 3)),
                at(0.5, 64.0, 3.60, breakAt(0, 1, 4), breakAt(0, 0, 4)),
                at(0.5, 64.0, 5.37, breakAt(0, 1, 5), breakAt(0, 0, 5))
        );
    }
}
