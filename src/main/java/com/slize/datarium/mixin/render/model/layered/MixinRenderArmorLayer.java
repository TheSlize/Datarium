package com.slize.datarium.mixin.render.model.layered;

import com.slize.datarium.client.cem.CEMArmorModels;
import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMRenderHooks;
import com.slize.datarium.client.cit.CITArmorHandler;
import net.minecraft.item.ItemArmor;
import org.spongepowered.asm.mixin.Unique;
import com.slize.datarium.client.cit.CITGlintRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LayerArmorBase.class)
public abstract class MixinRenderArmorLayer {

    @Inject(method = "getArmorResource(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/inventory/EntityEquipmentSlot;Ljava/lang/String;)Lnet/minecraft/util/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void onGetArmorResource(Entity entity, ItemStack stack, EntityEquipmentSlot slot, String type,
                                    CallbackInfoReturnable<ResourceLocation> cir) {
        if (!(entity instanceof EntityLivingBase)) return;
        ResourceLocation cit = CITArmorHandler.getArmorTexture(stack, slot, type);
        if (cit != null) cir.setReturnValue(cit);
    }

    @Unique private boolean datarium$armorSession;

    @Inject(method = "renderArmorLayer", at = @At("HEAD"))
    private void datarium$captureArmorStack(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                            float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                            EntityEquipmentSlot slot, CallbackInfo ci) {
        ItemStack stack = entity.getItemStackFromSlot(slot);
        CITGlintRenderer.setArmorStack(stack);

        datarium$armorSession = false;
        if (!(stack.getItem() instanceof ItemArmor armor) || armor.getEquipmentSlot() != slot) return;
        boolean inner = slot == EntityEquipmentSlot.LEGS;
        ModelBase model = ((LayerArmorBase<?>) (Object) this).getModelFromSlot(slot);
        CEMArmorModels.beginLayer(inner, model);
        if (CEMRenderHooks.getActiveWrapper() == null) {
            datarium$armorSession = CEMArmorModels.beginStandalone(entity, model, inner, limbSwing, limbSwingAmount,
                    partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }

    @Inject(method = "renderArmorLayer", at = @At("RETURN"))
    private void datarium$releaseArmorStack(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float partialTicks,
                                            float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                            EntityEquipmentSlot slot, CallbackInfo ci) {
        CITGlintRenderer.setArmorStack(ItemStack.EMPTY);
        if (datarium$armorSession) {
            datarium$armorSession = false;
            CEMGenericRender.end();
        }
        CEMArmorModels.endLayer();
    }

    @Inject(method = "renderEnchantedGlint", at = @At("HEAD"), cancellable = true)
    private static void datarium$onRenderEnchantedGlint(RenderLivingBase<?> renderer, EntityLivingBase entity, ModelBase model,
                                                        float limbSwing, float limbSwingAmount, float partialTicks,
                                                        float ageInTicks, float netHeadYaw, float headPitch, float scale,
                                                        CallbackInfo ci) {
        if (CITGlintRenderer.renderArmorGlint(entity, model, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale)) {
            ci.cancel();
        }
    }
}
