package com.ayesha.echoes.rendering;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;

public class EchoRenderer extends LivingEntityRenderer<com.ayesha.echoes.echo.EchoEntity, EchoEntityRenderState, HumanoidModel<EchoEntityRenderState>> {
    private static final Identifier ECHO_SKIN = Identifier.fromNamespaceAndPath("echoes", "textures/entity/echo.png");

    public EchoRenderer(EntityRendererProvider.Context context) {
        super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public EchoEntityRenderState createRenderState() {
        return new EchoEntityRenderState();
    }

    @Override
    public void extractRenderState(com.ayesha.echoes.echo.EchoEntity entity, EchoEntityRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
    }

    @Override
    public Identifier getTextureLocation(EchoEntityRenderState state) {
        return ECHO_SKIN;
    }

    @Override
    protected RenderType getRenderType(EchoEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing) {
        return RenderTypes.entityTranslucent(ECHO_SKIN);
    }
}
