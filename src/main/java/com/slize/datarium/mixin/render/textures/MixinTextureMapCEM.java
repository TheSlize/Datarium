package com.slize.datarium.mixin.render.textures;

import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class MixinTextureMapCEM {

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void datarium$onReloadCEM(IResourceManager resourceManager, CallbackInfo ci) {
        CEMManager.invalidate();
    }
}