package com.slize.datarium.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slize.datarium.client.cit.CITManager;
import com.slize.datarium.client.cit.GlobalCITProperties;
import com.slize.datarium.client.model.LogicCarrierOverride;
import com.slize.datarium.client.model.ModernModelDefinitionDeserializer;
import com.slize.datarium.client.model.ModernOverrideListWrapper;
import com.slize.datarium.client.model.nodes.*;
import com.slize.datarium.util.ResourceHelper;
import com.slize.datarium.util.RespackOptsManager;
import com.slize.datarium.util.RpoHandler;
import net.minecraft.client.renderer.BlockModelShapes;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.client.renderer.block.model.ModelBakery;
import net.minecraft.client.renderer.block.model.ModelBlock;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Mixin(ModelBakery.class)
public abstract class MixinModelBakery {

    @Shadow
    protected abstract ModelBlock loadModel(ResourceLocation location) throws IOException;

    /**
     * Invalidate RespackOpts cache as well as CIT cache when ModelBakery is re-initialized (resource reload).
     */
    @Inject(method = "<init>", at = @At("RETURN"))
    public void onInit(IResourceManager resourceManagerIn, TextureMap textureMapIn, BlockModelShapes blockModelShapesIn, CallbackInfo ci) {
        RespackOptsManager.invalidate();
        CITManager.invalidate();
        GlobalCITProperties.invalidate();
    }

    @Inject(method = "loadModel", at = @At("HEAD"), cancellable = true)
    public void onLoadModelHead(ResourceLocation location, CallbackInfoReturnable<ModelBlock> cir) {
        ResourceLocation redirect = RpoHandler.getRedirect(location);
        if (redirect != null) {
            try {
                ModelBlock result = this.loadModel(redirect);
                cir.setReturnValue(result);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Inject(method = "loadModel", at = @At("RETURN"))
    public void onLoadModelReturn(ResourceLocation location, CallbackInfoReturnable<ModelBlock> cir) {
        ModelBlock definition = cir.getReturnValue();

        if (definition != null) {
            InputStream stream = ResourceHelper.getModernItemDefinition(location);

            if (stream != null) {
                try {
                    String jsonString = IOUtils.toString(stream, StandardCharsets.UTF_8);
                    JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

                    List<ItemOverride> dependencies = new ModernOverrideListWrapper(null);
                    ModernModelNode logicTree = ModernModelDefinitionDeserializer.parseModernJsonStatic(json, dependencies);

                    if (logicTree != null) {
                        ModernOverrideListWrapper wrapper = new ModernOverrideListWrapper(logicTree);

                        List<ItemOverride> originalOverrides = ((IModelBlockAccessor) definition).getOverrides();
                        if (originalOverrides != null) {
                            wrapper.addAll(originalOverrides);
                        }
                        wrapper.addAll(dependencies);

                        wrapper.add(new LogicCarrierOverride(logicTree));
                        ((IModelBlockAccessor) definition).setOverrides(wrapper);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.closeQuietly(stream);
                }
            }
        }
    }
}
