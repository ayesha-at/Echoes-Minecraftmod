package com.ayesha.echoes.recording;

import java.util.Objects;

/**
 * A discrete world interaction, recorded as a whole-block offset from the
 * recording's origin block.
 *
 * Deliberately free of Minecraft types: this is plain, NBT-shaped data
 * (primitives and strings only), which keeps it serializable later and makes
 * it unit-testable now without booting the game. Turning dx/dy/dz into a real
 * BlockPos is the caller's job -- see EchoEntity.
 */
public final class EchoAction {
    public enum Type { BREAK_BLOCK, PLACE_BLOCK }

    public final Type type;
    public final int dx;
    public final int dy;
    public final int dz;
    /** Registry id of the block that was there when this was recorded. */
    public final String expectedBlockId;
    /** Registry id of the item used to place. Empty for breaks. */
    public final String itemId;

    public EchoAction(Type type, int dx, int dy, int dz, String expectedBlockId, String itemId) {
        this.type = Objects.requireNonNull(type, "type");
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.expectedBlockId = expectedBlockId == null ? "" : expectedBlockId;
        this.itemId = itemId == null ? "" : itemId;
    }

    /** Offset along a single axis: 0 = x, 1 = y, 2 = z. Used by LoopStride. */
    public int axis(int axis) {
        return switch (axis) {
            case 0 -> dx;
            case 1 -> dy;
            case 2 -> dz;
            default -> throw new IllegalArgumentException("axis must be 0..2, was " + axis);
        };
    }

    @Override
    public String toString() {
        return String.format("EchoAction[%s @ (%d, %d, %d) block=%s item=%s]",
                type, dx, dy, dz, expectedBlockId, itemId);
    }
}
