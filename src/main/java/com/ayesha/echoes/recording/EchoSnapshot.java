package com.ayesha.echoes.recording;

import java.util.Collections;
import java.util.List;

/**
 * One tick of recorded player state, plus any discrete world interactions
 * that happened during that tick.
 *
 * Like EchoAction, this is free of Minecraft types -- the live-player
 * sampling moved to RecordingManager, which is the class that actually owns
 * the world-facing side of recording. That keeps the whole recording and
 * playback data model plain Java and unit-testable.
 *
 * A tick can carry MORE THAN ONE action: breaking a snow layer can expose and
 * destroy the block beneath it inside the same 50ms window, and a single
 * nullable slot silently dropped the second one.
 */
public final class EchoSnapshot {
    public final double x;
    public final double y;
    public final double z;
    public final float yaw;
    public final float pitch;
    public final double velocityX;
    public final double velocityY;
    public final double velocityZ;
    public final boolean sprinting;
    public final boolean sneaking;
    public final boolean jumping;
    public final int hotbarSlot;
    /** Never null, never mutable; empty when nothing happened this tick. */
    public final List<EchoAction> actions;

    public EchoSnapshot(
            double x, double y, double z,
            float yaw, float pitch,
            double velocityX, double velocityY, double velocityZ,
            boolean sprinting, boolean sneaking, boolean jumping,
            int hotbarSlot, List<EchoAction> actions
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.velocityZ = velocityZ;
        this.sprinting = sprinting;
        this.sneaking = sneaking;
        this.jumping = jumping;
        this.hotbarSlot = hotbarSlot;
        // Copy, so a caller reusing its scratch list cannot rewrite history.
        this.actions = (actions == null || actions.isEmpty())
                ? Collections.emptyList()
                : List.copyOf(actions);
    }

    /** Convenience for a movement-only tick. */
    public EchoSnapshot(double x, double y, double z, float yaw, float pitch) {
        this(x, y, z, yaw, pitch, 0, 0, 0, false, false, false, 0, Collections.emptyList());
    }

    @Override
    public String toString() {
        return String.format(
                "EchoSnapshot[pos=(%.2f, %.2f, %.2f), rot=(%.1f, %.1f), sprint=%b, sneak=%b, jump=%b, slot=%d, actions=%d]",
                x, y, z, yaw, pitch, sprinting, sneaking, jumping, hotbarSlot, actions.size()
        );
    }
}
