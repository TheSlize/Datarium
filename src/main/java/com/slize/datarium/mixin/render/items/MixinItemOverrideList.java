package com.slize.datarium.mixin.render.items;

import com.google.common.collect.ImmutableList;
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
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(ItemOverrideList.class)
public abstract class MixinItemOverrideList {

    @Shadow
    public abstract ImmutableList<ItemOverride> getOverrides();

    @Shadow
    @Nullable
    public abstract ResourceLocation applyOverride(ItemStack stack, @Nullable World worldIn, @Nullable EntityLivingBase entityIn);

    @Unique
    private ModernModelNode datarium$modernLogic;

    @Inject(method = "handleItemState", at = @At("HEAD"), cancellable = true)
    public void onHandleItemState(IBakedModel originalModel, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity, CallbackInfoReturnable<IBakedModel> cir) {
        if (this.datarium$modernLogic == null) {
            List<ItemOverride> overrides = this.getOverrides();
            if (overrides != null && !overrides.isEmpty()) {
                for (ItemOverride override : overrides) {
                    if (override instanceof LogicCarrierOverride carrier) {
                        this.datarium$modernLogic = carrier.logic;
                        break;
                    }
                }
            }
        }

        CITEntry citMatch = CITManager.getMatch(stack, CITEntry.CITType.ITEM);
        if (citMatch != null) {
            IBakedModel citModel = datarium$getCITModel(citMatch, originalModel, stack, world, entity);
            if (citModel != null) {
                cir.setReturnValue(citModel);
                return;
            }
        }

        if (this.datarium$modernLogic != null) {
            Object result = this.datarium$modernLogic.resolve(stack, world, entity);

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
    private IBakedModel datarium$getCITModel(CITEntry entry, IBakedModel originalModel, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        ModelManager modelManager = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager();
        IBakedModel missing = modelManager.getMissingModel();

        String subKey = null;
        IBakedModel base = originalModel;
        if (this.datarium$modernLogic == null && stack.getItem().hasCustomProperties()) {
            ResourceLocation overrideLoc = this.applyOverride(stack, world, entity);
            if (overrideLoc != null) {
                String path = overrideLoc.getPath();
                subKey = path.substring(path.lastIndexOf('/') + 1);
                IBakedModel overrideModel = modelManager.getModel(ModelLoader.getInventoryVariant(overrideLoc.toString()));
                if (overrideModel != null && overrideModel != missing) base = overrideModel;
            }
        }

        ResourceLocation modelLoc = subKey != null ? entry.subModels().get(subKey) : null;
        if (modelLoc == null) modelLoc = entry.model();
        if (modelLoc != null) {
            ResourceLocation modelFile = modelLoc;
            ResourceLocation texture = entry.texture();
            IBakedModel finalBase = base;
            IBakedModel model = CITModelCache.get(modelFile, texture == null ? List.of() : List.of(texture), base, () -> {
                if (texture == null) {
                    ModelResourceLocation mrl = CITManager.registeredItemModel(modelFile);
                    if (mrl != null) {
                        IBakedModel registered = modelManager.getModel(mrl);
                        if (registered != null && registered != missing) return registered;
                    }
                }
                return CITModelLoader.loadAndBake(modelFile, finalBase, texture);
            });
            if (model != null) return model;
        }

        List<ResourceLocation> layers = CITManager.getTextureLayers(entry, stack, subKey);
        if (layers.isEmpty()) return null;
        IBakedModel finalBase = base;
        return CITModelCache.get(null, layers, base, () -> {
            TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
            List<TextureAtlasSprite> sprites = new ArrayList<>(layers.size());
            for (ResourceLocation layer : layers) {
                TextureAtlasSprite sprite = textureMap.getTextureExtry(CITManager.spriteName(layer));
                if (sprite == null) return null;
                sprites.add(sprite);
            }
            return new CITBakedModel(finalBase, sprites);
        });
    }

    @Unique
    private void datarium$addModel(ResourceLocation loc, ModelManager manager, IBakedModel missing, List<IBakedModel> collector) {
        IBakedModel m = manager.getModel(new ModelResourceLocation(loc, "inventory"));
        if (m != null && m != missing) {
            collector.add(m);
        }
    }
}
