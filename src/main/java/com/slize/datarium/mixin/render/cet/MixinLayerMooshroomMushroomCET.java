package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETMooshroom;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.entity.layers.LayerMooshroomMushroom;
import net.minecraft.init.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LayerMooshroomMushroom.class)
public abstract class MixinLayerMooshroomMushroomCET {

    @Redirect(method = "doRenderLayer(Lnet/minecraft/entity/passive/EntityMooshroom;FFFFFFF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BlockRendererDispatcher;renderBlockBrightness(Lnet/minecraft/block/state/IBlockState;F)V"))
    private void datarium$cetCustomMushroom(BlockRendererDispatcher dispatcher, IBlockState state, float brightness) {
        if (state.getBlock() == Blocks.RED_MUSHROOM && CETMooshroom.hasCustomRed()) {
            CETMooshroom.renderRed();
        } else {
            dispatcher.renderBlockBrightness(state, brightness);
        }
    }
}
