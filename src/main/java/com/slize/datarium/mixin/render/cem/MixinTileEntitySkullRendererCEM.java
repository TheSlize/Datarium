package com.slize.datarium.mixin.render.cem;

import com.mojang.authlib.GameProfile;
import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.tileentity.TileEntitySkullRenderer;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.EnumFacing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;

@Mixin(TileEntitySkullRenderer.class)
public abstract class MixinTileEntitySkullRendererCEM {

    @Unique @Nullable private TileEntitySkull datarium$currentTile;
    @Unique private final Deque<Boolean> datarium$sessions = new ArrayDeque<>();

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntitySkull;DDDFIF)V", at = @At("HEAD"))
    private void datarium$captureTile(TileEntitySkull te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        datarium$currentTile = te;
    }

    @Inject(method = "render(Lnet/minecraft/tileentity/TileEntitySkull;DDDFIF)V", at = @At("RETURN"))
    private void datarium$releaseTile(TileEntitySkull te, double x, double y, double z, float partialTicks, int destroyStage, float alpha, CallbackInfo ci) {
        datarium$currentTile = null;
    }

    @Inject(method = "renderSkull", at = @At("HEAD"))
    private void datarium$beginCem(float x, float y, float z, EnumFacing facing, float rotation, int skullType,
                                   @Nullable GameProfile profile, int destroyStage, float animateTicks, CallbackInfo ci) {
        boolean started = false;
        String modelName = CEMManager.getModelNameForSkull(skullType);
        if (modelName != null) {
            started = CEMGenericRender.beginTile(datarium$currentTile, "skull_" + skullType, this,
                    modelName, x * x + y * y + z * z, Minecraft.getMinecraft().getRenderPartialTicks());
        }
        datarium$sessions.push(started);
    }

    @Inject(method = "renderSkull", at = @At("RETURN"))
    private void datarium$endCem(float x, float y, float z, EnumFacing facing, float rotation, int skullType,
                                 @Nullable GameProfile profile, int destroyStage, float animateTicks, CallbackInfo ci) {
        Boolean started = datarium$sessions.poll();
        if (started != null && started) CEMGenericRender.end();
    }
}
