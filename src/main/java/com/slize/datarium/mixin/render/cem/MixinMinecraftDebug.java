package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMDebugSystem;
import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cet.CETState;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraftDebug {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void datarium$onTick(CallbackInfo ci) {
        CEMDebugSystem.onGameTick();
    }

    @Inject(method = "runGameLoop", at = @At("HEAD"))
    private void datarium$onFrameStart(CallbackInfo ci) {
        CEMManager.onFrameStart();
        CETState.verifyFrame();
    }
}