package com.slize.datarium.mixin.misc;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slize.datarium.util.GuiSheetComposer;
import com.slize.datarium.util.PackConverter;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleResource;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(FallbackResourceManager.class)
public class MixinFallbackResourceManager {

    @Unique
    private static final ResourceLocation LOGIC_CARRIER_MODEL =
            new ResourceLocation("datarium", "models/logic_carrier.json");

    // Did ya really think I'd create a .json empty model to suppress the error? Huh. No. I'd rather do it fucking HARDCODED
    @Unique
    private static final byte[] DUMMY_MODEL_JSON =
            "{\"parent\":\"builtin/generated\",\"textures\":{}}".getBytes(StandardCharsets.UTF_8);

    @Shadow @Final protected List<IResourcePack> resourcePacks;

    @Unique
    private final Map<ResourceLocation, Optional<GuiSheetComposer.Composed>> datarium$guiSheets = new ConcurrentHashMap<>();

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
            return;
        }

        PackConverter.GuiSheet sheet = PackConverter.guiSheet(location);
        if (sheet == null) return;
        GuiSheetComposer.Composed composed = this.datarium$guiSheets.computeIfAbsent(location,
                key -> Optional.ofNullable(GuiSheetComposer.compose(this.resourcePacks, key, sheet))).orElse(null);
        if (composed != null) {
            cir.setReturnValue(new SimpleResource(composed.pack(), location, new ByteArrayInputStream(composed.png()), null, null));
        }
    }

    @Inject(method = "addResourcePack", at = @At("HEAD"))
    private void datarium$trackPack(IResourcePack resourcePack, CallbackInfo ci) {
        PackConverter.track(resourcePack);
        this.datarium$guiSheets.clear();
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
