package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETRender;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcherCET {

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"))
    private void datarium$beginCet(TileEntity tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        CETRender.beginTile(tile);
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"))
    private void datarium$endCet(TileEntity tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        CETRender.end();
    }

    @Redirect(method = "render(Lnet/minecraft/tileentity/TileEntity;FI)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;getCombinedLight(Lnet/minecraft/util/math/BlockPos;I)I"))
    private int datarium$cetLightOverride(World world, BlockPos pos, int lightValue, TileEntity tile, float partialTicks, int destroyStage) {
        return CETRender.tileLight(tile, world.getCombinedLight(pos, lightValue));
    }
}
