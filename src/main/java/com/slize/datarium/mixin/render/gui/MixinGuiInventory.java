package com.slize.datarium.mixin.render.gui;

import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiInventory.class)
public class MixinGuiInventory {

    @Inject(method = "drawEntityOnScreen", at = @At("HEAD"))
    private static void datarium$guiStart(int x, int y, int scale, float mouseX, float mouseY,
                                          EntityLivingBase entity, CallbackInfo ci) {
        CEMRenderHooks.setRenderingInGui(true);
    }

    @Inject(method = "drawEntityOnScreen", at = @At("RETURN"))
    private static void datarium$guiEnd(int x, int y, int scale, float mouseX, float mouseY,
                                        EntityLivingBase entity, CallbackInfo ci) {
        CEMRenderHooks.setRenderingInGui(false);
    }
}
