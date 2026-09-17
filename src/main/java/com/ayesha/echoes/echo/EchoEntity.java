package com.ayesha.echoes.echo;

import com.ayesha.echoes.playback.EchoPlayback;
import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import com.ayesha.echoes.recording.RecordingManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class EchoEntity extends LivingEntity {
    private static final double SECONDS_PER_TICK = 1.0 / EchoRecording.TICKS_PER_SECOND;
    private static final Identifier ECHO_SKIN = Identifier.fromNamespaceAndPath("echoes", "textures/entity/echo.png");

    private EchoPlayback playback;
    private UUID ownerUuid;
    private long lastActionTick = -1L;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        // Position is fully recording-authoritative, so gravity would only
        // fight the replay. Block collision stays ON -- disabling it via
        // noPhysics is what made the ghost fall through the world back in the
        // Phase 2.5 spike.
        this.setNoGravity(true);
    }

    public void startPlayback(EchoPlayback playback, UUID ownerUuid) {
        this.playback = playback;
        this.ownerUuid = ownerUuid;
        this.lastActionTick = -1L;
    }

    public boolean hasPlayback() { return playback != null; }
    public Identifier getSkinTexture() { return ECHO_SKIN; }

    /**
     * Echoes are session-scoped. Without this, an Echo present at save time
     * would be written to the region file and come back on load with no
     * playback attached -- an invulnerable, immobile, untracked ghost that
     * nothing in the mod knows about and the player cannot remove.
     *
     * The playback == null guard in tick() is the belt-and-braces backstop, so
     * if this override ever fails to resolve against mappings you can simply
     * delete it and orphans still get cleaned up on their first tick.
     */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }
        if (playback == null) {
            // Orphan: restored from disk, or a spawn that never got wired up.
            discard();
            return;
        }

        // Read first, advance last -- see EchoPlayback's tick contract. The old
        // order advanced first, which skipped snapshot 0 (and any action on it)
        // on the first loop only.
        EchoSnapshot snapshot = playback.getCurrentSnapshot();
        if (snapshot == null) {
            killEcho("empty recording");
            return;
        }
        applySnapshot(snapshot);

        if (isOutOfBounds()) {
            killEcho("left the world");
            return;
        }

        long absoluteTick = playback.getAbsoluteTick();
        if (absoluteTick != lastActionTick) {
            lastActionTick = absoluteTick;
            for (EchoAction action : playback.getCurrentActions()) {
                if (!performAction(action)) {
                    return; // killEcho already called
                }
            }
        }

        playback.advance(SECONDS_PER_TICK);
        if (playback.hasExceededMaxLoops()) {
            killEcho("reached its max loop count (" + EchoPlayback.MAX_LOOPS + ")");
        }
    }

    /**
     * An automation Echo strides forward every loop forever, so it needs its
     * own way to notice it has run off the edge of the playable world. Checks
     * the build-height range AND the world border -- the border check was
     * missing, so an Echo could march past it indefinitely.
     */
    private boolean isOutOfBounds() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        double y = getY();
        if (y < serverLevel.getMinY() - 16 || y > serverLevel.getMaxY() + 16) {
            return true;
        }
        return !serverLevel.getWorldBorder().isWithinBounds(blockPosition());
    }

    private boolean performAction(EchoAction action) {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return true;
        }
        ServerPlayer owner = ownerUuid == null
                ? null
                : serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) {
            killEcho("owner unavailable");
            return false;
        }

        BlockPos target = new BlockPos(
                playback.getCurrentOriginBlockX() + action.dx,
                playback.getCurrentOriginBlockY() + action.dy,
                playback.getCurrentOriginBlockZ() + action.dz
        );

        // Never touch an unloaded chunk: doing so would force chunk generation
        // from inside an entity tick, and an Echo mining away from the player
        // reaches unloaded terrain sooner or later. Skip, do not die.
        if (!serverLevel.hasChunkAt(target)) {
            return true;
        }

        return switch (action.type) {
            case BREAK_BLOCK -> breakBlock(serverLevel, owner, target);
            case PLACE_BLOCK -> placeBlock(serverLevel, owner, action, target);
        };
    }

    private boolean breakBlock(ServerLevel serverLevel, ServerPlayer owner, BlockPos target) {
        BlockState targetState = serverLevel.getBlockState(target);
        // Forgiving: if the recorded block is already gone or changed, skip and
        // keep replaying. Only die for something it can NEVER break.
        if (targetState.isAir()) {
            return true;
        }
        if (targetState.getDestroySpeed(serverLevel, target) < 0) {
            killEcho("hit an unbreakable block");
            return false;
        }

        // V1.1: creative-style instant break -- no tool, tier or hardness
        // check, no mining delay. A deliberate simplification for the wow
        // factor, not a TODO.
        //
        // destroyBlock() is called with `owner` as the breaking player, so
        // PlayerBlockBreakEvents.AFTER fires with a UUID indistinguishable from
        // the owner actually mining. Without this guard, an Echo running in the
        // background would leak its automated breaks into a fresh recording the
        // player just started.
        RecordingManager.getInstance().setSuppressEchoCapture(true);
        try {
            serverLevel.destroyBlock(target, true, owner);
        } finally {
            RecordingManager.getInstance().setSuppressEchoCapture(false);
        }
        return true;
    }

    private boolean placeBlock(ServerLevel serverLevel, ServerPlayer owner, EchoAction action, BlockPos target) {
        Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(action.itemId));
        if (!(item instanceof BlockItem blockItem)) {
            return true;
        }

        ItemStack resource = findInInventory(owner, item);
        if (resource.isEmpty()) {
            killEcho("owner ran out of " + action.itemId);
            return false;
        }

        if (!serverLevel.getBlockState(target).canBeReplaced()) {
            // The world changed since recording. Skip, do not die.
            return true;
        }

        RecordingManager.getInstance().setSuppressEchoCapture(true);
        try {
            if (!serverLevel.setBlock(target, blockItem.getBlock().defaultBlockState(), 3)) {
                return true; // a failed placement is non-fatal
            }
        } finally {
            RecordingManager.getInstance().setSuppressEchoCapture(false);
        }

        resource.shrink(1);
        owner.containerMenu.broadcastChanges();
        return true;
    }

    private ItemStack findInInventory(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private void killEcho(String reason) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.POOF,
                    getX(), getY() + 1.0, getZ(), 12, 0.25, 0.45, 0.25, 0.02);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 0.45f, 0.7f);

            // Tell the owner WHY it stopped. Silent disappearance was the main
            // reason the looping bugs were hard to pin down in-game.
            ServerPlayer owner = ownerUuid == null
                    ? null
                    : serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                owner.sendSystemMessage(Component.literal("§7👻 An Echo ended: " + reason + "."));
            }
        }
        discard();
    }

    private void applySnapshot(EchoSnapshot snapshot) {
        double previousX = getX();
        double previousZ = getZ();
        setPos(snapshot.x, snapshot.y, snapshot.z);
        // The replay is authoritative, so residual velocity from LivingEntity's
        // own movement tick must be cleared -- otherwise it accumulates and
        // fights setPos on the following tick.
        setDeltaMovement(Vec3.ZERO);
        setYRot(snapshot.yaw);
        setXRot(snapshot.pitch);
        setYHeadRot(snapshot.yaw);
        setYBodyRot(snapshot.yaw);
        setSprinting(snapshot.sprinting);
        setShiftKeyDown(snapshot.sneaking);

        // Teleporting bypasses the built-in walk-animation bookkeeping, so feed
        // it the distance moved manually or the ghost slides without legs.
        double dx = snapshot.x - previousX;
        double dz = snapshot.z - previousZ;
        float distanceMoved = (float) Math.sqrt(dx * dx + dz * dz);
        walkAnimation.update(distanceMoved, 0.4f, 1.0f);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
    }

    public static AttributeSupplier.Builder createEchoAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    @Override public boolean isPushable() { return false; }
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) {}
}
