package com.ayesha.echoes.rendering;

import com.ayesha.echoes.echo.EchoEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;

public class EchoRenderer extends LivingEntityRenderer<EchoEntity, EchoEntityRenderState, HumanoidModel<EchoEntityRenderState>> {

    private static final Identifier FALLBACK_SKIN = Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public EchoRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public EchoEntityRenderState createRenderState() {
        return new EchoEntityRenderState();
    }

    @Override
    public void extractRenderState(EchoEntity entity, EchoEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.skinTexture = entity.getSkinTexture();
    }

    @Override
    public Identifier getTextureLocation(EchoEntityRenderState state) {
        return state.skinTexture != null ? state.skinTexture : FALLBACK_SKIN;
    }
}