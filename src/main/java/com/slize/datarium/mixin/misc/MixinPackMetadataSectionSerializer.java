package com.slize.datarium.mixin.misc;

import com.google.gson.JsonObject;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slize.datarium.util.PackConverter;
import net.minecraft.client.resources.data.PackMetadataSectionSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PackMetadataSectionSerializer.class)
public class MixinPackMetadataSectionSerializer {

    @WrapOperation(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/resources/data/PackMetadataSection;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/JsonUtils;getInt(Lcom/google/gson/JsonObject;Ljava/lang/String;)I"))
    private int datarium$modernFormat(JsonObject pack, String key, Operation<Integer> original) {
        if (!pack.has(key)) {
            int format = PackConverter.declaredFormat(pack);
            if (format > 0) return format;
        }
        return original.call(pack, key);
    }
}
