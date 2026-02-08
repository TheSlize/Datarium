package com.slize.datarium.mixin;

import com.slize.datarium.util.DatariumContext;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
/**
 * So Mojang insisted on having a shitton of item render methods having different transform types in them instead of centralizing it in one chunky method.
 * Fucking thank you, I have to do the same shitton of injects to properly handle every transform type.
 * @author Th3_Sl1ze
 */
@Mixin(RenderItem.class)
public class MixinRenderItem {

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("HEAD"))
    public void onRenderItemWithEntityHead(ItemStack stack, @Nullable EntityLivingBase entity, TransformType transform, boolean leftHanded, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.set(transform);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("RETURN"))
    public void onRenderItemWithEntityReturn(ItemStack stack, @Nullable EntityLivingBase entity, TransformType transform, boolean leftHanded, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.remove();
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At("HEAD"))
    public void onRenderItemSimpleHead(ItemStack stack, TransformType cameraTransformType, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.set(cameraTransformType);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V",
            at = @At("RETURN"))
    public void onRenderItemSimpleReturn(ItemStack stack, TransformType cameraTransformType, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.remove();
    }

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/item/ItemStack;II)V",
            at = @At("HEAD"))
    public void onRenderItemAndEffectIntoGUIHead(ItemStack stack, int x, int y, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.set(TransformType.GUI);
    }

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/item/ItemStack;II)V",
            at = @At("RETURN"))
    public void onRenderItemAndEffectIntoGUIReturn(ItemStack stack, int x, int y, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.remove();
    }

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;II)V",
            at = @At("HEAD"))
    public void onRenderItemAndEffectIntoGUIWithEntityHead(@Nullable EntityLivingBase entity, ItemStack stack, int x, int y, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.set(TransformType.GUI);
    }

    @Inject(method = "renderItemAndEffectIntoGUI(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;II)V",
            at = @At("RETURN"))
    public void onRenderItemAndEffectIntoGUIWithEntityReturn(@Nullable EntityLivingBase entity, ItemStack stack, int x, int y, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.remove();
    }
}