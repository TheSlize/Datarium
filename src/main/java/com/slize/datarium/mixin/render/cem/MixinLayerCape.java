package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMModel;
import com.slize.datarium.client.cem.CEMRandomModels;
import com.slize.datarium.client.cem.CEMRenderHooks;
import com.slize.datarium.client.cem.CEMRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerCape;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerCape.class)
public abstract class MixinLayerCape {
    @Unique private static final String DATARIUM$RENDER = "doRenderLayer(Lnet/minecraft/client/entity/AbstractClientPlayer;FFFFFFF)V";

    @Shadow @Final private RenderPlayer playerRenderer;

    @Unique private boolean datarium$customCape;
    @Unique private boolean datarium$chestplate;
    @Unique private boolean datarium$session;

    @Inject(method = DATARIUM$RENDER, at = @At("HEAD"))
    private void datarium$beginCape(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks,
                                    float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        datarium$customCape = false;
        datarium$session = false;
        if (player.getLocationCape() == null) return;
        datarium$chestplate = !player.getItemStackFromSlot(EntityEquipmentSlot.CHEST).isEmpty();

        String active = CEMRenderHooks.getActiveModelName();
        if (CEMRenderHooks.getActiveWrapper() != null && active != null) {
            CEMModel model = CEMManager.getModel(active);
            datarium$customCape = model != null && model.customCape;
            return;
        }

        String base = CEMManager.firstExisting("player_cape");
        if (base == null) return;
        String name = CEMRandomModels.select(base, player);
        ModelPlayer model = playerRenderer.getMainModel();
        Entity view = Minecraft.getMinecraft().getRenderViewEntity();
        double distSq = view != null ? player.getDistanceSq(view) : 0.0D;
        CEMRenderState state = CEMManager.getAuxState(player, CEMManager.AUX_CAPE);
        datarium$session = CEMGenericRender.beginModel(model, model, name, state, distSq, (ctx, frameTime) -> {
            ctx.setup(player, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks, frameTime);
            ctx.setRuleIndex(CEMRandomModels.ruleIndex(player, name));
        });
        datarium$customCape = datarium$session;
    }

    @Inject(method = DATARIUM$RENDER, at = @At("RETURN"))
    private void datarium$endCape(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks,
                                  float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        if (datarium$session) CEMGenericRender.end();
        datarium$session = false;
        datarium$customCape = false;
    }

    @Redirect(method = DATARIUM$RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 0))
    private void datarium$swayPitch(float angle, float x, float y, float z) {
        if (!datarium$customCape) {
            GlStateManager.rotate(angle, x, y, z);
        } else if (datarium$chestplate) {
            GlStateManager.translate(0.0F, -0.0625F, 0.0625F);
        }
    }

    @Redirect(method = DATARIUM$RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 1))
    private void datarium$swayRoll(float angle, float x, float y, float z) {
        if (!datarium$customCape) GlStateManager.rotate(angle, x, y, z);
    }

    @Redirect(method = DATARIUM$RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V", ordinal = 2))
    private void datarium$swayYaw(float angle, float x, float y, float z) {
        if (!datarium$customCape) GlStateManager.rotate(angle, x, y, z);
    }
}
