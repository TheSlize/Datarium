package com.slize.datarium.client.cit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.*;
import java.util.function.IntConsumer;

public final class CITGlintRenderer {

    private record Layer(CITEntry entry, float intensity) {
    }

    private static final Map<ResourceLocation, Integer> textureWidths = new HashMap<>();
    private static ItemStack itemStack = ItemStack.EMPTY;
    private static ItemStack armorStack = ItemStack.EMPTY;

    private CITGlintRenderer() {
    }

    public static void setItemStack(ItemStack stack) {
        itemStack = stack;
    }

    public static void setArmorStack(ItemStack stack) {
        armorStack = stack;
    }

    public static void clearCache() {
        textureWidths.clear();
    }

    public static boolean renderItemGlint(IntConsumer renderModel) {
        List<Layer> layers = collectLayers(itemStack);
        if (layers.isEmpty()) return !GlobalCITProperties.isUseGlint();

        Minecraft mc = Minecraft.getMinecraft();
        TextureManager textureManager = mc.getTextureManager();
        long time = Minecraft.getSystemTime();
        boolean vanilla = false;

        GlStateManager.depthMask(false);
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.disableLighting();
        GlStateManager.matrixMode(GL11.GL_TEXTURE);

        for (Layer layer : layers) {
            CITEntry entry = layer.entry();
            ResourceLocation texture = entry.texture();
            textureManager.bindTexture(texture);
            ITextureObject object = textureManager.getTexture(texture);
            if (entry.glintBlur() && object != null) object.setBlurMipmap(true, false);
            float width = textureWidth(texture);
            int color = applyBlend(entry, layer.intensity());

            GlStateManager.pushMatrix();
            GlStateManager.scale(width / 2f, width / 2f, width / 2f);
            GlStateManager.translate(entry.speed() * (time % 3000L) / 3000f / 8f, 0f, 0f);
            GlStateManager.rotate(entry.rotation(), 0f, 0f, 1f);
            renderModel.accept(color);
            GlStateManager.popMatrix();

            if (entry.glintBlur() && object != null) object.restoreLastBlurMipmap();
            vanilla |= entry.glintUseGlint();
        }

        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.enableLighting();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        return !(vanilla && GlobalCITProperties.isUseGlint());
    }

    public static boolean renderArmorGlint(EntityLivingBase entity, ModelBase model, float limbSwing, float limbSwingAmount,
                                           float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        List<Layer> layers = collectLayers(armorStack);
        if (layers.isEmpty()) return !GlobalCITProperties.isUseGlint();

        Minecraft mc = Minecraft.getMinecraft();
        TextureManager textureManager = mc.getTextureManager();
        long time = Minecraft.getSystemTime();
        boolean vanilla = false;

        mc.entityRenderer.setupFogColor(true);
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);

        for (Layer layer : layers) {
            CITEntry entry = layer.entry();
            ResourceLocation texture = entry.texture();
            textureManager.bindTexture(texture);
            ITextureObject object = textureManager.getTexture(texture);
            if (entry.glintBlur() && object != null) object.setBlurMipmap(true, false);
            float width = textureWidth(texture);
            applyBlend(entry, layer.intensity());
            GlStateManager.disableLighting();

            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.rotate(entry.rotation(), 0f, 0f, 1f);
            float texScale = width / 8f;
            GlStateManager.scale(texScale, texScale / 2f, texScale);
            GlStateManager.translate(0f, entry.speed() * (time % 3000L) / 3000f / 8f, 0f);
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);

            model.render(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);

