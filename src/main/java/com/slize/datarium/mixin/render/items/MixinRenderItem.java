package com.slize.datarium.mixin.render.items;

import com.slize.datarium.client.cit.CITEntry;
import com.slize.datarium.client.cit.CITManager;
import com.slize.datarium.client.cit.GlobalCITProperties;
import com.slize.datarium.util.DatariumContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;

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

    @Inject(method = "renderItemIntoGUI", at = @At("RETURN"))
    private void afterRenderItemIntoGUI(ItemStack stack, int x, int y, CallbackInfo ci) {
        if (stack.isEmpty() || !stack.isItemEnchanted()) return;
        List<CITEntry> enchEntries = CITManager.getMatchesOfType(stack, CITEntry.CITType.ENCHANTMENT);
        if (enchEntries.isEmpty()) return;

        int cap = GlobalCITProperties.getCap();
        enchEntries.sort(Comparator.comparingInt(CITEntry::glintLayer));
        if (cap < enchEntries.size()) enchEntries = enchEntries.subList(0, cap);

        TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks();

        for (CITEntry entry : enchEntries) {
            if (entry.texture() == null) continue;
            datarium$renderCITGlint(entry, textureMap, x, y);
        }
    }

    @Unique
    private void datarium$renderCITGlint(CITEntry entry, TextureMap textureMap, int x, int y) {
        String spritePath = entry.texture().getPath();
        if (spritePath.endsWith(".png")) spritePath = spritePath.substring(0, spritePath.length() - 4);
        if (spritePath.startsWith("textures/")) spritePath = spritePath.substring("textures/".length());
        String spriteName = entry.texture().getNamespace() + ":" + spritePath;
        TextureAtlasSprite sprite = textureMap.getAtlasSprite(spriteName);
        if (sprite == null) return;

        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(514);
        GlStateManager.disableLighting();
        GlStateManager.blendFunc(datarium$resolveBlendSrc(entry.glintBlend()),
                datarium$resolveBlendDst(entry.glintBlend()));
        GlStateManager.enableBlend();

        float alphaMult = GlobalCITProperties.getMethod().equals("average") ? GlobalCITProperties.getFade() : 1.0f;
        if (!GlobalCITProperties.isUseGlint() || !entry.glintUseGlint()) {
            GlStateManager.color(entry.glintR(), entry.glintG(), entry.glintB(), entry.glintA() * alphaMult);
        }

        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        float u0 = sprite.getMinU();
        float v0 = sprite.getMinV();
        float u1 = sprite.getMaxU();
        float v1 = sprite.getMaxV();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(7, DefaultVertexFormats.POSITION_TEX);
        buf.pos(x,      y + 16, 0).tex(u0, v1).endVertex();
        buf.pos(x + 16, y + 16, 0).tex(u1, v1).endVertex();
        buf.pos(x + 16, y,      0).tex(u1, v0).endVertex();
        buf.pos(x,      y,      0).tex(u0, v0).endVertex();
        tess.draw();

        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(515);
        GlStateManager.depthMask(true);
    }

    @Unique
    private int datarium$resolveBlendSrc(String blend) {
        return switch (blend.toLowerCase()) {
            case "replace", "screen", "dodge" -> GL11.GL_ONE;
            case "glint" -> GL11.GL_SRC_COLOR;
            case "alpha" -> GL11.GL_SRC_ALPHA;
            case "subtract" -> GL11.GL_ONE_MINUS_DST_COLOR;
            case "multiply", "overlay" -> GL11.GL_DST_COLOR;
            case "burn" -> GL11.GL_ZERO;
            default -> GL11.GL_SRC_ALPHA; // "add"
        };
    }

    @Unique
    private int datarium$resolveBlendDst(String blend) {
        return switch (blend.toLowerCase()) {
            case "replace", "subtract" -> GL11.GL_ZERO;
            case "glint" -> GL11.GL_ONE;
            case "alpha", "multiply" -> GL11.GL_ONE_MINUS_SRC_ALPHA;
            case "dodge" -> GL11.GL_ONE;
            case "burn", "screen" -> GL11.GL_ONE_MINUS_SRC_COLOR;
            case "overlay" -> GL11.GL_SRC_COLOR;
            default -> GL11.GL_ONE; // "add"
        };
    }
}