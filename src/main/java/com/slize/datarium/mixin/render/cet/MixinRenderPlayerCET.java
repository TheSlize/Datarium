package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETConfig;
import com.slize.datarium.client.cet.CETRender;
import com.slize.datarium.client.cet.CETState;
import com.slize.datarium.client.cet.player.CETPlayerLayer;
import com.slize.datarium.client.cet.player.CETPlayerTexture;
import com.slize.datarium.mixin.accessors.AccessorRenderLivingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayerCET {

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/RenderManager;Z)V", at = @At("TAIL"))
    private void datarium$addCetLayer(RenderManager renderManager, boolean useSmallArms, CallbackInfo ci) {
        ((AccessorRenderLivingBase) this).datarium$addLayer(new CETPlayerLayer((RenderPlayer) (Object) this));
    }

    @Inject(method = "getEntityTexture(Lnet/minecraft/client/entity/AbstractClientPlayer;)Lnet/minecraft/util/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void datarium$cetSkin(AbstractClientPlayer player, CallbackInfoReturnable<ResourceLocation> cir) {
        if (!CETConfig.skinFeaturesEnabled) return;
        CETPlayerTexture texture = CETPlayerTexture.of(player);
        if (texture != null && texture.baseTexture != null) cir.setReturnValue(texture.baseTexture.thisIdentifier);
    }

    @Inject(method = "renderRightArm", at = @At("HEAD"))
    private void datarium$cetRightArmHead(AbstractClientPlayer player, CallbackInfo ci) {
        datarium$beginArm(player);
    }

    @Inject(method = "renderRightArm", at = @At("RETURN"))
    private void datarium$cetRightArmReturn(AbstractClientPlayer player, CallbackInfo ci) {
        CETRender.end();
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"))
    private void datarium$cetLeftArmHead(AbstractClientPlayer player, CallbackInfo ci) {
        datarium$beginArm(player);
    }

    @Inject(method = "renderLeftArm", at = @At("RETURN"))
    private void datarium$cetLeftArmReturn(AbstractClientPlayer player, CallbackInfo ci) {
        CETRender.end();
    }

    @Unique
    private static void datarium$beginArm(AbstractClientPlayer player) {
        CETRender.beginEntity(player);
        CETPlayerTexture texture = CETConfig.skinFeaturesEnabled ? CETPlayerTexture.of(player) : null;
        ResourceLocation skin = texture != null && texture.baseTexture != null ? texture.baseTexture.thisIdentifier : player.getLocationSkin();
        if (CETState.isActive()) Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
    }
}