            if (entry.glintBlur() && object != null) object.restoreLastBlurMipmap();
            vanilla |= entry.glintUseGlint();
        }

        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.enableLighting();
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.disableBlend();
        mc.entityRenderer.setupFogColor(false);

        return !(vanilla && GlobalCITProperties.isUseGlint());
    }

    private static List<Layer> collectLayers(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<CITEntry> matches = CITManager.getMatchesOfType(stack, CITEntry.CITType.ENCHANTMENT);
        if (matches.isEmpty()) return List.of();

        TreeMap<Integer, CITEntry> byLayer = new TreeMap<>();
        for (CITEntry entry : matches) {
            if (entry.texture() != null) byLayer.putIfAbsent(entry.layer(), entry);
        }
        List<CITEntry> list = new ArrayList<>(byLayer.values());
        int cap = GlobalCITProperties.getCap();
        if (cap <= 0) return List.of();
        if (cap < list.size()) list = list.subList(list.size() - cap, list.size());

        float[] intensities = new float[list.size()];
        Arrays.fill(intensities, 1f);
        if (GlobalCITProperties.hasMethod() && list.size() > 1) {
            switch (GlobalCITProperties.getMethod()) {
                case "cycle" -> cycleIntensities(list, intensities);
                case "layered" -> levelIntensities(stack, list, intensities, false);
                default -> levelIntensities(stack, list, intensities, true);
            }
        }

        List<Layer> layers = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            if (intensities[i] > 0f) layers.add(new Layer(list.get(i), intensities[i]));
        }
        return layers;
    }

    private static void levelIntensities(ItemStack stack, List<CITEntry> list, float[] out, boolean average) {
        int[] levels = new int[list.size()];
        int total = 0;
        int max = 0;
        for (int i = 0; i < list.size(); i++) {
            levels[i] = Math.max(1, list.get(i).enchantmentLevel(stack));
            total += levels[i];
            max = Math.max(max, levels[i]);
        }
        int divisor = average ? total : max;
        for (int i = 0; i < list.size(); i++) {
            out[i] = divisor > 0 ? (float) levels[i] / divisor : 1f;
        }
    }

    private static void cycleIntensities(List<CITEntry> list, float[] out) {
        float[] durations = new float[list.size()];
        float total = 0f;
        for (int i = 0; i < list.size(); i++) {
            durations[i] = Math.max(0.05f, list.get(i).duration());
            total += durations[i];
        }
        long period = Math.max(1L, (long) (total * 1000f));
        float t = (Minecraft.getSystemTime() % period) / 1000f;
        Arrays.fill(out, 0f);
        float start = 0f;
        for (int i = 0; i < list.size(); i++) {
            float end = start + durations[i];
            if (t < end || i == list.size() - 1) {
                float fade = Math.min(GlobalCITProperties.getFade(), durations[i]);
                float local = t - start;
                if (fade > 0f && local > durations[i] - fade) {
                    float f = (local - (durations[i] - fade)) / fade;
                    out[i] = 1f - f;
                    out[(i + 1) % list.size()] = f;
                } else {
                    out[i] = 1f;
                }
                return;
            }
            start = end;
        }
    }

    private static int applyBlend(CITEntry entry, float brightness) {
        float r = 1f, g = 1f, b = 1f, a = 1f;
        switch (entry.blend()) {
            case ALPHA -> {
                blend(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                a = brightness;
            }
            case SUBTRACT -> {
                blend(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ZERO);
                r = g = b = brightness;
            }
            case MULTIPLY -> {
                blend(GL11.GL_DST_COLOR, GL11.GL_ONE_MINUS_SRC_ALPHA);
                r = g = b = a = brightness;
            }
            case DODGE -> {
                blend(GL11.GL_ONE, GL11.GL_ONE);
                r = g = b = brightness;
            }
            case BURN -> {
                blend(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR);
                r = g = b = brightness;
            }
            case SCREEN -> {
                blend(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_COLOR);
                r = g = b = brightness;
            }
            case OVERLAY -> {
                blend(GL11.GL_DST_COLOR, GL11.GL_SRC_COLOR);
                r = g = b = brightness;
            }
            case REPLACE -> {
                GlStateManager.enableAlpha();
                GlStateManager.disableBlend();
                a = brightness;
            }
            case GLINT -> {
                blend(GL11.GL_SRC_COLOR, GL11.GL_ONE);
                r = g = b = brightness;
            }
            default -> {
                blend(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
                a = brightness;
            }
        }
        r = Math.clamp(r * entry.glintR(), 0f, 1f);
        g = Math.clamp(g * entry.glintG(), 0f, 1f);
        b = Math.clamp(b * entry.glintB(), 0f, 1f);
        a = Math.clamp(a * entry.glintA(), 0f, 1f);
        GlStateManager.color(r, g, b, a);
        return ((int) (a * 255f) << 24) | ((int) (r * 255f) << 16) | ((int) (g * 255f) << 8) | (int) (b * 255f);
    }

    private static void blend(int src, int dst) {
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(src, dst);
    }

    private static float textureWidth(ResourceLocation texture) {
        Integer cached = textureWidths.get(texture);
        if (cached == null) {
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            cached = width > 0 ? width : 16;
            textureWidths.put(texture, cached);
        }
        return cached;
    }
}
