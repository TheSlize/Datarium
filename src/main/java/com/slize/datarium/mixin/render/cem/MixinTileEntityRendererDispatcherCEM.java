package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.tileentity.TileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(TileEntityRendererDispatcher.class)
public abstract class MixinTileEntityRendererDispatcherCEM {

    @Unique private final Deque<Boolean> datarium$sessions = new ArrayDeque<>();

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"))
    private void datarium$beginCem(TileEntity tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        boolean started = false;
        String modelName = CEMManager.getModelNameForTile(tile);
        if (modelName != null) {
            TileEntitySpecialRenderer<TileEntity> renderer = ((TileEntityRendererDispatcher) (Object) this).getRenderer(tile);
            if (renderer != null) {
                started = CEMGenericRender.beginTile(tile, modelName, renderer, modelName,
                        x * x + y * y + z * z, partialTicks);
            }
        }
        datarium$sessions.push(started);
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer;render(Lnet/minecraft/tileentity/TileEntity;DDDFIF)V"))
    private void datarium$endCem(TileEntity tile, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        Boolean started = datarium$sessions.poll();
        if (started != null && started) CEMGenericRender.end();
    }
}
