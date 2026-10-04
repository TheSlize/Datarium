package com.slize.datarium.client.cit;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import org.lwjgl.util.vector.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CITModelLoader {

    private static final Gson GSON = new GsonBuilder().create();

    private static final class ResolvedModel {
        final Map<String, String> textures = new HashMap<>();
        final Map<String, ResourceLocation> textureFiles = new HashMap<>();
        final Map<String, JsonObject> display = new HashMap<>();
        @Nullable
        JsonArray elements;
        boolean generated;
        boolean unsupported;
    }

    public static Set<ResourceLocation> collectTextures(ResourceLocation modelFile) {
        ResolvedModel model = resolve(modelFile, new HashSet<>());
        return model == null ? Collections.emptySet() : new HashSet<>(model.textureFiles.values());
    }

    @Nullable
    public static IBakedModel loadAndBake(ResourceLocation modelFile, @Nullable IBakedModel baseModel, @Nullable ResourceLocation overrideTexture) {
        ResolvedModel model = resolve(modelFile, new HashSet<>());
        if (model == null || model.unsupported) return null;

        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite override = null;
        if (overrideTexture != null) {
            override = textureMap.getTextureExtry(CITManager.spriteName(overrideTexture));
        }
        final TextureAtlasSprite forced = override;

        List<BakedQuad> quads;
        boolean gui3d;
        if (model.elements != null && !model.elements.isEmpty()) {
            quads = bakeElements(model, textureMap, forced);
            gui3d = true;
        } else if (model.generated) {
            List<TextureAtlasSprite> layers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                TextureAtlasSprite sprite = forced != null && i == 0 ? forced : sprite(model, textureMap, "layer" + i);
                if (sprite == null) break;
                layers.add(sprite);
                if (forced != null) break;
            }
            if (layers.isEmpty()) return null;
            quads = CITBakedModel.generateItemQuads(layers);
            gui3d = false;
        } else {
            return null;
        }
        if (quads.isEmpty()) return null;

        TextureAtlasSprite particleSprite = forced != null ? forced : sprite(model, textureMap, "particle");
        final TextureAtlasSprite particle = particleSprite != null ? particleSprite : quads.getFirst().getSprite();
        final List<BakedQuad> finalQuads = quads;
        final boolean finalGui3d = gui3d;
        final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> transforms = buildTransforms(model.display);

        return new IBakedModel() {
            @Override
            public List<BakedQuad> getQuads(@Nullable IBlockState s, @Nullable EnumFacing side, long rand) {
                return side == null ? finalQuads : Collections.emptyList();
            }

            @Override
            public boolean isAmbientOcclusion() {
                return false;
            }

            @Override
            public boolean isGui3d() {
                return finalGui3d;
            }

            @Override
            public boolean isBuiltInRenderer() {
                return false;
            }

            @Override
            public TextureAtlasSprite getParticleTexture() {
                return particle;
            }

            @Override
            public ItemOverrideList getOverrides() {
                return ItemOverrideList.NONE;
            }

            @Override
            public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {
                if (!transforms.isEmpty()) {
                    return PerspectiveMapWrapper.handlePerspective(this, transforms, type);
                }
                if (baseModel != null) {
                    return Pair.of(this, baseModel.handlePerspective(type).getRight());
                }
                return PerspectiveMapWrapper.handlePerspective(this,
                        PerspectiveMapWrapper.getTransforms(ItemCameraTransforms.DEFAULT), type);
            }
        };
    }

    @Nullable
    private static TextureAtlasSprite sprite(ResolvedModel model, TextureMap textureMap, String variable) {
        String name = variable;
        for (int depth = 0; depth < 16; depth++) {
            String value = model.textures.get(name.startsWith("#") ? name.substring(1) : name);
            if (value == null) return null;
            if (!value.startsWith("#")) return textureMap.getTextureExtry(value);
            name = value;
        }
        return null;
    }

    private static List<BakedQuad> bakeElements(ResolvedModel model, TextureMap textureMap, @Nullable TextureAtlasSprite forced) {
        List<BakedQuad> quads = new ArrayList<>();
        FaceBakery bakery = new FaceBakery();
        TextureAtlasSprite fallback = forced;
        if (fallback == null) {
            for (String key : model.textures.keySet()) {
                fallback = sprite(model, textureMap, key);
                if (fallback != null) break;
            }
        }
        if (fallback == null) fallback = textureMap.getMissingSprite();

        for (JsonElement elemEl : Objects.requireNonNull(model.elements)) {
            if (!elemEl.isJsonObject()) continue;
            JsonObject elem = elemEl.getAsJsonObject();
            if (!elem.has("from") || !elem.has("to") || !elem.has("faces")) continue;

            float[] from = floats(elem.getAsJsonArray("from"));
            float[] to = floats(elem.getAsJsonArray("to"));
            if (from.length < 3 || to.length < 3) continue;

            for (int axis = 0; axis < 3; axis++) {
                if (Math.abs(to[axis] - from[axis]) < 0.001f) {
                    from[axis] -= 0.01f;
                    to[axis] += 0.01f;
                    break;
                }
            }

            Vector3f posFrom = new Vector3f(from[0], from[1], from[2]);
            Vector3f posTo = new Vector3f(to[0], to[1], to[2]);

            BlockPartRotation rotation = null;
            if (elem.has("rotation") && elem.get("rotation").isJsonObject()) {
                JsonObject rot = elem.getAsJsonObject("rotation");
                try {
                    float angle = rot.get("angle").getAsFloat();
                    EnumFacing.Axis axis = EnumFacing.Axis.valueOf(rot.get("axis").getAsString().toUpperCase(Locale.ROOT));
                    float[] origin = rot.has("origin") ? floats(rot.getAsJsonArray("origin")) : new float[]{8, 8, 8};
                    boolean rescale = rot.has("rescale") && rot.get("rescale").getAsBoolean();
                    rotation = new BlockPartRotation(new Vector3f(origin[0] / 16f, origin[1] / 16f, origin[2] / 16f), axis, angle, rescale);
                } catch (Exception ignored) {
                }
            }
            boolean shade = !elem.has("shade") || elem.get("shade").getAsBoolean();

            for (Map.Entry<String, JsonElement> faceEntry : elem.getAsJsonObject("faces").entrySet()) {
                EnumFacing facing = EnumFacing.byName(faceEntry.getKey());
                if (facing == null || !faceEntry.getValue().isJsonObject()) continue;
                JsonObject faceJson = faceEntry.getValue().getAsJsonObject();

                TextureAtlasSprite sprite = forced;
                if (sprite == null && faceJson.has("texture")) {
                    sprite = sprite(model, textureMap, faceJson.get("texture").getAsString());
                }
                if (sprite == null) sprite = fallback;

                float[] uv = faceJson.has("uv") ? floats(faceJson.getAsJsonArray("uv")) : defaultUv(facing, from, to);
                if (uv.length < 4) uv = defaultUv(facing, from, to);
                int tintIndex = faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1;
                int faceRotation = faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0;

                BlockPartFace partFace = new BlockPartFace(null, tintIndex, sprite.getIconName(), new BlockFaceUV(uv, faceRotation));
                try {
                    quads.add(bakery.makeBakedQuad(posFrom, posTo, partFace, sprite, facing, ModelRotation.X0_Y0, rotation, false, shade));
                } catch (Exception ignored) {
                }
            }
        }
        return quads;
    }

    private static float[] defaultUv(EnumFacing facing, float[] from, float[] to) {
        return switch (facing) {
            case DOWN -> new float[]{from[0], 16f - to[2], to[0], 16f - from[2]};
            case UP -> new float[]{from[0], from[2], to[0], to[2]};
            case NORTH -> new float[]{16f - to[0], 16f - to[1], 16f - from[0], 16f - from[1]};
            case SOUTH -> new float[]{from[0], 16f - to[1], to[0], 16f - from[1]};
            case WEST -> new float[]{from[2], 16f - to[1], to[2], 16f - from[1]};
            case EAST -> new float[]{16f - to[2], 16f - to[1], 16f - from[2], 16f - from[1]};
        };
    }

    private static ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> buildTransforms(Map<String, JsonObject> display) {
        if (display.isEmpty()) return ImmutableMap.of();
        ImmutableMap.Builder<ItemCameraTransforms.TransformType, TRSRTransformation> builder = ImmutableMap.builder();
        for (Map.Entry<String, JsonObject> e : display.entrySet()) {
            ItemCameraTransforms.TransformType type = parseTransformType(e.getKey());
            if (type == null) continue;
            JsonObject t = e.getValue();
            float[] rot = jsonFloatArray(t, "rotation", new float[]{0, 0, 0});
            float[] trans = jsonFloatArray(t, "translation", new float[]{0, 0, 0});
            float[] scale = jsonFloatArray(t, "scale", new float[]{1, 1, 1});
            for (int i = 0; i < 3; i++) {
                trans[i] = Math.clamp(trans[i], -80f, 80f);
                scale[i] = Math.clamp(scale[i], -4f, 4f);
            }

            Matrix4f pre = new Matrix4f();
            pre.setIdentity();
            pre.m03 = 0.5f;
            pre.m13 = 0.5f;
            pre.m23 = 0.5f;

            Matrix4f post = new Matrix4f();
            post.setIdentity();
            post.m03 = -0.5f;
            post.m13 = -0.5f;
            post.m23 = -0.5f;

            Matrix4f tMat = new Matrix4f();
            tMat.setIdentity();
            tMat.m03 = trans[0] / 16f;
            tMat.m13 = trans[1] / 16f;
            tMat.m23 = trans[2] / 16f;

            Matrix4f rMat = new Matrix4f();
            rMat.set(TRSRTransformation.quatFromXYZ((float) Math.toRadians(rot[0]), (float) Math.toRadians(rot[1]), (float) Math.toRadians(rot[2])));

            Matrix4f sMat = new Matrix4f();
            sMat.setIdentity();
            sMat.m00 = scale[0];
            sMat.m11 = scale[1];
            sMat.m22 = scale[2];

            Matrix4f mat = new Matrix4f();
            mat.setIdentity();
            mat.mul(pre);
            mat.mul(tMat);
            mat.mul(rMat);
            mat.mul(sMat);
            mat.mul(post);

            builder.put(type, new TRSRTransformation(mat));
        }
        return builder.build();
    }

    @Nullable
    private static ItemCameraTransforms.TransformType parseTransformType(String key) {
        return switch (key) {
            case "thirdperson_righthand", "thirdperson" -> ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND;
            case "thirdperson_lefthand" -> ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND;
            case "firstperson_righthand", "firstperson" -> ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND;
            case "firstperson_lefthand" -> ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
            case "head" -> ItemCameraTransforms.TransformType.HEAD;
            case "gui" -> ItemCameraTransforms.TransformType.GUI;
            case "ground" -> ItemCameraTransforms.TransformType.GROUND;
            case "fixed" -> ItemCameraTransforms.TransformType.FIXED;
            default -> null;
        };
    }

    private static float[] floats(JsonArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) result[i] = arr.get(i).getAsFloat();
        return result;
    }

    private static float[] jsonFloatArray(JsonObject obj, String key, float[] def) {
        if (!obj.has(key) || !obj.get(key).isJsonArray()) return def;
        float[] res = floats(obj.getAsJsonArray(key));
        return res.length < 3 ? def : res;
    }

    @Nullable
    private static ResolvedModel resolve(ResourceLocation modelFile, Set<ResourceLocation> visited) {
        if (!visited.add(modelFile)) return null;
        JsonObject json = readModelJson(modelFile);
        if (json == null) return null;

        ResolvedModel resolved = new ResolvedModel();
        if (json.has("parent")) {
            String parent = json.get("parent").getAsString();
            String parentPath = parent.startsWith("minecraft:") ? parent.substring("minecraft:".length()) : parent;
            if (parentPath.equals("builtin/generated")) {
                resolved.generated = true;
            } else if (parentPath.startsWith("builtin/")) {
                resolved.unsupported = true;
            } else {
                ResourceLocation parentFile = CITManager.resolveAsset(modelFile, parent, CITManager.AssetKind.MODEL);
                if (parentFile != null) {
                    ResolvedModel parentModel = resolve(parentFile, visited);
                    if (parentModel != null) resolved = parentModel;
                }
            }
        }

        if (json.has("textures") && json.get("textures").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                if (!e.getValue().isJsonPrimitive()) continue;
                String value = e.getValue().getAsString();
                if (value.startsWith("#")) {
                    resolved.textures.put(e.getKey(), value);
                    continue;
                }
                ResourceLocation file = CITManager.resolveAsset(modelFile, value, CITManager.AssetKind.TEXTURE);
                if (file == null) continue;
                String spriteName = CITManager.spriteName(file);
                resolved.textures.put(e.getKey(), spriteName);
                resolved.textureFiles.put(spriteName, file);
            }
        }

        if (json.has("elements") && json.get("elements").isJsonArray()) {
            resolved.elements = json.getAsJsonArray("elements");
            resolved.unsupported = false;
        }

        if (json.has("display") && json.get("display").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("display").entrySet()) {
                if (e.getValue().isJsonObject()) resolved.display.put(e.getKey(), e.getValue().getAsJsonObject());
            }
        }

        return resolved;
    }

    @Nullable
    private static JsonObject readModelJson(ResourceLocation jsonLoc) {
        try (InputStreamReader reader = new InputStreamReader(
                Minecraft.getMinecraft().getResourceManager().getResource(jsonLoc).getInputStream(),
                StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            return null;
        }
    }
}
