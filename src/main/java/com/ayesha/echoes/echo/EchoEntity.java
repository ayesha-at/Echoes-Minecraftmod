package com.ayesha.echoes.echo;

import com.ayesha.echoes.playback.EchoPlayback;
import com.ayesha.echoes.recording.EchoRecording;
import com.ayesha.echoes.recording.EchoSnapshot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EchoEntity extends LivingEntity {

    private static final double SECONDS_PER_TICK = 1.0 / EchoRecording.TICKS_PER_SECOND;

    private Identifier skinTexture;

    /** Set once by EchoManager right after spawning. Null until then. */
    private EchoPlayback playback;

    public EchoEntity(EntityType<? extends EchoEntity> type, Level level) {
        super(type, level);
        // NOTE: noPhysics was here before and caused the entity to fall
        // straight through the floor (it disables ALL block collision, not
        // just player collision -- gravity still applies with nothing to
        // stop it, so it free-fell until far enough below the world to get
        // auto-discarded). Not needed for V1 anyway since walking through
        // walls isn't in scope yet -- normal collision lets it stand on
        // the ground like any other entity.
        this.setInvulnerable(true);
        // Position is fully authoritative from the recording every tick
        // (see tick() below), so we don't want the entity's own gravity
        // fighting that -- it'd get overwritten anyway, but there's no
        // reason to let it accumulate fall velocity in the meantime.
        this.setNoGravity(true);
    }

    /** Attaches the playback this Echo replays. Called once by EchoManager right after construction. */
    public void startPlayback(EchoPlayback playback) {
        this.playback = playback;
    }

    public boolean hasPlayback() {
        return playback != null;
    }

    @Override
    public void tick() {
        super.tick();

        // Echo movement is server-authoritative, driven from here on the
        // integrated server's own tick -- not poked at from the client
        // thread the way the Phase 2.5 spike's test rotation was. On the
        // client side this entity is just a normal synced entity; its
        // position/rotation arrive via the standard entity-tracking
        // packets, so there's nothing to do here client-side.
        if (playback == null || level().isClientSide()) {
            return;
        }

        playback.update(SECONDS_PER_TICK);
        applySnapshot(playback.getCurrentSnapshot());
    }

    private void applySnapshot(EchoSnapshot snap) {
        if (snap == null) {
            return;
        }

        double prevX = getX();
        double prevZ = getZ();

        setPos(snap.x, snap.y, snap.z);
        setYRot(snap.yaw);
        setXRot(snap.pitch);
        setYHeadRot(snap.yaw);
        setYBodyRot(snap.yaw);

        setSprinting(snap.sprinting);
        setShiftKeyDown(snap.sneaking);

        // LivingEntity's built-in walk-animation bookkeeping is driven by
        // actual physics movement (aiStep/travel), which this entity never
        // runs -- its position is set directly from the recording every
        // tick instead. So the walk animation has to be fed manually from
        // how far the snapshot moved this tick, or the ghost would stand
        // in the walking pose without ever animating its legs.
        // WalkAnimationState.update(targetSpeed, speedChangeRate, timeScale)
        // -- confirmed 3-arg signature (compiler caught the earlier 2-arg
        // guess). 0.4f speedChangeRate matches vanilla LivingEntity's own
        // usage; timeScale is 1.0f since this already runs once per tick.
        double dx = snap.x - prevX;
        double dz = snap.z - prevZ;
        float distanceMoved = (float) Math.sqrt(dx * dx + dz * dz);
        walkAnimation.update(distanceMoved, 0.4f, 1.0f);
    }

    /**
     * Belt-and-suspenders alongside setInvulnerable(true): a ghost shouldn't
     * be killable by anything -- suffocation from spawning inside a block,
     * drowning, fall damage, fire, the void, none of it. This was the
     * actual cause of it vanishing seconds after spawning (1 HP + normal
     * LivingEntity mortality + likely suffocation from spawn position).
     */
    @Override
    public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
        return true;
    }

    public static AttributeSupplier.Builder createEchoAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 1.0)
                .add(Attributes.MOVEMENT_SPEED, 0.0);
    }

    public void setSkinTexture(Identifier skinTexture) {
        this.skinTexture = skinTexture;
    }

    public Identifier getSkinTexture() {
        return skinTexture;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public HumanoidArm getMainArm() {
        return HumanoidArm.RIGHT;
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
    }
}
