package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMAttachments;
import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelParrot;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerEntityOnShoulder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(LayerEntityOnShoulder.class)
public abstract class MixinLayerEntityOnShoulderCEM {

    @Unique private EntityPlayer datarium$player;
    @Unique private boolean datarium$left;
    @Unique private float datarium$limbSwing, datarium$limbSwingAmount, datarium$partialTicks;
    @Unique private float datarium$netHeadYaw, datarium$headPitch;

    @Inject(method = "renderEntityOnShoulder", at = @At("HEAD"))
    private void datarium$capture(EntityPlayer player, UUID uuid, NBTTagCompound nbt,
                                  RenderLivingBase<? extends EntityLivingBase> renderer, ModelBase model,
                                  ResourceLocation texture, Class<?> entityClass, float limbSwing, float limbSwingAmount,
                                  float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                  boolean left, CallbackInfoReturnable<?> cir) {
        datarium$player = player;
        datarium$left = left;
        datarium$limbSwing = limbSwing;
        datarium$limbSwingAmount = limbSwingAmount;
        datarium$partialTicks = partialTicks;
        datarium$netHeadYaw = netHeadYaw;
        datarium$headPitch = headPitch;
    }

    @Redirect(method = "renderEntityOnShoulder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V", ordinal = 0))
    private void datarium$shoulderOffset(float x, float y, float z) {
        boolean attached = datarium$left
                ? CEMAttachments.apply(CEMAttachments.PARROT_LEFT, 0.4F, -3.0F, 0.0F)
                : CEMAttachments.apply(CEMAttachments.PARROT_RIGHT, -0.4F, -3.0F, 0.0F);
        if (!attached) attached = CEMAttachments.applyParrotAuto(datarium$left);
        if (!attached) GlStateManager.translate(x, y, z);
    }

    @Redirect(method = "renderEntityOnShoulder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/ModelBase;render(Lnet/minecraft/entity/Entity;FFFFFF)V"))
    private void datarium$renderShoulderModel(ModelBase model, Entity entity, float limbSwing, float limbSwingAmount,
                                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        EntityPlayer player = datarium$player;
        String name = model instanceof ModelParrot && player != null ? CEMManager.firstExisting("parrot") : null;
        boolean session = false;
        if (name != null) {
            boolean left = datarium$left;
            float partialTicks = datarium$partialTicks;
            float swing = datarium$limbSwing, swingAmount = datarium$limbSwingAmount;
            float yaw = datarium$netHeadYaw, pitch = datarium$headPitch;
            Entity view = Minecraft.getMinecraft().getRenderViewEntity();
            double distSq = view != null ? player.getDistanceSq(view) : 0.0D;
            CEMRenderState state = CEMManager.getAuxState(player, left ? CEMManager.AUX_SHOULDER_LEFT : CEMManager.AUX_SHOULDER_RIGHT);
            session = CEMGenericRender.beginModel(model, model, name, state, distSq, (ctx, frameTime) ->
                    ctx.setupShoulder(player, left, swing, swingAmount, ageInTicks, yaw, pitch, partialTicks, frameTime));
        }
        try {
            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
        } finally {
            if (session) CEMGenericRender.end();
        }
    }
}
