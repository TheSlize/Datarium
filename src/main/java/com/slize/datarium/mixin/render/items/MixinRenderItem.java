package com.slize.datarium.mixin.render.items;

import com.slize.datarium.client.cit.CITGlintRenderer;
import com.slize.datarium.client.cit.CITManager;
import com.slize.datarium.util.DatariumContext;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumHandSide;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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
        CITManager.setRenderOffHand(entity != null && leftHanded != (entity.getPrimaryHand() == EnumHandSide.LEFT));
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("RETURN"))
    public void onRenderItemWithEntityReturn(ItemStack stack, @Nullable EntityLivingBase entity, TransformType transform, boolean leftHanded, CallbackInfo ci) {
        DatariumContext.CURRENT_TRANSFORM.remove();
        CITManager.setRenderOffHand(false);
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

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("HEAD"))
    public void datarium$onRenderHeadItemPre(ItemStack stack, @Nullable EntityLivingBase entity, TransformType transform, boolean leftHanded, CallbackInfo ci) {
        if (transform == TransformType.HEAD) {
            GlStateManager.disableLighting();
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
            GL11.glPolygonOffset(-2.0f, -4.0f);
        }
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
            at = @At("RETURN"))
    public void datarium$onRenderHeadItemPost(ItemStack stack, @Nullable EntityLivingBase entity, TransformType transform, boolean leftHanded, CallbackInfo ci) {
        if (transform == TransformType.HEAD) {
            GL11.glPolygonOffset(0.0f, 0.0f);
            GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL);
            GlStateManager.enableLighting();
        }
    }

    @Shadow
    private void renderModel(IBakedModel model, int color) {
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            at = @At("HEAD"))
    private void datarium$captureGlintStack(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        CITGlintRenderer.setItemStack(stack);
    }

    @Inject(method = "renderEffect", at = @At("HEAD"), cancellable = true)
    private void datarium$onRenderEffect(IBakedModel model, CallbackInfo ci) {
        if (CITGlintRenderer.renderItemGlint(color -> this.renderModel(model, color))) {
            ci.cancel();
        }
    }
}
