package com.slize.datarium.mixin.misc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slize.datarium.util.PackConverter;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Mixin(FallbackResourceManager.class)
public class MixinFallbackResourceManager {

    @Unique
    private static final ResourceLocation LOGIC_CARRIER_MODEL =
            new ResourceLocation("datarium", "models/logic_carrier.json");

    // Did ya really think I'd create a .json empty model to suppress the error? Huh. No. I'd rather do it fucking HARDCODED
    @Unique
    private static final byte[] DUMMY_MODEL_JSON =
            "{\"parent\":\"builtin/generated\",\"textures\":{}}".getBytes(StandardCharsets.UTF_8);

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void onGetResource(ResourceLocation location, CallbackInfoReturnable<IResource> cir) {
        if (LOGIC_CARRIER_MODEL.equals(location)) {
            cir.setReturnValue(new SimpleResource(
                    "datarium",
                    location,
                    new ByteArrayInputStream(DUMMY_MODEL_JSON),
                    null,
                    null
            ));
        }
    }

    @Inject(method = "addResourcePack", at = @At("HEAD"))
    private void datarium$trackPack(IResourcePack resourcePack, CallbackInfo ci) {
        PackConverter.track(resourcePack);
    }

    @WrapOperation(method = {"getResource", "getAllResources"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/IResourcePack;resourceExists(Lnet/minecraft/util/ResourceLocation;)Z"))
    private boolean datarium$relinkExists(IResourcePack pack, ResourceLocation location, Operation<Boolean> original) {
        return original.call(pack, PackConverter.localize(pack, location));
    }

    @WrapOperation(method = "getInputStream",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/IResourcePack;getInputStream(Lnet/minecraft/util/ResourceLocation;)Ljava/io/InputStream;"))
    private InputStream datarium$relinkStream(IResourcePack pack, ResourceLocation location, Operation<InputStream> original) {
        return original.call(pack, PackConverter.localize(pack, location));
    }
}
