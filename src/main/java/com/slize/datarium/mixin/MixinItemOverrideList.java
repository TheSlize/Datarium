package com.slize.datarium.mixin;

import com.google.common.collect.ImmutableList;
import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cit.*;
import com.slize.datarium.client.model.CompositeBakedModel;
import com.slize.datarium.client.model.LogicCarrierOverride;
import com.slize.datarium.client.model.nodes.ModernModelNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Mixin(ItemOverrideList.class)
public abstract class MixinItemOverrideList {

    @Shadow
    public abstract ImmutableList<ItemOverride> getOverrides();

    @Unique
    private ModernModelNode modernLogic;

    @Inject(method = "handleItemState", at = @At("HEAD"), cancellable = true)
    public void onHandleItemState(IBakedModel originalModel, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity, CallbackInfoReturnable<IBakedModel> cir) {
        // Check CIT first
        CITEntry citMatch = CITManager.getMatch(stack);
        if (citMatch != null) {
            IBakedModel citModel = datarium$getCITModel(citMatch, originalModel);
            if (citModel != null) {
                cir.setReturnValue(citModel);
                return;
            }
        }

        // Then check modern model logic
        if (this.modernLogic == null) {
            List<ItemOverride> overrides = this.getOverrides();
            if (overrides != null && !overrides.isEmpty()) {
                for (ItemOverride override : overrides) {
                    if (override instanceof LogicCarrierOverride carrier) {
                        this.modernLogic = carrier.logic;
                        break;
                    }
                }
            }
        }

        if (this.modernLogic != null) {
            Object result = this.modernLogic.resolve(stack, world, entity);

            if (result != null) {
                ModelManager modelManager = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager();
                IBakedModel missing = modelManager.getMissingModel();

                List<IBakedModel> bakedModels = new ArrayList<>();

                if (result instanceof ResourceLocation loc) {
                    datarium$addModel(loc, modelManager, missing, bakedModels);
                } else if (result instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof ResourceLocation loc) {
                            datarium$addModel(loc, modelManager, missing, bakedModels);
                        }
                    }
                }

                if (!bakedModels.isEmpty()) {
                    cir.setReturnValue(new CompositeBakedModel(bakedModels));
                }
            }
        }
    }

    @Unique
    @Nullable
    private IBakedModel datarium$getCITModel(CITEntry entry, IBakedModel baseModel) {
        ResourceLocation textureLoc = entry.getTexture();
        ModelManager modelManager = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager();

        Map<String, ResourceLocation> subModels = entry.getSubModels();
        if (!subModels.isEmpty()) {
            ResourceLocation subLoc = subModels.getOrDefault("inventory", subModels.values().iterator().next());
            ModelResourceLocation mrl = new ModelResourceLocation(subLoc, "inventory");
            IBakedModel subModel = modelManager.getModel(mrl);
            if (subModel != null && subModel != modelManager.getMissingModel()) {
                return subModel;
            }
        }

        // Top-level model — загружаем динамически из ресурспака
        ResourceLocation modelLoc = entry.getModel();
        if (modelLoc != null) {
            // Сначала пробуем ModelManager (если модель зарегистрирована штатно)
            ModelResourceLocation mrl = new ModelResourceLocation(modelLoc, "inventory");
            IBakedModel registered = modelManager.getModel(mrl);
            if (registered != null && registered != modelManager.getMissingModel()) {
                return registered;
            }
            // Иначе — динамический загрузчик CIT-моделей
            if (CITModelCache.contains(modelLoc)) {
                return CITModelCache.get(modelLoc);
            }
            IBakedModel dynamic = CITModelLoader.loadAndBake(modelLoc, entry.getPropertiesLocation(), baseModel);
            if (dynamic != null) {
                CITModelCache.put(modelLoc, dynamic);
                return dynamic;
            }
        }

        // If CIT specifies a texture, retexture the base model
        if (textureLoc != null) {
            if (CITModelCache.contains(textureLoc)) {
                return CITModelCache.get(textureLoc);
            }

            TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
            String spritePath = textureLoc.getPath();
            if (spritePath.endsWith(".png")) spritePath = spritePath.substring(0, spritePath.length() - 4);
            if (spritePath.startsWith("textures/")) spritePath = spritePath.substring("textures/".length());
            String spriteName = textureLoc.getNamespace() + ":" + spritePath;
            TextureAtlasSprite sprite = textureMap.getTextureExtry(spriteName);
            if (sprite == null) sprite = textureMap.getAtlasSprite(spriteName);
            if (sprite != null) {
                IBakedModel citModel = new CITBakedModel(baseModel, sprite);
                CITModelCache.put(textureLoc, citModel);
                return citModel;
            }
        }

        return null;
    }

    @Unique
    private void datarium$addModel(ResourceLocation loc, ModelManager manager, IBakedModel missing, List<IBakedModel> collector) {
        IBakedModel m = manager.getModel(new ModelResourceLocation(loc, "inventory"));
        if (m != null && m != missing) {
            collector.add(m);
        }
    }
}