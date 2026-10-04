package com.slize.datarium.client.cem.textures;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.util.ResourceLocation;

public class LayerIronGolemCracks implements LayerRenderer<EntityIronGolem> {

    private static final ResourceLocation LOW = new ResourceLocation("textures/entity/iron_golem/iron_golem_crackiness_low.png");
    private static final ResourceLocation MEDIUM = new ResourceLocation("textures/entity/iron_golem/iron_golem_crackiness_medium.png");
    private static final ResourceLocation HIGH = new ResourceLocation("textures/entity/iron_golem/iron_golem_crackiness_high.png");

    private final RenderLivingBase<EntityIronGolem> renderer;

    public LayerIronGolemCracks(RenderLivingBase<EntityIronGolem> renderer) {
        this.renderer = renderer;
    }

    @Override
    public void doRenderLayer(EntityIronGolem golem, float limbSwing, float limbSwingAmount,
                              float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (golem.isInvisible()) return;

        float ratio = golem.getHealth() / golem.getMaxHealth();
        ResourceLocation cracks = ratio < 0.25F ? HIGH : ratio < 0.5F ? MEDIUM : ratio < 0.75F ? LOW : null;
        if (cracks == null) return;

        this.renderer.bindTexture(cracks);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(false);
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -1.0F);

        this.renderer.getMainModel().render(golem, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        GlStateManager.doPolygonOffset(0.0F, 0.0F);
        GlStateManager.disablePolygonOffset();
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
    }

    @Override
    public boolean shouldCombineTextures() {
        return true;
    }
}
