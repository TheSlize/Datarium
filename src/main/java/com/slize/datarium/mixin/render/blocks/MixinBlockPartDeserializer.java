package com.slize.datarium.mixin.render.blocks;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.renderer.block.model.BlockPart;
import net.minecraft.client.renderer.block.model.BlockPartFace;
import net.minecraft.util.JsonUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Type;

@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockPart$Deserializer")
public class MixinBlockPartDeserializer {

    /**
     * Supports arbitrary rotation angles for whatever modern shit we have (because 1.12.2 supports only +-22.5 degree step validation)
     * @author Th3_Sl1ze
     */
    @Overwrite
    private float parseAngle(JsonObject json) {
        return JsonUtils.getFloat(json, "angle");
    }

    @Inject(method = "deserialize(Lcom/google/gson/JsonElement;Ljava/lang/reflect/Type;Lcom/google/gson/JsonDeserializationContext;)Lnet/minecraft/client/renderer/block/model/BlockPart;",
            at = @At("RETURN"))
    private void onDeserialize(JsonElement jsonElement, Type type, JsonDeserializationContext context, CallbackInfoReturnable<BlockPart> cir) {
        BlockPart part = cir.getReturnValue();

        if (part != null && jsonElement.isJsonObject()) {
            JsonObject json = jsonElement.getAsJsonObject();

            if (json.has("texture_size")) {
                JsonArray sizeArray = json.getAsJsonArray("texture_size");
                if (sizeArray.size() >= 2) {
                    float width = sizeArray.get(0).getAsFloat();
                    float height = sizeArray.get(1).getAsFloat();

                    if (width > 0 && height > 0 && (width != 16.0F || height != 16.0F)) {
                        float scaleX = 16.0F / width;
                        float scaleY = 16.0F / height;

                        for (BlockPartFace face : part.mapFaces.values()) {
                            if (face.blockFaceUV != null && face.blockFaceUV.uvs != null) {
                                float[] uvs = face.blockFaceUV.uvs;
                                uvs[0] *= scaleX;
                                uvs[1] *= scaleY;
                                uvs[2] *= scaleX;
                                uvs[3] *= scaleY;
                            }
                        }
                    }
                }
            }
        }
    }
}