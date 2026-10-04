package com.slize.datarium.client.cet.player;

import com.slize.datarium.client.cet.CETConfig;
import com.slize.datarium.client.cet.CETState;
import com.slize.datarium.client.cet.CETSubject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.client.renderer.entity.layers.LayerRenderer;
import net.minecraft.entity.player.EnumPlayerModelParts;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

public final class CETPlayerLayer implements LayerRenderer<AbstractClientPlayer> {
    private static final ResourceLocation VILLAGER_TEXTURE = new ResourceLocation("textures/entity/villager/villager.png");

    private final RenderPlayer renderer;
    private final ModelRenderer jacket;
    private final ModelRenderer fatJacket;
    private final ModelRenderer villagerNose;
    private final ModelRenderer textureNose;

    public CETPlayerLayer(RenderPlayer renderer) {
        this.renderer = renderer;
        ModelBase skinModel = new ModelBase() {};
        skinModel.textureWidth = 64;
        skinModel.textureHeight = 64;
        jacket = new ModelRenderer(skinModel, 16, 32);
        jacket.addBox(-4.0F, 12.5F, -2.0F, 8, 12, 4, 0.25F);
        fatJacket = new ModelRenderer(skinModel, 16, 32);
        fatJacket.addBox(-4.0F, 12.5F, -2.0F, 8, 12, 4, 0.75F);
        villagerNose = new ModelRenderer(skinModel, 24, 0);
        villagerNose.addBox(-1.0F, -3.0F, -6.0F, 2, 4, 2);
        villagerNose.setRotationPoint(0.0F, -2.0F, 0.0F);

        ModelBase noseModel = new ModelBase() {};
        noseModel.textureWidth = 8;
        noseModel.textureHeight = 8;
        textureNose = new ModelRenderer(noseModel, 0, 0);
        textureNose.addBox(0.0F, -8.0F, -8.0F, 0, 8, 4);
        textureNose.setRotationPoint(0.0F, -2.0F, 0.0F);
    }

    @Override
    public void doRenderLayer(AbstractClientPlayer player, float limbSwing, float limbSwingAmount, float partialTicks,
                              float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        if (!CETConfig.skinFeaturesEnabled || player.isInvisible()) return;
        CETPlayerTexture texture = CETPlayerTexture.of(player);
        if (texture == null) return;
        ModelPlayer model = renderer.getMainModel();

        if (texture.hasVillagerNose || texture.noseIdentifier != null) {
            GlStateManager.pushMatrix();
            if (player.isSneaking()) GlStateManager.translate(0.0F, 0.2F, 0.0F);
            model.bipedHead.postRender(scale);
            renderNose(texture, player, scale);
            GlStateManager.popMatrix();
        }

        ItemStack legs = player.getItemStackFromSlot(EntityEquipmentSlot.LEGS);
        boolean wearsLegArmor = legs.getItem() instanceof ItemArmor armor && armor.armorType == EntityEquipmentSlot.LEGS;
        if (texture.coatIdentifier != null && player.isWearing(EnumPlayerModelParts.JACKET) && !wearsLegArmor) {
            GlStateManager.pushMatrix();
            if (player.isSneaking()) GlStateManager.translate(0.0F, 0.2F, 0.0F);
            model.bipedBody.postRender(scale);
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture.coatIdentifier);
            (texture.hasFatCoat ? fatJacket : jacket).render(scale);
            GlStateManager.popMatrix();
        }
    }

    private void renderNose(CETPlayerTexture texture, AbstractClientPlayer player, float scale) {
        if (texture.hasVillagerNose) {
            if (texture.noseType == CETPlayerTexture.NoseType.VILLAGER_TEXTURED || texture.noseType == CETPlayerTexture.NoseType.VILLAGER_TEXTURED_REMOVE) {
                ResourceLocation skin = texture.skinFor(CETState.subject() != null ? CETState.subject() : CETSubject.of(player));
                if (skin == null) return;
                Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
            } else {
                CETState.pushModify(false);
                Minecraft.getMinecraft().getTextureManager().bindTexture(VILLAGER_TEXTURE);
                CETState.popModify();
            }
            villagerNose.render(scale);
        } else if (texture.noseIdentifier != null) {
            Minecraft.getMinecraft().getTextureManager().bindTexture(texture.noseIdentifier);
            textureNose.render(scale);
        }
    }

    @Override
    public boolean shouldCombineTextures() {
        return false;
    }
}
