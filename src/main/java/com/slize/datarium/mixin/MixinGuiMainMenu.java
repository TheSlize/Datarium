package com.slize.datarium.mixin;

import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Unique
    private static final ResourceLocation CUSTOM_PANORAMA = new ResourceLocation("minecraft", "textures/gui/title/background/panorama_overlay.png");

    @Unique
    private boolean hasCustomPanorama;

    @Inject(method = "initGui", at = @At("HEAD"))
    private void checkCustomPanoramaExistence(CallbackInfo ci) {
        try {
            this.mc.getResourceManager().getResource(CUSTOM_PANORAMA);
            this.hasCustomPanorama = true;
        } catch (IOException e) {
            this.hasCustomPanorama = false;
        }
    }

    @Inject(method = "renderSkybox", at = @At("HEAD"), cancellable = true)
    private void renderCustomBackground(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (this.hasCustomPanorama) {
            this.mc.getTextureManager().bindTexture(CUSTOM_PANORAMA);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            drawModalRectWithCustomSizedTexture(0, 0, 0, 0, this.width, this.height, this.width, this.height);
            ci.cancel();
        }
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V"))
    private void removeVanillaGradients(GuiMainMenu instance, int left, int top, int right, int bottom, int startColor, int endColor) {
        if (!this.hasCustomPanorama) {
            this.drawGradientRect(left, top, right, bottom, startColor, endColor);
        }
    }
}