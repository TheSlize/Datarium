package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.model.ModelShulker;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.tileentity.TileEntityShulkerBoxRenderer;
import net.minecraft.tileentity.TileEntityShulkerBox;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityShulkerBoxRenderer.class)
public abstract class MixinTileEntityShulkerBoxRendererCEM {

    @Shadow @Final private ModelShulker model;

    @Unique private boolean datarium$modernLid;
    @Unique private float datarium$progress;

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V", at = @At("HEAD"))
    private void datarium$poseLid(TileEntityShulkerBox te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        String name = CEMGenericRender.activeModelName();
        datarium$modernLid = name != null && CEMManager.baseName(name).endsWith("shulker_box");
        if (!datarium$modernLid) return;
        datarium$progress = te.getProgress(partialTicks);
        model.lid.rotationPointY = 24.0F - datarium$progress * 0.5F * 16.0F;
        model.lid.rotateAngleY = 270.0F * datarium$progress * 0.017453292F;
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V", at = @At("RETURN"))
    private void datarium$resetLid(TileEntityShulkerBox te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        if (!datarium$modernLid) return;
        model.lid.rotationPointY = 24.0F;
        model.lid.rotateAngleY = 0.0F;
        datarium$modernLid = false;
    }

    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"))
    private void datarium$skipLidLift(float x, float y, float z) {
        if (datarium$modernLid && x == 0.0F && z == 0.0F && y == -datarium$progress * 0.5F) return;
        GlStateManager.translate(x, y, z);
    }

    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntityShulkerBox;DDDFIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V"))
    private void datarium$skipLidTwist(float angle, float x, float y, float z) {
        if (datarium$modernLid && x == 0.0F && y == 1.0F && z == 0.0F && angle == 270.0F * datarium$progress) return;
        GlStateManager.rotate(angle, x, y, z);
    }
}
