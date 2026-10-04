package com.slize.datarium.mixin.render.entities;

import com.slize.datarium.client.cem.CEMFirstPerson;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.util.EnumHandSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPlayer.class)
public abstract class MixinRenderPlayerArm {

    @Shadow
    public abstract ModelPlayer getMainModel();

    @Inject(method = "renderRightArm", at = @At("HEAD"))
    private void datarium$rightHead(AbstractClientPlayer player, CallbackInfo ci) {
        CEMFirstPerson.begin(player, getMainModel(), EnumHandSide.RIGHT);
    }

    @Inject(method = "renderRightArm", at = @At("RETURN"))
    private void datarium$rightReturn(AbstractClientPlayer player, CallbackInfo ci) {
        CEMFirstPerson.end();
    }

    @Inject(method = "renderLeftArm", at = @At("HEAD"))
    private void datarium$leftHead(AbstractClientPlayer player, CallbackInfo ci) {
        CEMFirstPerson.begin(player, getMainModel(), EnumHandSide.LEFT);
    }

    @Inject(method = "renderLeftArm", at = @At("RETURN"))
    private void datarium$leftReturn(AbstractClientPlayer player, CallbackInfo ci) {
        CEMFirstPerson.end();
    }
}
