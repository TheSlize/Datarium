package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMRandomModels;
import com.slize.datarium.client.cem.CEMRenderState;
import net.minecraft.client.model.ModelShield;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TileEntityItemStackRenderer.class)
public abstract class MixinTileEntityItemStackRendererCEM {
    @Unique private static final String DATARIUM$SHIELD = "Lnet/minecraft/client/model/ModelShield;render()V";

    @Shadow @Final private ModelShield modelShield;

    @Unique private boolean datarium$shieldSession;

    @Inject(method = "renderByItem(Lnet/minecraft/item/ItemStack;F)V",
            at = @At(value = "INVOKE", target = DATARIUM$SHIELD))
    private void datarium$beginShield(ItemStack stack, float partialTicks, CallbackInfo ci) {
        datarium$shieldSession = false;
        String base = CEMManager.firstExisting("shield");
        if (base == null) return;

        Entity holder = CEMGenericRender.currentHolder();
        String name = holder != null ? CEMRandomModels.select(base, holder) : base;
        CEMRenderState state = holder != null ? CEMManager.getAuxState(holder, CEMManager.AUX_SHIELD) : CEMManager.getSharedState(name);
        long id = holder != null ? holder.getEntityId() : name.hashCode();
        float age = holder != null ? holder.ticksExisted + partialTicks : partialTicks;
        datarium$shieldSession = CEMGenericRender.beginModel(modelShield, modelShield, name, state, 0.0D, (ctx, frameTime) -> {
            ctx.setupGeneric(id, holder, null, 0, 0, age, partialTicks, frameTime);
            ctx.setRuleIndex(holder != null ? CEMRandomModels.ruleIndex(holder, name) : 0);
        });
    }

    @Inject(method = "renderByItem(Lnet/minecraft/item/ItemStack;F)V",
            at = @At(value = "INVOKE", target = DATARIUM$SHIELD, shift = At.Shift.AFTER))
    private void datarium$endShield(ItemStack stack, float partialTicks, CallbackInfo ci) {
        if (datarium$shieldSession) CEMGenericRender.end();
        datarium$shieldSession = false;
    }
}
