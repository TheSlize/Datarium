package com.slize.datarium.client.cem;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.slize.datarium.DatariumMain;
import com.slize.datarium.mixin.accessors.AccessorModelBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class CEMModelExporter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> DISFAVORED_NAMES = new HashSet<>(Arrays.asList(
            "cape", "right_hind_leg", "left_hind_leg", "right_front_leg", "left_front_leg"));

    private CEMModelExporter() {}

    public static File exportDirectory() {
        return new File(Minecraft.getMinecraft().gameDir, "emf/export");
    }

    public static int exportAll() {
        RenderManager renderManager = Minecraft.getMinecraft().getRenderManager();
        Set<String> done = new HashSet<>();
        int count = 0;
        for (Map.Entry<String, RenderPlayer> entry : renderManager.getSkinMap().entrySet()) {
            String name = "slim".equals(entry.getKey()) ? "player_slim" : "player";
            if (done.add(name) && export(name, entry.getValue().getMainModel()) != null) count++;
        }
        for (Map.Entry<Class<? extends Entity>, Render<? extends Entity>> entry : renderManager.entityRenderMap.entrySet()) {
            if (!(entry.getValue() instanceof RenderLivingBase<?> living) || living.getMainModel() == null) continue;
            String name = CEMManager.exportNameForEntity(entry.getKey());
            if (name == null || !done.add(name)) continue;
            if (export(name, living.getMainModel()) != null) count++;
        }
        return count;
    }

    @Nullable
    public static File export(String name, ModelBase liveModel) {
        try {
            ModelBase model = restPoseInstance(liveModel, name);
            List<String> partNames = new ArrayList<>();
            JsonObject jem = build(name, model, partNames);
            File file = fileFor(name);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("cannot create " + parent);
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(jem, writer);
            }
            DatariumMain.LOGGER.info("[CEM] exported {} ({}) -> {} parts: {}", name, model.getClass().getName(), file, partNames);
            return file;
        } catch (Exception e) {
            DatariumMain.LOGGER.warn("[CEM] failed to export model {} ({})", name, liveModel.getClass().getName(), e);
            return null;
        }
    }

    private static File fileFor(String name) {
        String base = CEMManager.baseName(name);
        int colon = base.indexOf(':');
        File dir = exportDirectory();
        if (colon >= 0) dir = new File(dir, base.substring(0, colon));
        return new File(dir, CEMManager.fileNameOf(name) + ".jem");
    }

    private static ModelBase restPoseInstance(ModelBase model, String name) {
        if (model instanceof ModelPlayer) return new ModelPlayer(0.0F, "player_slim".equals(CEMManager.baseName(name)));
        try {
            return model.getClass().getConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        try {
            return model.getClass().getConstructor(float.class).newInstance(0.0F);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        return model;
    }

    private static JsonObject build(String name, ModelBase model, List<String> partNames) {
        Map<String, ModelRenderer> mapped = new TreeMap<>(CEMPartMapping.mapParts(model, name));

        Set<ModelRenderer> nested = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ModelRenderer renderer : mapped.values()) collectDescendants(renderer, nested);

        Map<ModelRenderer, String> childNames = new IdentityHashMap<>();
        Map<ModelRenderer, String> roots = new LinkedHashMap<>();
        List<Map.Entry<String, ModelRenderer>> ordered = new ArrayList<>(mapped.entrySet());
        ordered.sort(Comparator.comparing((Map.Entry<String, ModelRenderer> e) -> DISFAVORED_NAMES.contains(e.getKey()))
                .thenComparing(Map.Entry::getKey));
        for (Map.Entry<String, ModelRenderer> entry : ordered) {
            ModelRenderer renderer = entry.getValue();
            if (nested.contains(renderer)) {
                childNames.putIfAbsent(renderer, entry.getKey());
            } else if (!roots.containsKey(renderer)) {
                roots.put(renderer, entry.getKey());
            }
        }

        JsonObject jem = new JsonObject();
        jem.addProperty("credit", "Exported by Datarium");
        jem.add("textureSize", size(model.textureWidth, model.textureHeight));
        JsonArray models = new JsonArray();
        for (ModelRenderer renderer : model.boxList) {
            String partName = roots.get(renderer);
            if (partName == null) continue;
            partNames.add(partName);
            models.add(topLevel(partName, renderer, model, childNames));
            roots.remove(renderer);
        }
        for (Map.Entry<ModelRenderer, String> entry : roots.entrySet()) {
            partNames.add(entry.getValue());
            models.add(topLevel(entry.getValue(), entry.getKey(), model, childNames));
        }
        jem.add("models", models);
        return jem;
    }

    private static void collectDescendants(ModelRenderer renderer, Set<ModelRenderer> out) {
        if (renderer.childModels == null) return;
        for (ModelRenderer child : renderer.childModels) {
            if (out.add(child)) collectDescendants(child, out);
        }
    }

    private static JsonObject topLevel(String name, ModelRenderer renderer, ModelBase model, Map<ModelRenderer, String> childNames) {
        float px = renderer.rotationPointX + renderer.offsetX * 16.0F;
        float py = renderer.rotationPointY + renderer.offsetY * 16.0F;
        float pz = renderer.rotationPointZ + renderer.offsetZ * 16.0F;
        float[] shift = {px, py - 24.0F, pz};

        JsonObject part = new JsonObject();
        part.addProperty("part", name);
        part.addProperty("id", name);
        part.addProperty("invertAxis", "xy");
        part.add("translate", vec(px, py - 24.0F, -pz));
        part.add("rotate", vec(0, 0, 0));
        fillPart(part, name, renderer, model, shift, childNames);
        return part;
    }

    private static JsonObject child(String id, ModelRenderer renderer, ModelBase model, float[] parentShift,
                                    Map<ModelRenderer, String> childNames) {
        float cx = parentShift[0] + renderer.rotationPointX + renderer.offsetX * 16.0F;
        float cy = parentShift[1] + renderer.rotationPointY + renderer.offsetY * 16.0F;
        float cz = parentShift[2] + renderer.rotationPointZ + renderer.offsetZ * 16.0F;

        JsonObject part = new JsonObject();
        part.addProperty("id", id);
        part.addProperty("invertAxis", "xy");
        part.add("translate", vec(-cx, -cy, cz));
        part.add("rotate", vec((float) -Math.toDegrees(renderer.rotateAngleX), (float) -Math.toDegrees(renderer.rotateAngleY),
                (float) Math.toDegrees(renderer.rotateAngleZ)));
        fillPart(part, id, renderer, model, new float[]{0, 0, 0}, childNames);
        return part;
    }

    private static void fillPart(JsonObject part, String id, ModelRenderer renderer, ModelBase model, float[] shift,
                                 Map<ModelRenderer, String> childNames) {
        int texW = Math.round(renderer.textureWidth);
        int texH = Math.round(renderer.textureHeight);
        if (texW != model.textureWidth || texH != model.textureHeight) part.add("textureSize", size(texW, texH));

        JsonArray plain = new JsonArray();
        JsonArray mirrored = new JsonArray();
        for (ModelBox box : renderer.cubeList) {
            JsonObject json = box(box, renderer, shift);
            if (json == null) continue;
            if (json.remove("mirror") != null) mirrored.add(json);
            else plain.add(json);
        }

        JsonArray submodels = new JsonArray();
        if (plain.isEmpty() && !mirrored.isEmpty()) {
            part.addProperty("mirrorTexture", "u");
            part.add("boxes", mirrored);
        } else {
            part.add("boxes", plain);
            if (!mirrored.isEmpty()) {
                JsonObject split = new JsonObject();
                split.addProperty("id", id + "_mirrored");
                split.addProperty("invertAxis", "xy");
                split.add("translate", vec(0, 0, 0));
                split.addProperty("mirrorTexture", "u");
                split.add("boxes", mirrored);
                submodels.add(split);
            }
        }

        if (renderer.childModels != null) {
            for (int i = 0; i < renderer.childModels.size(); i++) {
                ModelRenderer childRenderer = renderer.childModels.get(i);
                String childId = childNames.getOrDefault(childRenderer, id + "_child" + (i + 1));
                submodels.add(child(childId, childRenderer, model, shift, childNames));
            }
        }
        if (!submodels.isEmpty()) part.add("submodels", submodels);
    }

    @Nullable
    private static JsonObject box(ModelBox box, ModelRenderer renderer, float[] shift) {
        TexturedQuad[] quads = ((AccessorModelBox) box).datarium$getQuadList();
        if (quads == null || quads.length < 3 || quads[0] == null || quads[1] == null || quads[2] == null) return null;

        float dx = box.posX2 - box.posX1;
        float dy = box.posY2 - box.posY1;
        float dz = box.posZ2 - box.posZ1;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        for (TexturedQuad quad : quads) {
            if (quad == null) continue;
            for (PositionTextureVertex vertex : quad.vertexPositions) {
                minX = Math.min(minX, (float) vertex.vector3D.x);
                minY = Math.min(minY, (float) vertex.vector3D.y);
            }
        }
        float delta = box.posY1 - minY;
        boolean mirror = dx + 2.0F * delta > 0.0F && (float) quads[0].vertexPositions[0].vector3D.x == minX;

        float minU = Float.MAX_VALUE;
        for (PositionTextureVertex vertex : quads[1].vertexPositions) minU = Math.min(minU, vertex.texturePositionX);
        float minV = Float.MAX_VALUE;
        for (PositionTextureVertex vertex : quads[2].vertexPositions) minV = Math.min(minV, vertex.texturePositionY);
        int texU = Math.round(minU * renderer.textureWidth);
        int texV = Math.round(minV * renderer.textureHeight);

        JsonObject json = new JsonObject();
        json.add("coordinates", array(
                -(shift[0] + box.posX2), -(shift[1] + box.posY2), shift[2] + box.posZ1, dx, dy, dz));
        json.add("textureOffset", size(texU, texV));
        if (delta != 0.0F) json.addProperty("sizeAdd", round(delta));
        if (mirror) json.addProperty("mirror", true);
        return json;
    }

    private static JsonArray vec(float x, float y, float z) {
        return array(x, y, z);
    }

    private static JsonArray array(float... values) {
        JsonArray array = new JsonArray();
        for (float value : values) array.add(round(value));
        return array;
    }

    private static JsonArray size(int width, int height) {
        JsonArray array = new JsonArray();
        array.add(width);
        array.add(height);
        return array;
    }

    private static float round(float value) {
        float rounded = Math.round(value * 10000.0F) / 10000.0F;
        return rounded == 0.0F ? 0.0F : rounded;
    }
}
