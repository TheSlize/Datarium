package com.slize.datarium.client.cit;

import com.google.common.collect.ImmutableMap;
import com.google.gson.*;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.PerspectiveMapWrapper;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import javax.vecmath.Quat4f;
import javax.vecmath.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class CITModelLoader {

    private static final Gson GSON = new GsonBuilder().create();

    @Nullable
    public static IBakedModel loadAndBake(ResourceLocation modelLoc, ResourceLocation propertiesLoc, IBakedModel baseModel) {
        IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();

        // Resolve model path: the .json may be relative to the .properties dir
        ResourceLocation jsonLoc = modelLoc;
        if (!jsonLoc.getPath().endsWith(".json")) {
            jsonLoc = new ResourceLocation(jsonLoc.getNamespace(), jsonLoc.getPath() + ".json");
        }

        JsonObject json;
        try (InputStreamReader reader = new InputStreamReader(
                rm.getResource(jsonLoc).getInputStream(), StandardCharsets.UTF_8)) {
            json = GSON.fromJson(reader, JsonObject.class);
        } catch (Exception e) {
            return null;
        }

        // Parse texture_size for UV scaling
        float texScaleU = 1f, texScaleV = 1f;
        if (json.has("texture_size")) {
            var arr = json.getAsJsonArray("texture_size");
            float w = arr.get(0).getAsFloat();
            float h = arr.get(1).getAsFloat();
            texScaleU = 16f / w;
            texScaleV = 16f / h;
        }

        // Resolve textures map: "#0" -> actual sprite
        Map<String, TextureAtlasSprite> spriteMap = new HashMap<>();
        if (json.has("textures")) {
            JsonObject textures = json.getAsJsonObject("textures");
            for (Map.Entry<String, JsonElement> e : textures.entrySet()) {
                String key = e.getKey();
                String texPath = e.getValue().getAsString();
                if (texPath.startsWith("#")) continue;

                ResourceLocation texLoc;
                if (texPath.startsWith("./")) {
                    String dir = propertiesLoc.getPath();
                    int slash = dir.lastIndexOf('/');
                    String folder = slash >= 0 ? dir.substring(0, slash + 1) : "";
                    String rel = texPath.substring(2);
                    if (rel.endsWith(".png")) rel = rel.substring(0, rel.length() - 4);
                    texLoc = new ResourceLocation(propertiesLoc.getNamespace(), folder + rel);
                } else if (texPath.contains(":")) {
                    String[] parts = texPath.split(":", 2);
                    String p = parts[1].endsWith(".png") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
                    texLoc = new ResourceLocation(parts[0], p);
                } else {
                    String p = texPath.endsWith(".png") ? texPath.substring(0, texPath.length() - 4) : texPath;
                    texLoc = new ResourceLocation(propertiesLoc.getNamespace(), p);
                }

                String spriteName = texLoc.getNamespace() + ":" + texLoc.getPath();
                TextureAtlasSprite sprite = textureMap.getAtlasSprite(spriteName);
                if (sprite != null) spriteMap.put(key, sprite);
            }
        }

        if (spriteMap.isEmpty()) return null;

        // Parse elements and bake quads manually
        List<BakedQuad> quads = new ArrayList<>();
        FaceBakery bakery = new FaceBakery();

        if (json.has("elements")) {
            for (JsonElement elemEl : json.getAsJsonArray("elements")) {
                JsonObject elem = elemEl.getAsJsonObject();

                float[] from = jsonArrayToFloats(elem.getAsJsonArray("from"));
                float[] to   = jsonArrayToFloats(elem.getAsJsonArray("to"));

                org.lwjgl.util.vector.Vector3f posFrom = new org.lwjgl.util.vector.Vector3f(from[0], from[1], from[2]);
                org.lwjgl.util.vector.Vector3f posTo   = new org.lwjgl.util.vector.Vector3f(to[0],   to[1],   to[2]);

                BlockPartRotation rotation = null;
                if (elem.has("rotation")) {
                    JsonObject rot = elem.getAsJsonObject("rotation");
                    float angle = rot.get("angle").getAsFloat();
                    net.minecraft.util.EnumFacing.Axis axis = net.minecraft.util.EnumFacing.Axis.valueOf(
                            rot.get("axis").getAsString().toUpperCase());
                    float[] origin = jsonArrayToFloats(rot.getAsJsonArray("origin"));
                    org.lwjgl.util.vector.Vector3f originVec = new org.lwjgl.util.vector.Vector3f(origin[0], origin[1], origin[2]);
                    rotation = new BlockPartRotation(originVec, axis, angle, false);
                }

                if (!elem.has("faces")) continue;
                JsonObject faces = elem.getAsJsonObject("faces");

                for (Map.Entry<String, JsonElement> faceEntry : faces.entrySet()) {
                    net.minecraft.util.EnumFacing facing;
                    try {
                        facing = net.minecraft.util.EnumFacing.byName(faceEntry.getKey());
                    } catch (Exception ex) { continue; }
                    if (facing == null) continue;

                    JsonObject faceJson = faceEntry.getValue().getAsJsonObject();

                    // Get texture reference
                    String texRef = faceJson.has("texture") ? faceJson.get("texture").getAsString() : "#0";
                    if (texRef.startsWith("#")) texRef = texRef.substring(1);
                    TextureAtlasSprite sprite = spriteMap.get(texRef);
                    if (sprite == null) sprite = spriteMap.values().iterator().next();

                    // UV with texture_size scaling
                    float[] uv;
                    if (faceJson.has("uv")) {
                        uv = jsonArrayToFloats(faceJson.getAsJsonArray("uv"));
                        uv[0] *= texScaleU;
                        uv[1] *= texScaleV;
                        uv[2] *= texScaleU;
                        uv[3] *= texScaleV;
                    } else {
                        uv = new float[]{0, 0, 16, 16};
                    }

                    int tintIndex = faceJson.has("tintindex") ? faceJson.get("tintindex").getAsInt() : -1;
                    int rotation2d = faceJson.has("rotation") ? faceJson.get("rotation").getAsInt() : 0;

                    BlockFaceUV blockFaceUV = new BlockFaceUV(uv, rotation2d);
                    BlockPartFace partFace = new BlockPartFace(null, tintIndex, sprite.getIconName(), blockFaceUV);

                    try {
                        BakedQuad quad = bakery.makeBakedQuad(posFrom, posTo, partFace, sprite, facing,
                                ModelRotation.X0_Y0, rotation, false, true);
                        quads.add(quad);
                    } catch (Exception ex) {
                        // skip malformed face
                    }
                }
            }
        }

        if (quads.isEmpty()) return null;

        final List<BakedQuad> finalQuads = quads;
        final TextureAtlasSprite particle = spriteMap.values().iterator().next();

        final ImmutableMap<ItemCameraTransforms.TransformType, TRSRTransformation> citTransforms;
        if (json.has("display")) {
            ImmutableMap.Builder<ItemCameraTransforms.TransformType, TRSRTransformation> builder = ImmutableMap.builder();
            JsonObject display = json.getAsJsonObject("display");
            for (Map.Entry<String, JsonElement> e : display.entrySet()) {
                ItemCameraTransforms.TransformType type = parseTransformType(e.getKey());
                if (type == null) continue;
                JsonObject t = e.getValue().getAsJsonObject();
                float[] rot = jsonFloatArray(t, "rotation", new float[]{0,0,0});
                float[] trans = jsonFloatArray(t, "translation", new float[]{0,0,0});
                float[] scale = jsonFloatArray(t, "scale", new float[]{1,1,1});
                Quat4f q = TRSRTransformation.quatFromXYZDegrees(new Vector3f(rot[0], rot[1], rot[2]));
                Vector3f tv = new Vector3f(trans[0]/16f, trans[1]/16f, trans[2]/16f);
                Vector3f sv = new Vector3f(scale[0], scale[1], scale[2]);
                Vector3f center = new Vector3f(-0.5f, -0.5f, -0.5f);

                Matrix4f pre = new Matrix4f();
                pre.setIdentity();
                Vector3f negCenter = new Vector3f(-center.x, -center.y, -center.z);
                pre.setTranslation(negCenter);

                Matrix4f post = new Matrix4f();
                post.setIdentity();
                post.setTranslation(center);

                Matrix4f tMat = new Matrix4f();
                tMat.setIdentity();
                tMat.setTranslation(tv);

                Matrix4f rMat = new Matrix4f();
                rMat.set(q);

                Matrix4f sMat = new Matrix4f();
                sMat.setIdentity();
                sMat.m00 = sv.x; sMat.m11 = sv.y; sMat.m22 = sv.z;

                Matrix4f inner = new Matrix4f();
                inner.setIdentity();
                inner.mul(tMat);
                inner.mul(rMat);
                inner.mul(sMat);

                Matrix4f mat = new Matrix4f();
                mat.setIdentity();
                mat.mul(pre);
                mat.mul(inner);
                mat.mul(post);

                builder.put(type, new TRSRTransformation(mat));
            }
            citTransforms = builder.build();
        } else {
            citTransforms = ImmutableMap.of();
        }

        return new IBakedModel() {
            @Override public List<BakedQuad> getQuads(@Nullable IBlockState s, @Nullable EnumFacing side, long rand) {
                return side == null ? finalQuads : Collections.emptyList();
            }
            @Override public boolean isAmbientOcclusion() { return true; }
            @Override public boolean isGui3d() { return true; }
            @Override public boolean isBuiltInRenderer() { return false; }
            @Override public TextureAtlasSprite getParticleTexture() { return particle; }
            @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
            @Override
            public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType type) {

                if (!citTransforms.isEmpty()) {
                    return PerspectiveMapWrapper.handlePerspective(this, citTransforms, type);
                }
                if (baseModel != null) {
                    Pair<? extends IBakedModel, Matrix4f> pair = baseModel.handlePerspective(type);
                    return Pair.of(this, pair.getRight());
                }
                return PerspectiveMapWrapper.handlePerspective(this,
                        PerspectiveMapWrapper.getTransforms(ItemCameraTransforms.DEFAULT), type);
            }
        };
    }

    private static float[] jsonArrayToFloats(JsonArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) result[i] = arr.get(i).getAsFloat();
        return result;
    }

    private static ItemCameraTransforms.TransformType parseTransformType(String key) {
        return switch (key) {
            case "thirdperson_righthand" -> ItemCameraTransforms.TransformType.THIRD_PERSON_RIGHT_HAND;
            case "thirdperson_lefthand" -> ItemCameraTransforms.TransformType.THIRD_PERSON_LEFT_HAND;
            case "firstperson_righthand" -> ItemCameraTransforms.TransformType.FIRST_PERSON_RIGHT_HAND;
            case "firstperson_lefthand" -> ItemCameraTransforms.TransformType.FIRST_PERSON_LEFT_HAND;
            case "head" -> ItemCameraTransforms.TransformType.HEAD;
            case "gui" -> ItemCameraTransforms.TransformType.GUI;
            case "ground" -> ItemCameraTransforms.TransformType.GROUND;
            case "fixed" -> ItemCameraTransforms.TransformType.FIXED;
            default -> null;
        };
    }

    private static float[] jsonFloatArray(JsonObject obj, String key, float[] def) {
        if (!obj.has(key)) return def;
        com.google.gson.JsonArray arr = obj.getAsJsonArray(key);
        float[] res = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) res[i] = arr.get(i).getAsFloat();
        return res;
    }
}