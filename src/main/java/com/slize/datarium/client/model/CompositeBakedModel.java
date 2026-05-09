package com.slize.datarium.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.ArrayList;
import java.util.List;

public record CompositeBakedModel(List<IBakedModel> models) implements IBakedModel {

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        List<BakedQuad> quads = new ArrayList<>();
        for (IBakedModel model : models) {
            quads.addAll(model.getQuads(state, side, rand));
        }
        return quads;
    }

    @Override
    public boolean isAmbientOcclusion() {
        for (IBakedModel model : models) {
            if (model.isAmbientOcclusion()) return true;
        }
        return false;
    }

    @Override
    public boolean isGui3d() {
        for (IBakedModel model : models) {
            if (model.isGui3d()) return true;
        }
        return false;
    }

    @Override
    public boolean isBuiltInRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleTexture() {
        return models.isEmpty() ? null : models.getFirst().getParticleTexture();
    }

    @Override
    public ItemOverrideList getOverrides() {
        return ItemOverrideList.NONE;
    }

    @Override
    public Pair<? extends IBakedModel, Matrix4f> handlePerspective(ItemCameraTransforms.TransformType cameraTransformType) {
        List<IBakedModel> transformedModels = new ArrayList<>();
        Matrix4f matrix = null;

        for (IBakedModel model : models) {
            Pair<? extends IBakedModel, Matrix4f> pair = model.handlePerspective(cameraTransformType);
            transformedModels.add(pair.getLeft());

            if (matrix == null && pair.getRight() != null) {
                matrix = pair.getRight();
            }
        }

        return Pair.of(new CompositeBakedModel(transformedModels), matrix);
    }
}
