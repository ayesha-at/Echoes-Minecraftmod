package com.ayesha.echoes.recording;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** A discrete world interaction recorded relative to the recording origin. */
public final class EchoAction {
    public enum Type { BREAK_BLOCK, PLACE_BLOCK }

    public final Type type;
    public final int dx;
    public final int dy;
    public final int dz;
    public final String expectedBlockId;
    public final String itemId;

    public EchoAction(Type type, int dx, int dy, int dz, String expectedBlockId, String itemId) {
        this.type = type;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.expectedBlockId = expectedBlockId;
        this.itemId = itemId;
    }

    public BlockPos targetFrom(BlockPos origin) {
        return origin.offset(dx, dy, dz);
    }

    public static String blockId(BlockState state) {
        return state.getBlock().builtInRegistryHolder().key().identifier().toString();
    }
}
