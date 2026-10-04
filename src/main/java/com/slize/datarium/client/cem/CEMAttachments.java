package com.slize.datarium.client.cem;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.FloatBuffer;

/**
 * JPM "attachments": moves the current matrix onto the part that declares the attachment, in the same
 * frame OptiFine/EMF use for handheld items, then offsets it by the declared [x, y, z] (pixels).
 */
public final class CEMAttachments {
    public static final String LEFT_HAND = "left_handheld_item";
    public static final String RIGHT_HAND = "right_handheld_item";
    public static final String HEAD = "head_item";
    public static final String ENDERMAN = "enderman_block";
    public static final String WITCH = "witch_item";
    public static final String PARROT_LEFT = "parrot_left";
    public static final String PARROT_RIGHT = "parrot_right";

    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private CEMAttachments() {}

    @Nullable
    public static CEMModelRenderer find(String type) {
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        return wrapper != null ? wrapper.findAttachment(type) : null;
    }

    public static boolean apply(String type) {
        return apply(type, 0.0F, 0.0F, 0.0F);
    }

    public static boolean applyParrotAuto(boolean left) {
        String modelName = CEMRenderHooks.getActiveModelName();
        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        if (modelName == null || wrapper == null || !CEMManager.isPlayerModel(modelName)) return false;
        if (wrapper.findAttachment(PARROT_LEFT) != null || wrapper.findAttachment(PARROT_RIGHT) != null) return false;
        CEMModelRenderer arm = wrapper.findRootByPart(left ? "left_arm" : "right_arm");
        if (arm == null) return false;

        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        arm.applyAttachmentTransform(0.0625F);
        GlStateManager.translate(left ? 0.4F : -0.4F, -1.5F, 0.0F);
        MATRIX.clear();
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        GlStateManager.popMatrix();

        GlStateManager.translate(MATRIX.get(12), -1.5F + MATRIX.get(13), MATRIX.get(14));
        return true;
    }

    /** @param baseX extra translation in blocks, added on top of the attachment offset */
    public static boolean apply(String type, float baseX, float baseY, float baseZ) {
        CEMModelRenderer part = find(type);
        if (part == null) return false;
        float[] offset = part.getCemPart().attachments.get(type);
        String invert = part.getCemPart().invertAxis;
        part.applyAttachmentTransform(0.0625F);
        GlStateManager.translate(
                baseX + (invert.contains("x") ? -offset[0] : offset[0]) * 0.0625F,
                baseY + (invert.contains("y") ? -offset[1] : offset[1]) * 0.0625F,
                baseZ + (invert.contains("z") ? -offset[2] : offset[2]) * 0.0625F);
        return true;
    }
}
