package com.slize.datarium.client.cet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public final class CETMooshroom {
    private static final ResourceLocation RED = new ResourceLocation("textures/entity/cow/red_mushroom.png");
    private static Boolean redExists;

    private CETMooshroom() {}

    public static void reset() {
        redExists = null;
    }

    public static boolean hasCustomRed() {
        if (!CETConfig.enableCustomTextures) return false;
        if (redExists == null) redExists = CETUtils.exists(RED);
        return redExists;
    }

    public static void renderRed() {
        Minecraft.getMinecraft().getTextureManager().bindTexture(RED);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        GlStateManager.disableCull();
        if (CETRender.isTracking()) CETRender.renderTopLevel(CETMooshroom::drawCross);
        else drawCross();
        if (cull) GlStateManager.enableCull();
        Minecraft.getMinecraft().getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
    }

    private static void drawCross() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);
        buffer.pos(0, 0, 0).tex(0, 1).normal(0.7071F, 0, -0.7071F).endVertex();
        buffer.pos(1, 0, 1).tex(1, 1).normal(0.7071F, 0, -0.7071F).endVertex();
        buffer.pos(1, 1, 1).tex(1, 0).normal(0.7071F, 0, -0.7071F).endVertex();
        buffer.pos(0, 1, 0).tex(0, 0).normal(0.7071F, 0, -0.7071F).endVertex();
        buffer.pos(1, 0, 0).tex(0, 1).normal(0.7071F, 0, 0.7071F).endVertex();
        buffer.pos(0, 0, 1).tex(1, 1).normal(0.7071F, 0, 0.7071F).endVertex();
        buffer.pos(0, 1, 1).tex(1, 0).normal(0.7071F, 0, 0.7071F).endVertex();
        buffer.pos(1, 1, 0).tex(0, 0).normal(0.7071F, 0, 0.7071F).endVertex();
        tessellator.draw();
    }
}
