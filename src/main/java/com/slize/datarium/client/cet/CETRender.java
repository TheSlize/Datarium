package com.slize.datarium.client.cet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Deque;

public final class CETRender {
    private static final ResourceLocation GLINT = new ResourceLocation("textures/misc/enchanted_item_glint.png");
    private static final ResourceLocation BLANK = new ResourceLocation("datarium", "cet/blank.png");
    private static final Deque<Boolean> TRANSLUCENT_STACK = new ArrayDeque<>();
    private static boolean blankRegistered;
    private static boolean warnedUnsupportedLayer;

    private CETRender() {}

    public static boolean isEnabled() {
        return CETConfig.enableCustomTextures || CETConfig.enableEmissiveTextures || CETConfig.enableEnchantedTextures
                || CETConfig.enableBlinking || CETConfig.skinFeaturesEnabled;
    }

    public static void beginEntity(Entity entity) {
        CETState.mount(isEnabled() ? CETSubject.of(entity) : null);
        beginLayerOverride();
    }

    public static void beginTile(TileEntity tile) {
        CETState.mount(isEnabled() ? CETSubject.of(tile) : null);
        beginLayerOverride();
    }

    public static void beginVirtual(@Nullable CETSubject subject) {
        CETState.mount(isEnabled() ? subject : null);
        TRANSLUCENT_STACK.push(false);
    }

    public static void end() {
        Boolean translucent = TRANSLUCENT_STACK.poll();
        if (translucent != null && translucent) endTranslucent();
        CETState.unmount();
    }

    private static void beginLayerOverride() {
        CETManager.RenderLayerOverride override = CETManager.layerOverride(CETState.subject());
        boolean translucent = false;
        if (override == CETManager.RenderLayerOverride.TRANSLUCENT || override == CETManager.RenderLayerOverride.TRANSLUCENT_CULL) {
            beginTranslucent();
            translucent = true;
        } else if (override != null && !warnedUnsupportedLayer) {
            warnedUnsupportedLayer = true;
            CETUtils.warn("entityRenderLayerOverride=" + override.name().toLowerCase() + " is not supported on 1.12.2, ignoring");
        }
        TRANSLUCENT_STACK.push(translucent);
    }

