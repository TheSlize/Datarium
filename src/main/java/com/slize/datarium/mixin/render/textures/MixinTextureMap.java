package com.slize.datarium.mixin.render.textures;

import com.slize.datarium.client.cit.CITAtlasSprite;
import com.slize.datarium.client.cit.CITManager;
import com.slize.datarium.client.cit.CITModelCache;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap {

    @Shadow
    public abstract boolean setTextureEntry(TextureAtlasSprite entry);

    @Shadow
    public abstract TextureAtlasSprite getTextureExtry(String name);

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void onLoadTextureAtlasHead(IResourceManager resourceManager, CallbackInfo ci) {
        CITManager.reload();
        CITModelCache.clear();

        for (ResourceLocation texLoc : CITManager.getAllCITTextures()) {
            String spriteName = CITManager.spriteName(texLoc);
            if (this.getTextureExtry(spriteName) == null) {
                this.setTextureEntry(new CITAtlasSprite(spriteName, texLoc));
            }
        }
    }
}
