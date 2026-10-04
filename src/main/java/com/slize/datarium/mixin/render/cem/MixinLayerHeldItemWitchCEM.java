package com.slize.datarium.mixin.render.cem;

import com.slize.datarium.client.cem.CEMAttachments;
import com.slize.datarium.client.cem.CEMModelRenderer;
import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelWitch;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.entity.RenderWitch;
import net.minecraft.client.renderer.entity.layers.LayerHeldItemWitch;
import net.minecraft.entity.monster.EntityWitch;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LayerHeldItemWitch.class)
public abstract class MixinLayerHeldItemWitchCEM {

    @Shadow @Final private RenderWitch witchRenderer;

    // Th3_Sl1ze: don't worry I'm having pain looking at this too
    @Inject(method = "doRenderLayer(Lnet/minecraft/entity/monster/EntityWitch;FFFFFFF)V", at = @At("HEAD"), cancellable = true)
    private void datarium$modernPotionLayer(EntityWitch witch, float limbSwing, float limbSwingAmount, float partialTicks,
                                            float ageInTicks, float netHeadYaw, float headPitch, float scale, CallbackInfo ci) {
        ItemStack stack = witch.getHeldItemMainhand();
        if (stack.isEmpty() || stack.getItem() != Items.POTIONITEM) return;
        if (CEMRenderHooks.getActiveWrapper() == null || CEMAttachments.find(CEMAttachments.WITCH) != null) return;

        ModelWitch model = this.witchRenderer.getMainModel();
        CEMModelRenderer head = CEMRenderHooks.getReplacement(model.villagerHead);
        CEMModelRenderer nose = CEMRenderHooks.getReplacement(model.villagerNose);
        if (head == null || nose == null) return;

        GlStateManager.color(1.0F, 1.0F, 1.0F);
        GlStateManager.pushMatrix();
        head.applyPostRender(0.0625F);
        nose.applyPostRender(0.0625F);
        GlStateManager.translate(0.0625F, 0.25F, 0.0F);
        GlStateManager.rotate(180.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(140.0F, 1.0F, 0.0F, 0.0F);
        GlStateManager.rotate(10.0F, 0.0F, 0.0F, 1.0F);
        GlStateManager.rotate(180.0F, 1.0F, 0.0F, 0.0F);
        Minecraft.getMinecraft().getItemRenderer().renderItem(witch, stack, ItemCameraTransforms.TransformType.GROUND);
        GlStateManager.popMatrix();
        ci.cancel();
    }
}
