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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CEMModelLoader {
    private static final Pattern SELF_REFERENCE = Pattern.compile("(?<![\\w.:])(this|part)(?=[.:])");
    private static final AtomicInteger ANONYMOUS_IDS = new AtomicInteger();

    @Nullable
    public static CEMModel loadJEM(ResourceLocation location) {
        JsonObject root = readJson(location);
        if (root == null) return null;

        CEMModel model = new CEMModel();

        if (root.has("credit")) model.credit = root.get("credit").getAsString();
        if (root.has("textureSize")) model.textureSize = parseSize(root.getAsJsonArray("textureSize"));
        if (root.has("texture")) model.texture = resolveTexture(location, root.get("texture").getAsString());
        JsonElement shadowSize = root.has("shadowSize") ? root.get("shadowSize") : root.get("shadow_size");
        if (shadowSize != null && shadowSize.isJsonPrimitive()) {
            CEMAnimation shadow = new CEMAnimation();
            shadow.expressions.put("render.shadow_size", shadowSize.getAsString());
            model.animations.add(shadow);
        }

        if (root.has("models")) {
            Map<String, JsonObject> byId = new HashMap<>();
            for (JsonElement elem : root.getAsJsonArray("models")) {
                JsonObject obj = elem.getAsJsonObject();
                if (obj.has("id")) byId.putIfAbsent(obj.get("id").getAsString(), obj);
            }
            for (JsonElement elem : root.getAsJsonArray("models")) {
                JsonObject obj = elem.getAsJsonObject();
                JsonObject baseObj = obj.has("baseId") ? byId.get(obj.get("baseId").getAsString()) : null;
                model.parts.add(parseModelPart(obj, baseObj == obj ? null : baseObj, null, location, model));
            }
        }
        readAnimations(root, model, null);
        resolveSelfReferences(model);

        for (CEMModelPart part : model.parts) inheritTexture(part, model.texture, model.textureSize);

        model.indexParts();
        return model;
    }

    private static CEMModelPart parseModelPart(JsonObject obj, @Nullable JsonObject baseObj, @Nullable CEMModelPart parent,
                                               ResourceLocation base, CEMModel target) {
        CEMModelPart part = new CEMModelPart();
        part.parent = parent;

        if (baseObj != null) {
            applyModelReference(baseObj, part, base, target, false);
            applyPartJson(baseObj, part, base, target, false);
        }

        // "model" pulls in a .jpm: it supplies geometry and defaults, the referencing entry overrides them.
        // Fresh Animations does this at any depth, so submodels go through here too.
        applyModelReference(obj, part, base, target, true);
        applyPartJson(obj, part, base, target, true);
        return part;
    }

    private static void applyModelReference(JsonObject obj, CEMModelPart part, ResourceLocation base,
                                            CEMModel target, boolean withAnimations) {
        if (!obj.has("model")) return;
        ResourceLocation loc = resolveModel(base, obj.get("model").getAsString());
        JsonObject jpm = loc != null ? readJson(loc) : null;
        if (jpm != null) applyPartJson(jpm, part, loc, target, withAnimations);
    }

    private static void applyPartJson(JsonObject obj, CEMModelPart part, ResourceLocation base,
                                      CEMModel target, boolean withAnimations) {
        if (obj.has("part")) part.part = obj.get("part").getAsString();
        if (obj.has("id")) part.id = obj.get("id").getAsString();
        if (obj.has("attach")) part.attach = obj.get("attach").getAsString().equalsIgnoreCase("true");
        if (obj.has("invertAxis")) part.invertAxis = obj.get("invertAxis").getAsString();
        if (obj.has("mirrorTexture")) part.mirrorTexture = obj.get("mirrorTexture").getAsString();
        if (obj.has("texture")) {
            ResourceLocation texture = resolveTexture(base, obj.get("texture").getAsString());
            if (texture != null) part.texture = texture;
        }
        if (obj.has("textureSize")) part.textureSize = parseSize(obj.getAsJsonArray("textureSize"));

        if (obj.has("translate")) part.translate = parseVec3(obj.getAsJsonArray("translate"));
        if (obj.has("rotate")) part.rotate = parseVec3(obj.getAsJsonArray("rotate"));
        if (obj.has("scale")) {
            if (obj.get("scale").isJsonArray()) {
                part.scale = parseVec3(obj.getAsJsonArray("scale"));
            } else {
                float s = obj.get("scale").getAsFloat();
                part.scale = new float[]{s, s, s};
            }
        }

        if (obj.has("attachments") && obj.get("attachments").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : obj.getAsJsonObject("attachments").entrySet()) {
                if (entry.getValue().isJsonArray() && entry.getValue().getAsJsonArray().size() >= 3) {
                    part.attachments.put(entry.getKey().toLowerCase(Locale.ROOT), parseVec3(entry.getValue().getAsJsonArray()));
                }
            }
        }

        if (obj.has("boxes")) {
            for (JsonElement elem : obj.getAsJsonArray("boxes")) part.boxes.add(parseBox(elem.getAsJsonObject()));
        }
        if (obj.has("elements")) {
            for (JsonElement elem : obj.getAsJsonArray("elements")) part.boxes.add(parseBox(elem.getAsJsonObject()));
        }

        if (obj.has("submodel") && obj.get("submodel").isJsonObject()) {
            part.submodels.add(parseModelPart(obj.getAsJsonObject("submodel"), null, part, base, target));
        }
        if (obj.has("submodels")) {
            for (JsonElement elem : obj.getAsJsonArray("submodels")) {
                part.submodels.add(parseModelPart(elem.getAsJsonObject(), null, part, base, target));
            }
        }

        if (withAnimations) readAnimations(obj, target, part);
    }

    private static void inheritTexture(CEMModelPart part, @Nullable ResourceLocation texture, int[] textureSize) {
        if (part.texture == null) part.texture = texture;
        if (part.textureSize == null) part.textureSize = textureSize;
        for (CEMModelPart sub : part.submodels) inheritTexture(sub, part.texture, part.textureSize);
    }

    private static void readAnimations(JsonObject obj, CEMModel target, @Nullable CEMModelPart owner) {
        if (!obj.has("animations")) return;
        for (JsonElement elem : obj.getAsJsonArray("animations")) {
            CEMAnimation anim = new CEMAnimation();
            anim.owner = owner;
            for (Map.Entry<String, JsonElement> entry : elem.getAsJsonObject().entrySet()) {
                anim.expressions.put(entry.getKey(), entry.getValue().getAsString());
            }
            target.animations.add(anim);
        }
    }

    private static void resolveSelfReferences(CEMModel model) {
        for (CEMAnimation anim : model.animations) {
            if (anim.owner == null) continue;
            String self = selfName(anim.owner);
            String original = rootPartName(anim.owner);
            Map<String, String> rewritten = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : anim.expressions.entrySet()) {
                rewritten.put(rewriteSelf(entry.getKey(), self, original), rewriteSelf(entry.getValue(), self, original));
            }
            anim.expressions = rewritten;
        }
    }

    private static String selfName(CEMModelPart part) {
        if (part.id != null && !part.id.isEmpty()) return part.id;
        if (part.parent == null && part.part != null && !part.part.isEmpty()) return part.part;
        part.id = "cem_this_" + ANONYMOUS_IDS.incrementAndGet();
        return part.id;
    }

    @Nullable
    private static String rootPartName(CEMModelPart part) {
        CEMModelPart root = part;
        while (root.parent != null) root = root.parent;
        if (root.part != null && !root.part.isEmpty()) return root.part;
        return root.id != null && !root.id.isEmpty() ? root.id : null;
    }

    private static String rewriteSelf(String text, String self, @Nullable String original) {
        Matcher matcher = SELF_REFERENCE.matcher(text);
        if (!matcher.find()) return text;
        StringBuilder out = new StringBuilder();
        do {
            String name = matcher.group(1).equals("this") ? self : original;
            matcher.appendReplacement(out, Matcher.quoteReplacement(name != null ? name : matcher.group(1)));
        } while (matcher.find());
        matcher.appendTail(out);
        return out.toString();
    }

    @Nullable
    private static JsonObject readJson(ResourceLocation location) {
        try {
            IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
            String json;
            try (InputStream is = resource.getInputStream()) {
                json = IOUtils.toString(is, StandardCharsets.UTF_8);
            }
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (Exception e) {
            DatariumMain.LOGGER.debug("Failed to load CEM json: {}", location, e);
            return null;
        }
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
        if (obj.has("sizeAdd")) {
            box.sizeAdd = obj.get("sizeAdd").getAsFloat();
            box.sizeAddX = box.sizeAddY = box.sizeAddZ = box.sizeAdd;
        }
        if (obj.has("sizesAdd")) {
            float[] sizes = parseVec3(obj.getAsJsonArray("sizesAdd"));
            box.sizeAddX = sizes[0];
            box.sizeAddY = sizes[1];
            box.sizeAddZ = sizes[2];
        }
        if (obj.has("sizeAddX")) box.sizeAddX = obj.get("sizeAddX").getAsFloat();
        if (obj.has("sizeAddY")) box.sizeAddY = obj.get("sizeAddY").getAsFloat();
        if (obj.has("sizeAddZ")) box.sizeAddZ = obj.get("sizeAddZ").getAsFloat();
        box.uvNorth = parseUV(obj, "uvNorth", "uvFront");
        box.uvSouth = parseUV(obj, "uvSouth", "uvBack");
        box.uvEast = parseUV(obj, "uvEast", "uvRight");
        box.uvWest = parseUV(obj, "uvWest", "uvLeft");
        box.uvUp = parseUV(obj, "uvUp", null);
        box.uvDown = parseUV(obj, "uvDown", null);
        return box;
    }

    @Nullable
    private static float[] parseUV(JsonObject obj, String key, @Nullable String alias) {
        JsonElement elem = obj.has(key) ? obj.get(key) : alias != null && obj.has(alias) ? obj.get(alias) : null;
        if (elem == null || !elem.isJsonArray()) return null;
        JsonArray arr = elem.getAsJsonArray();
        return new float[]{
                arr.get(0).getAsFloat(), arr.get(1).getAsFloat(),
                arr.get(2).getAsFloat(), arr.get(3).getAsFloat()
        };
    }

    private static float[] parseVec3(JsonArray arr) {
        return new float[]{arr.get(0).getAsFloat(), arr.get(1).getAsFloat(), arr.get(2).getAsFloat()};
    }

    private static int[] parseSize(JsonArray arr) {
        return new int[]{arr.get(0).getAsInt(), arr.get(1).getAsInt()};
    }

    @Nullable
    private static ResourceLocation resolveModel(ResourceLocation base, String path) {
        String withExt = path.endsWith(".jpm") || path.endsWith(".jem") ? path : path + ".jpm";
        ResourceLocation relative = resolveOptiFinePath(base, withExt, true);
        if (relative != null && exists(relative)) return relative;
        ResourceLocation optifine = resolveOptiFinePath(base, withExt, false);
        return optifine != null && exists(optifine) ? optifine : relative;
    }

    @Nullable
    private static ResourceLocation resolveTexture(ResourceLocation base, String path) {
        if (path == null || path.isEmpty()) return null;
        String withExt = path.endsWith(".png") ? path : path + ".png";
        ResourceLocation loc = resolveOptiFinePath(base, withExt, false);
        if (loc != null && exists(loc)) return loc;
        ResourceLocation relative = resolveOptiFinePath(base, withExt, true);
        if (relative != null && exists(relative)) return relative;
        DatariumMain.LOGGER.warn("[CEM] texture '{}' referenced by {} was not found", path, base);
        return null;
    }

    @Nullable
    private static ResourceLocation resolveOptiFinePath(ResourceLocation base, String path, boolean forceRelative) {
        try {
            String basePath = base.getPath();
            int lastSlash = basePath.lastIndexOf('/');
            String dir = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "";
            if (path.startsWith("./")) return new ResourceLocation(base.getNamespace(), dir + path.substring(2));
            if (path.startsWith("~/")) return new ResourceLocation(base.getNamespace(), "optifine/" + path.substring(2));
            int colon = path.indexOf(':');
            if (colon > 0) return new ResourceLocation(path.substring(0, colon), path.substring(colon + 1));
            if (forceRelative || path.indexOf('/') < 0) return new ResourceLocation(base.getNamespace(), dir + path);
            return new ResourceLocation(base.getNamespace(), path);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean exists(ResourceLocation location) {
        try {
            Minecraft.getMinecraft().getResourceManager().getResource(location).close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
