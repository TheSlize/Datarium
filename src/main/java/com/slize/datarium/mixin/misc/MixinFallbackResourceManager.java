package com.slize.datarium.mixin.misc;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FallbackResourceManager.class)
public class MixinFallbackResourceManager {

    @Unique
    private static final ResourceLocation LOGIC_CARRIER_MODEL =
            new ResourceLocation("datarium", "models/logic_carrier.json");

    // Did ya really think I'd create a .json empty model to suppress the error? Huh. No. I'd rather do it fucking HARDCODED
    @Unique
    private static final byte[] DUMMY_MODEL_JSON =
            "{\"parent\":\"builtin/generated\",\"textures\":{}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Inject(method = "getResource", at = @At("HEAD"), cancellable = true)
    private void onGetResource(ResourceLocation location, CallbackInfoReturnable<IResource> cir) {
        if (LOGIC_CARRIER_MODEL.equals(location)) {
            cir.setReturnValue(new SimpleResource(
                    "datarium",
                    location,
                    new java.io.ByteArrayInputStream(DUMMY_MODEL_JSON),
                    null,
                    null
            ));
        }
    }
}
