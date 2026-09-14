package com.ayesha.echoes.echo;

import com.ayesha.echoes.playback.EchoPlayback;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns every currently-active Echo. This is the "real" spawn/despawn path
 * that Phase 3 combines Recording + Playback + the Phase 2.5 ghost renderer
 * into -- it replaces PlaybackTester and RenderingSpikeTester, both of
 * which were explicitly temporary and are removed as of this phase.
 *
 * Enforces the locked V1 cap of 3 active Echoes and is the single place
 * that knows how many are currently out, so the HUD counter and the E/H
 * keybinds all go through here rather than tracking state themselves.
 */
public final class EchoManager {

    public static final int MAX_ACTIVE_ECHOES = 3;

    private static final EchoManager INSTANCE = new EchoManager();

    public static EchoManager getInstance() {
        return INSTANCE;
    }

    private final List<EchoEntity> activeEchoes = new ArrayList<>();

    private EchoManager() {
    }

    /**
     * Drops any references that went stale some other way (world unload,
     * the entity dying to something we didn't expect) before reporting a
     * count, so the max-3 check and the HUD counter never drift from
     * reality.
     */
    public int getActiveCount() {
        activeEchoes.removeIf(EchoEntity::isRemoved);
        return activeEchoes.size();
    }

    public boolean isAtMax() {
        return getActiveCount() >= MAX_ACTIVE_ECHOES;
    }

    /**
     * Spawns a new Echo replaying {@code recording}, starting at the
     * position/rotation the recording itself begins at (not wherever the
     * player happens to be standing when they press E -- the Echo should
     * replay where it actually started).
     *
     * Returns false and spawns nothing if there's no usable recording or
     * the 3-Echo cap is already reached.
     */
    public boolean spawn(Player player, EchoRecording recording) {
        if (recording == null || recording.isEmpty()) {
            player.sendSystemMessage(Component.literal("§cNo recording to summon — press R to record something first."));
            return false;
        }
        if (isAtMax()) {
            player.sendSystemMessage(Component.literal(
                    "§cAlready at the max of " + MAX_ACTIVE_ECHOES + " Echoes — press H to clear them first."));
            return false;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.getSingleplayerServer() == null
                || !(client.player instanceof AbstractClientPlayer clientPlayer)) {
            return false;
        }
        ServerLevel serverLevel = client.getSingleplayerServer().getLevel(client.level.dimension());
        if (serverLevel == null) {
            return false;
        }

        List<EchoSnapshot> snapshots = recording.getSnapshots();
        EchoSnapshot start = snapshots.get(0);

        Vec3 spawnPoint = getSpawnPoint(clientPlayer, start);
        EchoEntity echo = new EchoEntity(EchoEntities.ECHO, serverLevel);
        echo.setPos(spawnPoint.x, spawnPoint.y, spawnPoint.z);
        echo.setYRot(start.yaw);
        echo.setXRot(start.pitch);
        echo.setYHeadRot(start.yaw);
        echo.startPlayback(new EchoPlayback(recording, spawnPoint.x, spawnPoint.y, spawnPoint.z), player.getUUID());

        serverLevel.addFreshEntity(echo);
        playSpawnEffects(serverLevel, spawnPoint.x, spawnPoint.y, spawnPoint.z);
        activeEchoes.add(echo);

        player.sendSystemMessage(Component.literal(
                "§d👻 Echo summoned (" + getActiveCount() + "/" + MAX_ACTIVE_ECHOES + ")"));
        return true;
    }

    /** Spawn at the block/face currently under the player's crosshair.
     * The recording itself is then translated so its first frame lands here. */
    private Vec3 getSpawnPoint(AbstractClientPlayer player, EchoSnapshot start) {
        if (Minecraft.getInstance().hitResult instanceof BlockHitResult hit) {
            // The Echo's position is its FEET position.  The old implementation
            // added 0.36 blocks along the hit face, which made a top-face spawn
            // visibly float above the ground and could make the replayed path
            // miss the blocks it interacted with.
            BlockPos block = hit.getBlockPos();
            Direction face = hit.getDirection();

            if (face == Direction.UP) {
                return new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5);
            }

            // For side/bottom faces, spawn on the adjacent block when possible.
            BlockPos adjacent = block.relative(face);
            return new Vec3(adjacent.getX() + 0.5, adjacent.getY() + 1.0, adjacent.getZ() + 0.5);
        }
        return new Vec3(player.getX(), player.getY(), player.getZ());
    }

    /** Despawns every active Echo. Bound to H in the locked V1 keybinds. */
    public void clearAll(Player player) {
        if (getActiveCount() == 0) {
            player.sendSystemMessage(Component.literal("§7No active Echoes to clear."));
            return;
        }

        for (EchoEntity echo : activeEchoes) {
            if (!echo.isRemoved()) {
                playDespawnEffects(echo);
                echo.discard();
            }
        }
        activeEchoes.clear();
        player.sendSystemMessage(Component.literal("§d👻 All Echoes cleared"));
    }

    /**
     * Drops all tracked references without trying to despawn them for
     * real. Meant for (re)join -- a previous world's Echo entities are
     * already gone once that world/server is gone, so this just clears
     * our bookkeeping rather than calling discard() on dead references.
     */
    public void reset() {
        activeEchoes.clear();
    }

    private void playSpawnEffects(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 20, 0.3, 0.5, 0.3, 0.02);
        level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.6f, 1.4f);
    }

    private void playDespawnEffects(EchoEntity echo) {
        if (echo.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.POOF, echo.getX(), echo.getY() + 1.0, echo.getZ(), 15, 0.3, 0.5, 0.3, 0.02);
            level.playSound(null, echo.getX(), echo.getY(), echo.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.5f, 0.8f);
        }
    }
}