    public static void beginTranslucent() {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.003921569F);
    }

    public static void endTranslucent() {
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);
        GlStateManager.disableBlend();
    }

    public static ResourceLocation onBindTexture(ResourceLocation location) {
        CETSubject subject = CETState.subject();
        if (subject == null || location == null) return location;
        if (CETState.overlayPhase != CETState.OverlayPhase.NONE) return overlayTextureFor(location, subject);
        if (!CETState.isModifyAllowed() || (!CETConfig.enableArmorAndTrims && location.getPath().startsWith("textures/models/armor/"))) {
            CETState.onTextureResolved(null);
            return location;
        }
        if (!CETConfig.canDoCustomTextures()) {
            CETState.onTextureResolved(CETManager.getNoVariation(location));
            return location;
        }
        CETTexture texture = CETManager.getVariant(location, subject);
        CETState.onTextureResolved(texture);
        return texture.getTextureIdentifier(subject);
    }

    private static ResourceLocation overlayTextureFor(ResourceLocation location, CETSubject subject) {
        if (CETState.overlayPhase == CETState.OverlayPhase.GLINT) return GLINT;
        CETTexture texture = CETConfig.canDoCustomTextures() ? CETManager.getVariant(location, subject) : CETManager.getNoVariation(location);
        texture.getTextureIdentifier(subject);
        ResourceLocation overlay = CETState.overlayPhase == CETState.OverlayPhase.EMISSIVE
                ? texture.getEmissiveIdentifierOfCurrentState() : texture.getEnchantIdentifierOfCurrentState();
        return overlay != null ? overlay : blank();
    }

    private static ResourceLocation blank() {
        if (!blankRegistered) {
            CETUtils.register(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), BLANK);
            blankRegistered = true;
        }
        return BLANK;
    }

    public static boolean isTracking() {
        return CETState.isActive() && CETState.overlayPhase == CETState.OverlayPhase.NONE;
    }

    public static void renderTopLevel(Runnable render) {
        int depth = CETState.modelPartDepth;
        CETTexture texture = depth == 0 ? CETState.currentTexture : null;
        CETState.modelPartDepth = depth + 1;
        try {
            render.run();
        } finally {
            CETState.modelPartDepth = depth;
        }
        if (depth == 0 && texture != null && texture.hasOverlays()) renderOverlays(texture, render);
    }

    public static void renderOverlays(CETTexture texture, Runnable render) {
        ResourceLocation emissive = CETConfig.canDoEmissiveTextures() ? texture.getEmissiveIdentifierOfCurrentState() : null;
        ResourceLocation enchant = CETConfig.enableEnchantedTextures ? texture.getEnchantIdentifierOfCurrentState() : null;
        if (emissive == null && enchant == null) return;
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (emissive != null) renderEmissive(emissive, render);
        if (enchant != null) renderEnchanted(enchant, render);
        GlStateManager.bindTexture(previous);
        CETState.currentTexture = texture;
    }

    private static void bindRaw(ResourceLocation location) {
        CETState.pushModify(false);
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        CETState.popModify();
    }

    public static void renderEmissive(ResourceLocation emissive, Runnable render) {
        CETSubject subject = CETState.subject();
        boolean bright = CETConfig.emissiveRenderMode == CETConfig.EmissiveRenderMode.BRIGHT && (subject == null || subject.canRenderBright());
        float lastX = OpenGlHelper.lastBrightnessX;
        float lastY = OpenGlHelper.lastBrightnessY;
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);

        bindRaw(emissive);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        if (bright) GlStateManager.disableLighting();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);

        CETState.overlayPhase = CETState.OverlayPhase.EMISSIVE;
        try {
            render.run();
        } finally {
            CETState.overlayPhase = CETState.OverlayPhase.NONE;
        }

        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        if (bright && lighting) GlStateManager.enableLighting();
        if (!blend) GlStateManager.disableBlend();
    }

    public static void renderEnchanted(ResourceLocation enchant, Runnable render) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);

        bindRaw(enchant);
        CETState.overlayPhase = CETState.OverlayPhase.ENCHANT;
        try {
            render.run();
        } finally {
            CETState.overlayPhase = CETState.OverlayPhase.NONE;
        }

        bindRaw(GLINT);
        float time = (float) (Minecraft.getSystemTime() % 1000000L) / 50.0F;
        GlStateManager.enableBlend();
        GlStateManager.depthFunc(GL11.GL_EQUAL);
        GlStateManager.depthMask(false);
        GlStateManager.color(0.5F, 0.5F, 0.5F, 1.0F);
        CETState.overlayPhase = CETState.OverlayPhase.GLINT;
        try {
            for (int i = 0; i < 2; ++i) {
                GlStateManager.disableLighting();
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_COLOR, GlStateManager.DestFactor.ONE);
                GlStateManager.color(0.38F, 0.19F, 0.608F, 1.0F);
                GlStateManager.matrixMode(GL11.GL_TEXTURE);
                GlStateManager.loadIdentity();
                GlStateManager.scale(0.33333334F, 0.33333334F, 0.33333334F);
                GlStateManager.rotate(30.0F - (float) i * 60.0F, 0.0F, 0.0F, 1.0F);
                GlStateManager.translate(0.0F, time * (0.001F + (float) i * 0.003F) * 20.0F, 0.0F);
                GlStateManager.matrixMode(GL11.GL_MODELVIEW);
                render.run();
            }
        } finally {
            CETState.overlayPhase = CETState.OverlayPhase.NONE;
            GlStateManager.matrixMode(GL11.GL_TEXTURE);
            GlStateManager.loadIdentity();
            GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        GlStateManager.depthMask(true);
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        if (lighting) GlStateManager.enableLighting();
        if (!blend) GlStateManager.disableBlend();
    }

    public static int entityLight(Entity entity, int original) {
        if (!CETManager.hasLightOverrides()) return original;
        Integer override = CETManager.lightOverride(CETSubject.keyOf(entity));
        if (override == null) return original;
        BlockPos pos = new BlockPos(entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        World world = entity.world;
        int sky = world.getLightFor(EnumSkyBlock.SKY, pos);
        int block = entity.isBurning() ? 15 : world.getLightFor(EnumSkyBlock.BLOCK, pos);
        return Math.max(sky, block) << 20 | override << 4;
    }

    public static int tileLight(TileEntity tile, int original) {
        if (!CETManager.hasLightOverrides() || !tile.hasWorld()) return original;
        ResourceLocation id = TileEntity.getKey(tile.getClass());
        Integer override = id == null ? null : CETManager.lightOverride(id.toString());
        if (override == null) return original;
        World world = tile.getWorld();
        int sky = world.getLightFor(EnumSkyBlock.SKY, tile.getPos());
        int block = world.getLightFor(EnumSkyBlock.BLOCK, tile.getPos());
        return Math.max(sky, block) << 20 | override << 4;
    }
}
