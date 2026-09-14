package com.ayesha.echoes.echo;

import com.ayesha.echoes.playback.EchoPlayback;
import com.ayesha.echoes.recording.EchoAction;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class EchoEntity extends LivingEntity {
    private static final double SECONDS_PER_TICK = 1.0 / EchoRecording.TICKS_PER_SECOND;
    private static final Identifier ECHO_SKIN = Identifier.fromNamespaceAndPath("echoes", "textures/entity/echo.png");

    private EchoPlayback playback;
    private UUID ownerUuid;
    private int lastActionTick = -1;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
        this.setInvulnerable(true);
        this.setNoGravity(true);
    }

    public void startPlayback(EchoPlayback playback, UUID ownerUuid) {
        this.playback = playback;
        this.ownerUuid = ownerUuid;
        this.lastActionTick = -1;
    }

    public boolean hasPlayback() { return playback != null; }
    public Identifier getSkinTexture() { return ECHO_SKIN; }

    @Override
    public void tick() {
        super.tick();
        if (playback == null || level().isClientSide()) return;

        playback.update(SECONDS_PER_TICK);
        EchoSnapshot snap = playback.getCurrentSnapshot();
        if (snap == null) { killEcho("empty recording"); return; }

        // Echoes are replays, not physical mobs.  They should be able to pass
        // through the world exactly as the original recording did, so a small
        // collision/placement difference must not kill the replay.
        applySnapshot(snap);

        int tick = playback.getCurrentTick();
        if (tick != lastActionTick) {
            lastActionTick = tick;
            EchoAction action = playback.getCurrentAction();
            if (action != null && !performAction(action)) return;
        }
    }

    private boolean performAction(EchoAction action) {
        if (!(level() instanceof ServerLevel serverLevel)) return true;
        ServerPlayer owner = ownerUuid == null ? null : serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
        if (owner == null) { killEcho("owner unavailable"); return false; }

        BlockPos target = action.targetFrom(playback.getOriginBlock());

        if (action.type == EchoAction.Type.BREAK_BLOCK) {
            // Forgiving interaction: if the recorded block has already been
            // removed or changed, simply skip this action and keep replaying.
            // The Echo should only die for a resource it genuinely needs.
            if (serverLevel.getBlockState(target).isAir()) {
                return true;
            }
            serverLevel.destroyBlock(target, true, owner);
            return true;
        }

        if (action.type == EchoAction.Type.PLACE_BLOCK) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(action.itemId));
            if (!(item instanceof BlockItem blockItem)) {
                return true;
            }

            ItemStack resource = findInInventory(owner, item);
            if (resource.isEmpty()) {
                killEcho("main player's resources ran out");
                return false;
            }

            if (!serverLevel.getBlockState(target).canBeReplaced()) {
                // Another block/player may have changed the world since the
                // recording. Skip the placement rather than killing the Echo.
                return true;
            }

            if (!serverLevel.setBlock(target, blockItem.getBlock().defaultBlockState(), 3)) {
                // Failed world interaction is non-fatal; the replay continues.
                return true;
            }
            resource.shrink(1);
            owner.containerMenu.broadcastChanges();
            return true;
        }

        return true;
    }

    private ItemStack findInInventory(ServerPlayer player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private void killEcho(String reason) {
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                    getX(), getY() + 1.0, getZ(), 12, 0.25, 0.45, 0.25, 0.02);
            serverLevel.playSound(null, getX(), getY(), getZ(),
                    net.minecraft.sounds.SoundEvents.ENDERMAN_TELEPORT,
                    net.minecraft.sounds.SoundSource.NEUTRAL, 0.45f, 0.7f);
        }
        discard();
    }

    private void applySnapshot(EchoSnapshot snap) {
        double prevX = getX();
        double prevZ = getZ();
        setPos(snap.x, snap.y, snap.z);
        setYRot(snap.yaw);
        setXRot(snap.pitch);
        setYHeadRot(snap.yaw);
        setYBodyRot(snap.yaw);
        setSprinting(snap.sprinting);
        setShiftKeyDown(snap.sneaking);
        double dx = snap.x - prevX;
        double dz = snap.z - prevZ;
        float distanceMoved = (float) Math.sqrt(dx * dx + dz * dz);
        walkAnimation.update(distanceMoved, 0.4f, 1.0f);
    }

    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) { return true; }

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
