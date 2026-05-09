package com.slize.datarium.client.cem;

import com.google.gson.*;
import com.slize.datarium.DatariumMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class CEMModelLoader {

    @Nullable
    public static CEMModel loadJEM(ResourceLocation location) {
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
            String json;
            try (InputStream is = resource.getInputStream()) {
                json = IOUtils.toString(is, StandardCharsets.UTF_8);
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            CEMModel model = new CEMModel();

            if (root.has("credit")) {
                model.credit = root.get("credit").getAsString();
            }

            if (root.has("textureSize")) {
                JsonArray size = root.getAsJsonArray("textureSize");
                model.textureSize = new int[]{size.get(0).getAsInt(), size.get(1).getAsInt()};
            }

            if (root.has("models")) {
                JsonArray modelsArray = root.getAsJsonArray("models");
                for (JsonElement elem : modelsArray) {
                    // Pass null as parent for root parts
                    CEMModelPart part = parseModelPart(elem.getAsJsonObject(), null);
                    model.parts.add(part);
                }
            }

            model.indexParts();

            for (CEMModelPart part : model.parts) {
                if (part.modelPath != null && !part.modelPath.isEmpty()) {
                    ResourceLocation jpmLoc = resolveRelativePath(location, part.modelPath);
                    CEMModel jpmModel = loadJPM(jpmLoc);
                    if (jpmModel != null) {
                        model.animations.addAll(jpmModel.animations);
                    }
                }
            }

            return model;
        } catch (Exception e) {
            DatariumMain.LOGGER.debug("Failed to load CEM model: {}", location, e);
            return null;
        }
    }

    @Nullable
    public static CEMModel loadJPM(ResourceLocation location) {
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
            String json;
            try (InputStream is = resource.getInputStream()) {
                json = IOUtils.toString(is, StandardCharsets.UTF_8);
            }

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            CEMModel model = new CEMModel();

            if (root.has("credit")) {
                model.credit = root.get("credit").getAsString();
            }
            if (root.has("textureSize")) {
                JsonArray size = root.getAsJsonArray("textureSize");
                model.textureSize = new int[]{size.get(0).getAsInt(), size.get(1).getAsInt()};
            }

            if (root.has("elements")) {
                CEMModelPart rootPart = new CEMModelPart();
                rootPart.id = "root";
                JsonArray elements = root.getAsJsonArray("elements");
                for (JsonElement elem : elements) {
                    CEMBox box = parseBox(elem.getAsJsonObject());
                    rootPart.boxes.add(box);
                }
                model.parts.add(rootPart);
            }

            if (root.has("animations")) {
                JsonArray animations = root.getAsJsonArray("animations");
                for (JsonElement elem : animations) {
                    CEMAnimation anim = new CEMAnimation();
                    JsonObject animObj = elem.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : animObj.entrySet()) {
                        anim.expressions.put(entry.getKey(), entry.getValue().getAsString());
                    }
                    model.animations.add(anim);
                }
            }

            model.indexParts();
            return model;
        } catch (Exception e) {
            DatariumMain.LOGGER.debug("Failed to load JPM model: {}", location, e);
            return null;
        }
    }

    private static CEMModelPart parseModelPart(JsonObject obj, @Nullable CEMModelPart parent) {
        CEMModelPart part = new CEMModelPart();
        part.parent = parent;

        if (obj.has("part")) part.part = obj.get("part").getAsString();
        if (obj.has("id")) part.id = obj.get("id").getAsString();
        if (obj.has("model")) part.modelPath = obj.get("model").getAsString();
        if (obj.has("attach")) part.attach = obj.get("attach").getAsString().equalsIgnoreCase("true");
        if (obj.has("invertAxis")) part.invertAxis = obj.get("invertAxis").getAsString();
        if (obj.has("mirrorTexture")) part.mirrorTexture = obj.get("mirrorTexture").getAsString();

        if (obj.has("translate")) {
            JsonArray arr = obj.getAsJsonArray("translate");
            part.translate = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }
        if (obj.has("rotate")) {
            JsonArray arr = obj.getAsJsonArray("rotate");
            part.rotate = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
        }
        if (obj.has("scale")) {
            if (obj.get("scale").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("scale");
                part.scale = new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
            } else {
                float s = obj.get("scale").getAsFloat();
                part.scale = new float[]{s, s, s};
            }
        }

        if (obj.has("boxes")) {
            JsonArray boxes = obj.getAsJsonArray("boxes");
            for (JsonElement elem : boxes) {
                CEMBox box = parseBox(elem.getAsJsonObject());
                part.boxes.add(box);
            }
        }

        if (obj.has("submodels")) {
            JsonArray submodels = obj.getAsJsonArray("submodels");
            for (JsonElement elem : submodels) {
                CEMModelPart subPart = parseModelPart(elem.getAsJsonObject(), part);
                part.submodels.add(subPart);
            }
        }

        return part;
    }

    private static CEMBox parseBox(JsonObject obj) {
        CEMBox box = new CEMBox();
        if (obj.has("coordinates")) {
            JsonArray coords = obj.getAsJsonArray("coordinates");
            for (int i = 0; i < Math.min(6, coords.size()); i++) box.coordinates[i] = coords.get(i).getAsFloat();
        }
        if (obj.has("textureOffset")) {
            JsonArray offset = obj.getAsJsonArray("textureOffset");
            box.textureOffset = new int[]{offset.get(0).getAsInt(), offset.get(1).getAsInt()};
        }
        if (obj.has("sizeAdd")) box.sizeAdd = obj.get("sizeAdd").getAsFloat();
        if (obj.has("uvNorth")) box.uvNorth = parseUV(obj.getAsJsonArray("uvNorth"));
        if (obj.has("uvSouth")) box.uvSouth = parseUV(obj.getAsJsonArray("uvSouth"));
        if (obj.has("uvEast")) box.uvEast = parseUV(obj.getAsJsonArray("uvEast"));
        if (obj.has("uvWest")) box.uvWest = parseUV(obj.getAsJsonArray("uvWest"));
        if (obj.has("uvUp")) box.uvUp = parseUV(obj.getAsJsonArray("uvUp"));
        if (obj.has("uvDown")) box.uvDown = parseUV(obj.getAsJsonArray("uvDown"));
        return box;
    }

    private static float[] parseUV(JsonArray arr) {
        return new float[]{
                arr.get(0).getAsFloat(), arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat(), arr.get(3).getAsFloat()
        };
    }

    private static ResourceLocation resolveRelativePath(ResourceLocation base, String path) {
        String basePath = base.getPath();
        int lastSlash = basePath.lastIndexOf('/');
        String dir = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "";
        if (path.startsWith("./")) path = path.substring(2);
        return new ResourceLocation(base.getNamespace(), dir + path);
    }
}