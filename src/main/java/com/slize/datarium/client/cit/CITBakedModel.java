package com.slize.datarium.client.cit;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.*;

public class CITBakedModel implements IBakedModel {
    private final IBakedModel baseModel;
    private final List<TextureAtlasSprite> layers;
    private final TextureAtlasSprite citSprite;
    private final List<BakedQuad> cachedQuads;

    public CITBakedModel(IBakedModel baseModel, List<TextureAtlasSprite> layers) {
        this.baseModel = baseModel;
        this.layers = layers;
        this.citSprite = layers.getFirst();
        this.cachedQuads = baseModel.isGui3d() ? null : generateItemQuads(layers);
    }

    public static List<BakedQuad> generateItemQuads(List<TextureAtlasSprite> layers) {
        Map<String, String> textures = new HashMap<>();
        for (int i = 0; i < layers.size(); i++) {
            textures.put("layer" + i, layers.get(i).getIconName());
        }

        ModelBlock dummy = new ModelBlock(null, new ArrayList<>(), textures, false, false, ItemCameraTransforms.DEFAULT, new ArrayList<>());
        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        ModelBlock result = new ItemModelGenerator().makeItemModel(textureMap, dummy);

        if (result == null || result.getElements().isEmpty()) {
            return Collections.emptyList();
        }

        FaceBakery bakery = new FaceBakery();
        List<BakedQuad> quads = new ArrayList<>();
        for (BlockPart part : result.getElements()) {
            for (Map.Entry<EnumFacing, BlockPartFace> face : part.mapFaces.entrySet()) {
                TextureAtlasSprite sprite = layerSprite(layers, face.getValue().texture);
                quads.add(bakery.makeBakedQuad(part.positionFrom, part.positionTo, face.getValue(), sprite, face.getKey(),
                        ModelRotation.X0_Y0, part.partRotation, false, true));
            }
        }
        return quads;
    }

    private static TextureAtlasSprite layerSprite(List<TextureAtlasSprite> layers, String texture) {
        String name = texture.startsWith("#") ? texture.substring(1) : texture;
        if (name.startsWith("layer")) {
            try {
                int index = Integer.parseInt(name.substring("layer".length()));
                if (index >= 0 && index < layers.size()) return layers.get(index);
            } catch (NumberFormatException ignored) {
            }
        }
        return layers.getFirst();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (cachedQuads != null) {
            return side == null ? cachedQuads : Collections.emptyList();
        }

        List<BakedQuad> originalQuads = baseModel.getQuads(state, side, rand);
        ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();
        for (BakedQuad quad : originalQuads) {
            builder.add(retextureQuad(quad, citSprite));
        }
        return builder.build();
    }

    private BakedQuad retextureQuad(BakedQuad original, TextureAtlasSprite newSprite) {
        TextureAtlasSprite originalSprite = original.getSprite();
        if (originalSprite == null) {
            return original;
        }

        int[] vertexData = original.getVertexData().clone();
        int stride = original.getFormat().getIntegerSize();
        int uvOffset = original.getFormat().getUvOffsetById(0) / 4;
        for (int v = 0; v < 4; v++) {
            int offset = v * stride + uvOffset;
            float u = Float.intBitsToFloat(vertexData[offset]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 1]);
            vertexData[offset] = Float.floatToRawIntBits(newSprite.getInterpolatedU(originalSprite.getUnInterpolatedU(u)));
            vertexData[offset + 1] = Float.floatToRawIntBits(newSprite.getInterpolatedV(originalSprite.getUnInterpolatedV(vCoord)));
        }

        return new BakedQuad(vertexData, original.getTintIndex(), original.getFace(), newSprite, original.shouldApplyDiffuseLighting(), original.getFormat());
    }

    @Override
    public boolean isAmbientOcclusion() {
        return baseModel.isAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return baseModel.isGui3d();
    }

    @Override
    public boolean isBuiltInRenderer() {
        return baseModel.isBuiltInRenderer();
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return citSprite;
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
        Pair<? extends IBakedModel, Matrix4f> pair = baseModel.handlePerspective(cameraTransformType);
        IBakedModel resultModel = pair.getLeft();

        if (resultModel == baseModel) {
            return Pair.of(this, pair.getRight());
        }

        return Pair.of(new CITBakedModel(resultModel, layers), pair.getRight());
    }
}
