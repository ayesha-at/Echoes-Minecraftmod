package com.ayesha.echoes.echo;

import com.ayesha.echoes.playback.EchoPlayback;
import com.ayesha.echoes.playback.LoopStride;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns every currently-active Echo: the single place that knows how many are
 * out, so the HUD counter, the max-3 cap and the keybinds all agree.
 *
 * <h2>Threading</h2>
 * Keybinds call in on the CLIENT thread, but entities live on the integrated
 * server thread. Spawning and discarding therefore hop onto the server thread
 * via {@link MinecraftServer#execute} instead of mutating server-owned state
 * directly -- the old code called addFreshEntity() and discard() straight from
 * the client tick, which is a real data race that happened to work most of the
 * time. The tracking list is copy-on-write for the same reason.
 */
public final class EchoManager {

    public static final int MAX_ACTIVE_ECHOES = 3;

    private static final EchoManager INSTANCE = new EchoManager();

    public static EchoManager getInstance() {
        return INSTANCE;
    }

    private final List<EchoEntity> activeEchoes = new CopyOnWriteArrayList<>();

    private EchoManager() {
    }

    /** Prunes references that went stale some other way before counting. */
    public int getActiveCount() {
        activeEchoes.removeIf(EchoEntity::isRemoved);
        return activeEchoes.size();
    }

    public boolean isAtMax() {
        return getActiveCount() >= MAX_ACTIVE_ECHOES;
    }

    /**
     * Spawns an Echo replaying {@code recording}, starting at the block under
     * the crosshair (falling back to the player's own feet). Returns false and
     * spawns nothing if there is no usable recording or the cap is already hit.
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
        MinecraftServer server = client.getSingleplayerServer();
        if (client.level == null || client.player == null || server == null) {
            player.sendSystemMessage(Component.literal("§cEchoes only work in singleplayer for now."));
            return false;
        }

        ResourceKey<Level> dimension = client.level.dimension();
        Vec3 spawnPoint = resolveSpawnPoint(client);
        LoopStride stride = LoopStride.of(recording);

        // Build the playback on the client thread -- it is pure data -- then do
        // all world mutation on the thread that owns the world.
        EchoPlayback playback = new EchoPlayback(recording, spawnPoint.x, spawnPoint.y, spawnPoint.z);
        EchoSnapshot start = recording.getFirst();
        UUID ownerUuid = player.getUUID();

        server.execute(() -> {
            ServerLevel serverLevel = server.getLevel(dimension);
            if (serverLevel == null) {
                return;
            }
            // Re-check on the owning thread: the check above is an early-out
            // for the player's benefit, not a guarantee.
            if (getActiveCount() >= MAX_ACTIVE_ECHOES) {
                return;
            }

            EchoEntity echo = new EchoEntity(EchoEntities.ECHO, serverLevel);
            echo.setPos(spawnPoint.x, spawnPoint.y, spawnPoint.z);
            echo.setYRot(start.yaw);
            echo.setXRot(start.pitch);
            echo.setYHeadRot(start.yaw);
            echo.setYBodyRot(start.yaw);
            echo.startPlayback(playback, ownerUuid);

            if (!serverLevel.addFreshEntity(echo)) {
                return;
            }
            activeEchoes.add(echo);
            playSpawnEffects(serverLevel, spawnPoint.x, spawnPoint.y, spawnPoint.z);
        });

        // Say what the Echo is actually going to do. A silently zero stride was
        // indistinguishable in-game from a broken loop.
        player.sendSystemMessage(Component.literal(
                "§d👻 Echo summoned — §f" + stride.describe()
                        + " §7(" + String.format("%.1fs", recording.getDurationSeconds())
                        + ", " + recording.getActionCount() + " actions)"));
        if (stride.isZero() && recording.getActionCount() > 0) {
            player.sendSystemMessage(Component.literal(
                    "§eThis recording ends where it started, so the Echo repeats in place. "
                            + "Walk in one direction while recording to make it extend."));
        }
        return true;
    }

    /**
     * Spawn at the block/face under the crosshair; the recording is then
     * translated so its first frame lands exactly there.
     */
    private Vec3 resolveSpawnPoint(Minecraft client) {
        if (client.hitResult instanceof BlockHitResult hit && hit.getDirection() != null) {
            BlockPos block = hit.getBlockPos();
            Direction face = hit.getDirection();
            // Echo position is FEET position, so sit it on top of the surface.
            if (face == Direction.UP) {
                return new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5);
            }
            BlockPos adjacent = block.relative(face);
            return new Vec3(adjacent.getX() + 0.5, adjacent.getY() + 1.0, adjacent.getZ() + 0.5);
        }
        return new Vec3(client.player.getX(), client.player.getY(), client.player.getZ());
    }

    /** Despawns every active Echo. Bound to H. */
    public void clearAll(Player player) {
        int count = getActiveCount();
        if (count == 0) {
            player.sendSystemMessage(Component.literal("§7No active Echoes to clear."));
            return;
        }

        List<EchoEntity> doomed = List.copyOf(activeEchoes);
        activeEchoes.clear();

        MinecraftServer server = Minecraft.getInstance().getSingleplayerServer();
        Runnable removal = () -> {
            for (EchoEntity echo : doomed) {
                if (!echo.isRemoved()) {
                    playDespawnEffects(echo);
                    echo.discard();
                }
            }
        };
        if (server != null) {
            server.execute(removal);
        } else {
            removal.run();
        }

        player.sendSystemMessage(Component.literal("§d👻 Cleared " + count + " Echo" + (count == 1 ? "" : "es")));
    }

    /**
     * Drops tracked references without despawning them. For (re)join: the
     * previous world's entities are already gone with that world, so this
     * clears stale bookkeeping rather than calling discard() on dead refs.
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
