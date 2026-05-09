package com.slize.datarium.mixin;

import com.slize.datarium.client.cit.CITArmorHandler;
import net.minecraft.client.renderer.entity.layers.LayerArmorBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LayerArmorBase.class)
public abstract class MixinRenderArmorLayer {

    @Inject(method = "getArmorResource(Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/inventory/EntityEquipmentSlot;Ljava/lang/String;)Lnet/minecraft/util/ResourceLocation;",
            at = @At("RETURN"), cancellable = true)
    private void onGetArmorResource(Entity entity, ItemStack stack, EntityEquipmentSlot slot, String type,
                                    CallbackInfoReturnable<ResourceLocation> cir) {
        if (!(entity instanceof EntityLivingBase)) return;
        ResourceLocation cit = CITArmorHandler.getArmorTexture((EntityLivingBase) entity, stack, slot, type);
        if (cit != null) cir.setReturnValue(cit);
    }
}