package com.slize.datarium.mixin;

import com.slize.datarium.client.cit.CITArmorHandler;
import net.minecraft.client.model.ModelElytra;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.client.renderer.entity.layers.LayerElytra;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerElytra.class)
public abstract class MixinLayerElytra {

    @Shadow
    protected RenderLivingBase<?> renderPlayer;

    @Inject(method = "doRenderLayer", at = @At("HEAD"), cancellable = true)
    private void datarium$onDoRenderLayer(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                                          float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                          CallbackInfo ci) {
        ItemStack chest = entity.getItemStackFromSlot(EntityEquipmentSlot.CHEST);
        if (chest.isEmpty() || chest.getItem() != Items.ELYTRA) return;

        ResourceLocation cit = CITArmorHandler.getElytraTexture(chest);
        if (cit == null) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);

        this.renderPlayer.bindTexture(cit);

        GlStateManager.pushMatrix();
        GlStateManager.translate(0.0F, 0.0F, 0.125F);

        ModelElytra model = ((ILayerElytraAccessor)(Object)this).datarium$getModelElytra();
        model.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entity);
        model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

        if (chest.isItemEnchanted()) {
            LayerArmorBase.renderEnchantedGlint(
                    this.renderPlayer, entity, model, limbSwing, limbSwingAmount,
                    partialTicks, ageInTicks, netHeadYaw, headPitch, scale);
        }

        GlStateManager.disableBlend();
        GlStateManager.popMatrix();

        ci.cancel();
    }
}