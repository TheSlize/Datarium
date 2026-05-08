package com.slize.datarium.mixin;

import com.slize.datarium.client.cit.CITAtlasSprite;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Stitcher.class)
public class MixinStitcher {

    @Inject(method = "addSprite", at = @At("HEAD"), cancellable = true)
    private void datarium$skipCITSprites(TextureAtlasSprite sprite, CallbackInfo ci) {
        if (sprite instanceof CITAtlasSprite && ((CITAtlasSprite) sprite).isStandalone()) {
            ci.cancel();
        }
    }
}
