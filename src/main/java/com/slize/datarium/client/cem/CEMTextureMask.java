package com.slize.datarium.client.cem;

import com.slize.datarium.mixin.accessors.AccessorGlStateManager;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;

/** Which texels of a bound texture have any alpha, so boxes mapped onto fully transparent areas can be dropped. */
public final class CEMTextureMask {
    private static final int MAX_PIXELS = 1 << 24;
    private static final int MAX_REBUILDS = 3;
    private static final CEMTextureMask OPAQUE = new CEMTextureMask(0, 0, new long[0]);

    private static final Int2ObjectOpenHashMap<CEMTextureMask> MASKS = new Int2ObjectOpenHashMap<>();
    private static final Int2IntOpenHashMap REBUILDS = new Int2IntOpenHashMap();

    private static ByteBuffer readBuffer = BufferUtils.createByteBuffer(1 << 16);
    private static int boundTexture;
    private static int lastTexture = -1;
    @Nullable private static CEMTextureMask lastMask;

    private final int width;
    private final int height;
    private final long[] bits;

    private CEMTextureMask(int width, int height, long[] bits) {
        this.width = width;
        this.height = height;
        this.bits = bits;
    }

    public static void onBind(int texture) {
        boundTexture = texture;
    }

    public static void onUpload() {
        if (MASKS.isEmpty()) return;
        int texture = boundTexture;
        CEMTextureMask mask = MASKS.get(texture);
        if (mask == null || (mask == OPAQUE && REBUILDS.get(texture) >= MAX_REBUILDS)) return;
        MASKS.remove(texture);
        REBUILDS.addTo(texture, 1);
        lastTexture = -1;
    }

    public static void onDelete(int texture) {
        if (MASKS.isEmpty()) return;
        MASKS.remove(texture);
        REBUILDS.remove(texture);
        lastTexture = -1;
    }

    /** @return the mask of a texture that already has one, null if it is unknown or has nothing to cull. */
    @Nullable
    public static CEMTextureMask peek(int texture) {
        CEMTextureMask mask = MASKS.get(texture);
        return mask == OPAQUE ? null : mask;
    }

    public static void invalidate() {
        MASKS.clear();
        REBUILDS.clear();
        lastTexture = -1;
    }

    /** @return null when nothing can be culled for the texture bound right now. */
    @Nullable
    public static CEMTextureMask current() {
        int texture = boundTexture;
        if (texture == lastTexture) return lastMask;
        if (texture <= 0 || AccessorGlStateManager.datarium$getActiveTextureUnit() != 0) return null;

        CEMTextureMask mask = MASKS.get(texture);
        if (mask == null) {
            if (GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D) != texture) return null;
            mask = REBUILDS.get(texture) >= MAX_REBUILDS ? OPAQUE : read();
            MASKS.put(texture, mask);
        }
        lastTexture = texture;
        lastMask = mask == OPAQUE ? null : mask;
        return lastMask;
    }

    private static CEMTextureMask read() {
        int width = GlStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int height = GlStateManager.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) return OPAQUE;

        int pixels = width * height;
        if (readBuffer.capacity() < pixels) readBuffer = BufferUtils.createByteBuffer(pixels);
        readBuffer.clear();

        int alignment = GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_ALPHA, GL11.GL_UNSIGNED_BYTE, readBuffer);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, alignment);

        long[] bits = new long[(pixels + 63) >> 6];
        boolean anyTransparent = false;
        for (int i = 0; i < pixels; i++) {
            if (readBuffer.get(i) != 0) bits[i >> 6] |= 1L << (i & 63);
            else anyTransparent = true;
        }
        return anyTransparent ? new CEMTextureMask(width, height, bits) : OPAQUE;
    }

    /** uv = { u1, v1, u2, v2 } normalised, in any order. */
    public boolean isVisible(float[] uv) {
        float minU = Math.min(uv[0], uv[2]);
        float maxU = Math.max(uv[0], uv[2]);
        float minV = Math.min(uv[1], uv[3]);
        float maxV = Math.max(uv[1], uv[3]);
        if (!(minU >= 0.0F && maxU <= 1.0F && minV >= 0.0F && maxV <= 1.0F)) return true;

        int x0 = Math.max(0, (int) Math.floor(minU * width) - 1);
        int x1 = Math.min(width, (int) Math.ceil(maxU * width) + 1);
        int y0 = Math.max(0, (int) Math.floor(minV * height) - 1);
        int y1 = Math.min(height, (int) Math.ceil(maxV * height) + 1);
        for (int y = y0; y < y1; y++) {
            int row = y * width;
            for (int i = row + x0, end = row + x1; i < end; i++) {
                if ((bits[i >> 6] & (1L << (i & 63))) != 0) return true;
            }
        }
        return false;
    }
}
