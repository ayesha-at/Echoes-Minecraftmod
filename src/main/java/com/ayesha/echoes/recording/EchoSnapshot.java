package com.ayesha.echoes.recording;

import net.minecraft.world.entity.player.Player;

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

    public EchoSnapshot(
            double x, double y, double z,
            float yaw, float pitch,
            double velocityX, double velocityY, double velocityZ,
            boolean sprinting, boolean sneaking, boolean jumping,
            int hotbarSlot
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
    }

    public static EchoSnapshot capture(Player player) {
        boolean jumping = !player.onGround() && player.getDeltaMovement().y > 0.0;

        return new EchoSnapshot(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z,
                player.isSprinting(), player.isShiftKeyDown(), jumping,
                player.getInventory().getSelectedSlot()
        );
    }

    @Override
    public String toString() {
        return String.format(
                "EchoSnapshot[pos=(%.2f, %.2f, %.2f), rot=(%.1f, %.1f), sprint=%b, sneak=%b, jump=%b, slot=%d]",
                x, y, z, yaw, pitch, sprinting, sneaking, jumping, hotbarSlot
        );
    }
}
