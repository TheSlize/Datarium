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
    private final TextureAtlasSprite citSprite;
    private final List<BakedQuad> cachedQuads;

    public CITBakedModel(IBakedModel baseModel, TextureAtlasSprite citSprite) {
        this.baseModel = baseModel;
        this.citSprite = citSprite;
        boolean isGui3d = baseModel.isGui3d();

        // If it's a generated item (not a block/gui3d), we must regenerate the mesh
        // to get the correct 3D extrusion for the new texture.
        if (citSprite != null && !isGui3d) {
            this.cachedQuads = generateItemQuads(citSprite);
        } else {
            this.cachedQuads = null;
        }
    }

    private List<BakedQuad> generateItemQuads(TextureAtlasSprite sprite) {
        // Setup dummy ModelBlock with the CIT texture as layer0
        Map<String, String> textures = new HashMap<>();
        textures.put("layer0", sprite.getIconName());

        // Create a dummy model block to pass to the generator.
        // We provide the texture mapping so the generator knows which sprite to use.
        ModelBlock dummy = new ModelBlock(null, new ArrayList<>(), textures, false, false, ItemCameraTransforms.DEFAULT, new ArrayList<>());

        ItemModelGenerator generator = new ItemModelGenerator();
        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();
        ModelBlock result = generator.makeItemModel(textureMap, dummy);

        if (result == null || result.getElements().isEmpty()) {
            return Collections.emptyList();
        }

        // Bake the elements into quads using FaceBakery
        FaceBakery bakery = new FaceBakery();
        List<BakedQuad> quads = new ArrayList<>();

        for (BlockPart part : result.getElements()) {
            for (EnumFacing side : part.mapFaces.keySet()) {
                BlockPartFace face = part.mapFaces.get(side);
                quads.add(bakery.makeBakedQuad(part.positionFrom, part.positionTo, face, sprite, side, ModelRotation.X0_Y0, part.partRotation, false, true));
            }
        }

        return quads;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (citSprite == null) {
            return baseModel.getQuads(state, side, rand);
        }

        // Use the newly generated quads that match the CIT texture's shape
        if (cachedQuads != null) {
            if (side != null) {
                return Collections.emptyList();
            }
            return cachedQuads;
        }

        // Or retexture the existing geometry (e.g. for blocks) to preserve complex shapes
        List<BakedQuad> originalQuads = baseModel.getQuads(state, side, rand);
        ImmutableList.Builder<BakedQuad> builder = ImmutableList.builder();

        for (BakedQuad quad : originalQuads) {
            builder.add(retextureQuad(quad, citSprite));
        }

        return builder.build();
    }

    private BakedQuad retextureQuad(BakedQuad original, TextureAtlasSprite newSprite) {
        int[] vertexData = original.getVertexData().clone();
        TextureAtlasSprite originalSprite = original.getSprite();

        if (originalSprite == null) {
            return original;
        }

        for (int v = 0; v < 4; v++) {
            int offset = v * 7;

            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float vCoord = Float.intBitsToFloat(vertexData[offset + 5]);

            float normalizedU = originalSprite.getUnInterpolatedU(u);
            float normalizedV = originalSprite.getUnInterpolatedV(vCoord);

            float newU = newSprite.getInterpolatedU(normalizedU);
            float newV = newSprite.getInterpolatedV(normalizedV);

            vertexData[offset + 4] = Float.floatToRawIntBits(newU);
            vertexData[offset + 5] = Float.floatToRawIntBits(newV);
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
        return citSprite != null ? citSprite : baseModel.getParticleTexture();
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

        return Pair.of(new CITBakedModel(resultModel, citSprite), pair.getRight());
    }
}