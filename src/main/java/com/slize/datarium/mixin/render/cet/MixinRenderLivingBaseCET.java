package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETConfig;
import com.slize.datarium.client.cet.CETRender;
import com.slize.datarium.client.cet.CETState;
import com.slize.datarium.client.cet.player.CETPlayerTexture;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderLivingBase.class)
public abstract class MixinRenderLivingBaseCET<T extends EntityLivingBase> {

    @Unique private boolean datarium$cetTranslucent;

    @Inject(method = "renderLayers", at = @At("HEAD"))
    private void datarium$beginFeatures(T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                        float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        CETState.isRenderingFeatures = true;
    }

    @Inject(method = "renderLayers", at = @At("RETURN"))
    private void datarium$endFeatures(T entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                                      float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        CETState.isRenderingFeatures = false;
    }

    @Inject(method = "renderModel", at = @At("HEAD"))
    private void datarium$beginSkinTransparency(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                                float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        datarium$cetTranslucent = false;
        if (!(entity instanceof AbstractClientPlayer player) || CETConfig.skinTransparencyMode == CETConfig.SkinTransparencyMode.VANILLA) return;
        CETPlayerTexture texture = CETPlayerTexture.of(player);
        if (texture != null && !texture.wasForcedSolid) {
            CETRender.beginTranslucent();
            datarium$cetTranslucent = true;
        }
    }

    @Inject(method = "renderModel", at = @At("RETURN"))
    private void datarium$endSkinTransparency(T entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                              float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (datarium$cetTranslucent) {
            CETRender.endTranslucent();
            datarium$cetTranslucent = false;
        }
    }
}
