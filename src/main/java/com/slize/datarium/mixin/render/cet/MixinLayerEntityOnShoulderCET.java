package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETRender;
import com.slize.datarium.client.cet.CETSubject;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerEntityOnShoulder;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(LayerEntityOnShoulder.class)
public abstract class MixinLayerEntityOnShoulderCET {

    @Inject(method = "renderEntityOnShoulder", at = @At("HEAD"))
    private void datarium$cetShoulderHead(EntityPlayer player, UUID uuid, NBTTagCompound compound,
                                          RenderLivingBase<? extends EntityLivingBase> renderer, ModelBase model,
                                          ResourceLocation texture, Class<?> entityClass, float limbSwing, float limbSwingAmount,
                                          float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                          boolean leftShoulder, CallbackInfoReturnable<?> cir) {
        UUID id = compound.hasUniqueId("UUID") ? compound.getUniqueId("UUID") : uuid;
        CETRender.beginVirtual(id == null ? null : CETSubject.virtual(id, player.world, new BlockPos(player)));
    }

    @Inject(method = "renderEntityOnShoulder", at = @At("RETURN"))
    private void datarium$cetShoulderReturn(EntityPlayer player, UUID uuid, NBTTagCompound compound,
                                            RenderLivingBase<? extends EntityLivingBase> renderer, ModelBase model,
                                            ResourceLocation texture, Class<?> entityClass, float limbSwing, float limbSwingAmount,
                                            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                            boolean leftShoulder, CallbackInfoReturnable<?> cir) {
        CETRender.end();
    }
}
