package com.slize.datarium.mixin;

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

import java.util.Set;

@Mixin(TextureMap.class)
public abstract class MixinTextureMap {

    @Shadow
    public abstract boolean setTextureEntry(TextureAtlasSprite entry);

    @Inject(method = "loadTextureAtlas", at = @At("HEAD"))
    private void onLoadTextureAtlasHead(IResourceManager resourceManager, CallbackInfo ci) {
        // Reload CIT
        CITManager.reload();
        CITModelCache.clear();

        Set<ResourceLocation> citTextures = CITManager.getAllCITTextures();

        for (ResourceLocation texLoc : citTextures) {
            String path = texLoc.getPath();
            String spritePath = path;
            if (spritePath.endsWith(".png")) {
                spritePath = spritePath.substring(0, spritePath.length() - 4);
            }

            String spriteName = texLoc.getNamespace() + ":" + spritePath;
            CITAtlasSprite sprite = new CITAtlasSprite(spriteName, texLoc);
            this.setTextureEntry(sprite);
        }
    }
}