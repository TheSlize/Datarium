package com.slize.datarium.client.cem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

public class CEMModelRenderer extends ModelRenderer {
    private final CEMModelPart cemPart;
    private final int textureWidth;
    private final int textureHeight;
    private final List<CEMModelRenderer> cemChildren;
    private CEMPartTransform transform;

    // Absolute pivot point in Global Model Space (Minecraft Y-down)
    private final float absPivotX;
    private final float absPivotY;
    private final float absPivotZ;

    // Relative offset from parent pivot (This is what Vanilla ModelRenderer uses for rotationPoint)
    private final float defaultOffsetX;
    private final float defaultOffsetY;
    private final float defaultOffsetZ;
    private float defaultRotateX, defaultRotateY, defaultRotateZ;

    // Buffer to save texture state without GC overhead
    private static final IntBuffer TEXTURE_BUF = BufferUtils.createIntBuffer(16);

    public CEMModelRenderer(ModelBase model, CEMModelPart cemPart, int texWidth, int texHeight, @Nullable CEMModelRenderer parent) {
        super(model);
        this.cemPart = cemPart;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
        this.cemChildren = new ArrayList<>();
        this.transform = null;

        // Process invertAxis for the translation values
        boolean invX = cemPart.invertAxis.contains("x");
        boolean invY = cemPart.invertAxis.contains("y");
        boolean invZ = cemPart.invertAxis.contains("z");

        // Apply invertAxis to the translation values first
        float invXTrans = invX ? -cemPart.translate[0] : cemPart.translate[0];
        float invYTrans = invY ? -cemPart.translate[1] : cemPart.translate[1];
        float invZTrans = invZ ? -cemPart.translate[2] : cemPart.translate[2];

        // Calculate absolute pivot point based on parent
        if (parent == null) {
            // For top-level parts, translation is absolute
            this.absPivotX = invXTrans;
            this.absPivotY = invYTrans;
            this.absPivotZ = invZTrans;
        } else {
            // For child parts, translation is relative to parent
            this.absPivotX = parent.absPivotX + invXTrans;
            this.absPivotY = parent.absPivotY + invYTrans;
            this.absPivotZ = parent.absPivotZ + invZTrans;
        }

        // Calculate relative offset for ModelRenderer translation
        this.defaultOffsetX = this.absPivotX;
        this.defaultOffsetY = this.absPivotY;
        this.defaultOffsetZ = this.absPivotZ;
        /*if (parent != null) {
            this.defaultOffsetX = this.absPivotX - parent.absPivotX;
            this.defaultOffsetY = this.absPivotY - parent.absPivotY;
            this.defaultOffsetZ = this.absPivotZ - parent.absPivotZ;
        } else {
            this.defaultOffsetX = this.absPivotX;
            this.defaultOffsetY = this.absPivotY;
            this.defaultOffsetZ = this.absPivotZ;
        }*/

        // Initial Rotations (Standard ModelRenderer behavior)
        this.defaultRotateX = (float) Math.toRadians(cemPart.rotate[0]);
        this.defaultRotateY = (float) Math.toRadians(cemPart.rotate[1]);
        this.defaultRotateZ = (float) Math.toRadians(cemPart.rotate[2]);

        // Apply invertAxis to rotations as well
        if (invX) this.defaultRotateX = -this.defaultRotateX;
        if (invY) this.defaultRotateY = -this.defaultRotateY;
        if (invZ) this.defaultRotateZ = -this.defaultRotateZ;

        // Set initial values for Vanilla ModelRenderer fields
        this.rotationPointX = defaultOffsetX;
        this.rotationPointY = defaultOffsetY;
        this.rotationPointZ = defaultOffsetZ;
        this.rotateAngleX = defaultRotateX;
        this.rotateAngleY = defaultRotateY;
        this.rotateAngleZ = defaultRotateZ;

        for (CEMModelPart sub : cemPart.submodels) {
            CEMModelRenderer childRenderer = new CEMModelRenderer(model, sub, texWidth, texHeight, this);
            cemChildren.add(childRenderer);
        }
    }

    public void setTransform(@Nullable CEMPartTransform transform) {
        this.transform = transform;
    }

    public CEMModelPart getCemPart() {
        return cemPart;
    }

    public boolean isAttached() {
        return cemPart.attach;
    }

    public List<CEMModelRenderer> getCemChildren() {
        return cemChildren;
    }

    @Override
    public void render(float scale) {
        renderWithVanilla(scale);
    }

    public void renderWithVanilla(float scale) {
        if (!this.showModel) return;
        if (transform != null && transform.hasVisible && !transform.visible) return;

        GlStateManager.pushMatrix();

        applyTransforms(scale);

        // --- Render Geometry ---
        // Boxes must be rendered relative to the CURRENT local origin (which is at absPivot)
        boolean invX = cemPart.invertAxis.contains("x");
        boolean invY = cemPart.invertAxis.contains("y");
        boolean invZ = cemPart.invertAxis.contains("z");
        renderBoxes(scale, invX, invY, invZ);

        // --- Render Children ---
        for (CEMModelRenderer child : cemChildren) {
            child.renderWithVanilla(scale);
        }

        GlStateManager.popMatrix();
    }

    /**
     * Separate render pass for debug info.
     */
    public void renderDebugOnly(float scale) {
        GlStateManager.pushMatrix();

        applyTransforms(scale);

        // Render debug using absolute pivot info
        renderDebugInternals(scale);

        // Recurse
        for (CEMModelRenderer child : cemChildren) {
            child.renderDebugOnly(scale);
        }

        GlStateManager.popMatrix();
    }

    private void applyTransforms(float scale) {
        // --- Translation ---
        // Start with default relative offset (Calculated Pivot)
        float tx = defaultOffsetX;
        float ty = defaultOffsetY;
        float tz = defaultOffsetZ;

        // Add Animation Transforms (Offsets)
        // Optifine animations for translation are offsets added to the base pivot
        if (transform != null) {
            if (transform.hasTranslateX) tx = transform.translateX;
            if (transform.hasTranslateY) ty = transform.translateY;
            if (transform.hasTranslateZ) tz = transform.translateZ;
        }

        GlStateManager.translate(tx * scale, ty * scale, tz * scale);

        // --- Rotation ---
        // Rotations are typically replacements
        float rx = defaultRotateX;
        float ry = defaultRotateY;
        float rz = defaultRotateZ;

        if (transform != null) {
            if (transform.hasRotateX) rx = transform.rotateX;
            if (transform.hasRotateY) ry = transform.rotateY;
            if (transform.hasRotateZ) rz = transform.rotateZ;
        }

        if (rz != 0.0F) GlStateManager.rotate((float) Math.toDegrees(rz), 0.0F, 0.0F, 1.0F);
        if (ry != 0.0F) GlStateManager.rotate((float) Math.toDegrees(ry), 0.0F, 1.0F, 0.0F);
        if (rx != 0.0F) GlStateManager.rotate((float) Math.toDegrees(rx), 1.0F, 0.0F, 0.0F);

        // --- Scaling ---
        float sx = 1.0f, sy = 1.0f, sz = 1.0f;
        if (cemPart.scale != null) {
            sx = cemPart.scale[0];
            sy = cemPart.scale[1];
            sz = cemPart.scale[2];
        }
        if (transform != null) {
            if (transform.hasScaleX) sx *= transform.scaleX;
            if (transform.hasScaleY) sy *= transform.scaleY;
            if (transform.hasScaleZ) sz *= transform.scaleZ;
        }

        if (sx != 1.0f || sy != 1.0f || sz != 1.0f) {
            GlStateManager.scale(sx, sy, sz);
        }
    }

    private void renderDebugInternals(float scale) {
        String partName = cemPart.id != null ? cemPart.id : cemPart.part;
        boolean isSelected = CEMDebugSystem.isSelected(partName);

        TEXTURE_BUF.clear();
        GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D, TEXTURE_BUF);
        int originalTexture = TEXTURE_BUF.get(0);

        GlStateManager.pushMatrix();

        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableDepth();
        GL11.glLineWidth(isSelected ? 3.0F : 1.0F);

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);

        float axisLen = isSelected ? 0.4f : 0.1f;
        b.pos(0, 0, 0).color(1f, 0f, 0f, 1f).endVertex();
        b.pos(axisLen, 0, 0).color(1f, 0f, 0f, 1f).endVertex();
        b.pos(0, 0, 0).color(0f, 1f, 0f, 1f).endVertex();
        b.pos(0, axisLen, 0).color(0f, 1f, 0f, 1f).endVertex();
        b.pos(0, 0, 0).color(0f, 0f, 1f, 1f).endVertex();
        b.pos(0, 0, axisLen).color(0f, 0f, 1f, 1f).endVertex();

        // Draw wireframe for boxes when selected
        if (isSelected) {
            boolean invX = cemPart.invertAxis.contains("x");
            boolean invY = cemPart.invertAxis.contains("y");
            boolean invZ = cemPart.invertAxis.contains("z");

            for (CEMBox box : cemPart.boxes) {
                float bx = box.coordinates[0];
                float by = box.coordinates[1];
                float bz = box.coordinates[2];
                float bw = box.coordinates[3];
                float bh = box.coordinates[4];
                float bd = box.coordinates[5];
                float inflate = box.sizeAdd;

                if (invX) bx = -(bx + bw);
                if (invY) by = -(by + bh);
                if (invZ) bz = -(bz + bd);

                // Local = Abs - AbsPivot
                float x1 = (bx - inflate + absPivotX) * scale;
                float y1 = (by - inflate + absPivotY) * scale;
                float z1 = (bz - inflate + absPivotZ) * scale;
                float x2 = (bx + bw + inflate + absPivotX) * scale;
                float y2 = (by + bh + inflate + absPivotY) * scale;
                float z2 = (bz + bd + inflate + absPivotZ) * scale;

                drawBoxWireframe(b, x1, y1, z1, x2, y2, z2);
            }
        }

        // Bones to children
        for (CEMModelRenderer child : cemChildren) {
            // Child position relative to current part is simply its defaultOffset
            float cx = child.defaultOffsetX * scale;
            float cy = child.defaultOffsetY * scale;
            float cz = child.defaultOffsetZ * scale;

            b.pos(0, 0, 0).color(1f, 1f, 0f, 0.8f).endVertex();
            b.pos(cx, cy, cz).color(1f, 1f, 0f, 0.8f).endVertex();
        }
        t.draw();

        if (partName != null && !partName.isEmpty()) {
            GlStateManager.enableTexture2D();
            List<String> lines = new ArrayList<>();
            lines.add((isSelected ? ">> " : "") + partName);

            if (isSelected) {

                float dispRx = (float) Math.toRadians(cemPart.rotate[0]);
                float dispRy = (float) Math.toRadians(cemPart.rotate[1]);
                float dispRz = (float) Math.toRadians(cemPart.rotate[2]);
                float dispTx = absPivotX;
                float dispTy = absPivotY;
                float dispTz = absPivotZ;
                float dispSx = 1f, dispSy = 1f, dispSz = 1f;

                if (transform != null) {
                    if (transform.hasRotateX) dispRx = transform.rotateX;
                    if (transform.hasRotateY) dispRy = transform.rotateY;
                    if (transform.hasRotateZ) dispRz = transform.rotateZ;
                    if (transform.hasTranslateX) dispTx = transform.translateX;
                    if (transform.hasTranslateY) dispTy = transform.translateY;
                    if (transform.hasTranslateZ) dispTz = transform.translateZ;
                    if (transform.hasScaleX) dispSx = transform.scaleX;
                    if (transform.hasScaleY) dispSy = transform.scaleY;
                    if (transform.hasScaleZ) dispSz = transform.scaleZ;
                }

                lines.add(String.format("AbsPivot: %.1f, %.1f, %.1f", absPivotX, absPivotY, absPivotZ));
                lines.add(String.format("RelOffset: %.1f, %.1f, %.1f", defaultOffsetX, defaultOffsetY, defaultOffsetZ));
                lines.add(String.format("Pivot (eff): %.2f, %.2f, %.2f", dispTx, dispTy, dispTz));
                lines.add(String.format("Rot: %.1f, %.1f, %.1f", Math.toDegrees(dispRx), Math.toDegrees(dispRy), Math.toDegrees(dispRz)));
                lines.add(String.format("Scl: %.2f, %.2f, %.2f", dispSx, dispSy, dispSz));
                lines.add("Visible: " + (transform != null && transform.hasVisible ? transform.visible : "true"));
                lines.add("InvertAxis: " + (cemPart.invertAxis.isEmpty() ? "none" : cemPart.invertAxis));
                lines.add("MirrorTex: " + (cemPart.mirrorTexture.isEmpty() ? "none" : cemPart.mirrorTexture));
                lines.add("Boxes: " + cemPart.boxes.size());

                for (int i = 0; i < cemPart.boxes.size(); i++) {
                    CEMBox box = cemPart.boxes.get(i);
                    lines.add(String.format("  Box %d: pos[%.1f,%.1f,%.1f] size[%.0f,%.0f,%.0f] uv[%d,%d] inflate=%.2f",
                            i, box.coordinates[0], box.coordinates[1], box.coordinates[2],
                            box.coordinates[3], box.coordinates[4], box.coordinates[5],
                            box.textureOffset[0], box.textureOffset[1], box.sizeAdd));
                    if (box.uvNorth != null) lines.add(String.format("    uvN[%.0f,%.0f,%.0f,%.0f]", box.uvNorth[0], box.uvNorth[1], box.uvNorth[2], box.uvNorth[3]));
                }

                if (cemPart.parent != null) {
                    lines.add("Parent: " + (cemPart.parent.id != null ? cemPart.parent.id : cemPart.parent.part));
                }
                lines.add("Children: " + cemChildren.size());
            }

            renderFloatingText(lines, isSelected ? 0xFF55FF55 : 0xFFFFFFFF, isSelected);
        }

        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.bindTexture(originalTexture);
        GlStateManager.enableLighting();
        GlStateManager.enableDepth();
        GlStateManager.enableTexture2D();
        GL11.glLineWidth(1.0F);

        GlStateManager.popMatrix();
    }

    private void drawBoxWireframe(BufferBuilder b, float x1, float y1, float z1, float x2, float y2, float z2) {
        // Bottom
        b.pos(x1, y1, z1).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x2, y1, z1).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x2, y1, z1).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x2, y1, z2).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x2, y1, z2).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x1, y1, z2).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x1, y1, z2).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x1, y1, z1).color(0f, 1f, 1f, 0.8f).endVertex();
        // Top
        b.pos(x1, y2, z1).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x2, y2, z1).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x2, y2, z1).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x2, y2, z2).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x2, y2, z2).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x1, y2, z2).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x1, y2, z2).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x1, y2, z1).color(0f, 1f, 1f, 0.8f).endVertex();
        // Vertical
        b.pos(x1, y1, z1).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x1, y2, z1).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x2, y1, z1).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x2, y2, z1).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x2, y1, z2).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x2, y2, z2).color(0f, 1f, 1f, 0.8f).endVertex();
        b.pos(x1, y1, z2).color(0f, 1f, 1f, 0.8f).endVertex(); b.pos(x1, y2, z2).color(0f, 1f, 1f, 0.8f).endVertex();
    }

    private void renderFloatingText(List<String> lines, int color, boolean detailed) {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        float tagScale = detailed ? 0.005f : 0.003f;

        GlStateManager.pushMatrix();
        GlStateManager.translate(0, -0.1f, 0);

        GlStateManager.rotate(mc.getRenderManager().playerViewY, 0.0F, 1.0F, 0.0F);
        GlStateManager.rotate((float)(mc.getRenderManager().options.thirdPersonView == 2 ? -1 : 1) * mc.getRenderManager().playerViewX, 1.0F, 0.0F, 0.0F);

        GlStateManager.scale(-tagScale, tagScale, tagScale);
        GlStateManager.disableLighting();
        GlStateManager.depthMask(false);
        GlStateManager.disableDepth();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);

        int lineHeight = 10;
        int totalHeight = lines.size() * lineHeight;

        int maxWidth = 0;
        for (String line : lines) {
            int w = fr.getStringWidth(line);
            if (w > maxWidth) maxWidth = w;
        }

        int halfW = maxWidth / 2;
        int padding = 2;

        Tessellator t = Tessellator.getInstance();
        BufferBuilder b = t.getBuffer();
        GlStateManager.disableTexture2D();
        b.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        float bgAlpha = detailed ? 0.6f : 0.25f;
        b.pos(-halfW - padding, -padding, 0).color(0f, 0f, 0f, bgAlpha).endVertex();
        b.pos(-halfW - padding, totalHeight + padding, 0).color(0f, 0f, 0f, bgAlpha).endVertex();
        b.pos(halfW + padding, totalHeight + padding, 0).color(0f, 0f, 0f, bgAlpha).endVertex();
        b.pos(halfW + padding, -padding, 0).color(0f, 0f, 0f, bgAlpha).endVertex();
        t.draw();
        GlStateManager.enableTexture2D();

        int y = 0;
        for (String line : lines) {
            fr.drawString(line, -fr.getStringWidth(line) / 2, y, color);
            y += lineHeight;
        }

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.popMatrix();
    }

    private void renderBoxes(float scale, boolean invX, boolean invY, boolean invZ) {
        if (cemPart.boxes.isEmpty()) return;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        for (CEMBox box : cemPart.boxes) {
            float x = box.coordinates[0];
            float y = box.coordinates[1];
            float z = box.coordinates[2];
            float w = box.coordinates[3];
            float h = box.coordinates[4];
            float d = box.coordinates[5];
            float inflate = box.sizeAdd;

            // Inverted Axis Logic for Box Coordinates (Standard Optifine)
            if (invX) x = -(x + w);
            if (invY) y = -(y + h);
            if (invZ) z = -(z + d);

            // JPM Coordinates are Global/Absolute.
            // We are currently rendering in Local Space (Matrix translated by relative offset).
            // To get local box coordinates: Local = Global - AbsPivot.
            float x1 = (x - inflate + absPivotX) * scale;
            float y1 = (y - inflate + absPivotY) * scale;
            float z1 = (z - inflate + absPivotZ) * scale;
            float x2 = (x + w + inflate + absPivotX) * scale;
            float y2 = (y + h + inflate + absPivotY) * scale;
            float z2 = (z + d + inflate + absPivotZ) * scale;

            boolean mirrorU = cemPart.mirrorTexture.contains("u");

            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_NORMAL);

            int texU = box.textureOffset[0];
            int texV = box.textureOffset[1];
            float texW = textureWidth;
            float texH = textureHeight;

            float[] uvNorth = box.uvNorth != null ? normalizeUV(box.uvNorth, texW, texH) : calculateFaceUV(texU, texV, w, h, d, "north", texW, texH);
            float[] uvSouth = box.uvSouth != null ? normalizeUV(box.uvSouth, texW, texH) : calculateFaceUV(texU, texV, w, h, d, "south", texW, texH);
            float[] uvEast = box.uvEast != null ? normalizeUV(box.uvEast, texW, texH) : calculateFaceUV(texU, texV, w, h, d, "east", texW, texH);
            float[] uvWest = box.uvWest != null ? normalizeUV(box.uvWest, texW, texH) : calculateFaceUV(texU, texV, w, h, d, "west", texW, texH);
            float[] uvUp = box.uvUp != null ? normalizeUV(box.uvUp, texW, texH) : calculateFaceUV(texU, texV, w, h, d, "up", texW, texH);
            float[] uvDown = box.uvDown != null ? normalizeUV(box.uvDown, texW, texH) : calculateFaceUV(texU, texV, w, h, d, "down", texW, texH);

            if (mirrorU) {
                uvNorth = mirrorUV(uvNorth);
                uvSouth = mirrorUV(uvSouth);
                uvEast = mirrorUV(uvEast);
                uvWest = mirrorUV(uvWest);
                uvUp = mirrorUV(uvUp);
                uvDown = mirrorUV(uvDown);
            }

            addFace(buffer, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, uvNorth, 0, 0, -1);
            addFace(buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, uvSouth, 0, 0, 1);
            addFace(buffer, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, uvEast, 1, 0, 0);
            addFace(buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, uvWest, -1, 0, 0);
            addFace(buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, uvUp, 0, -1, 0);
            addFace(buffer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, uvDown, 0, 1, 0);

            tessellator.draw();
        }
    }

    private float[] normalizeUV(float[] uv, float w, float h) {
        return new float[]{uv[0] / w, uv[1] / h, uv[2] / w, uv[3] / h};
    }

    private float[] calculateFaceUV(int texU, int texV, float sizeX, float sizeY, float sizeZ, String face, float texW, float texH) {
        float u1, v1, u2, v2;
        switch (face) {
            case "north" -> { u1 = texU + sizeZ; v1 = texV + sizeZ; u2 = u1 + sizeX; v2 = v1 + sizeY; }
            case "south" -> { u1 = texU + sizeZ + sizeX + sizeZ; v1 = texV + sizeZ; u2 = u1 + sizeX; v2 = v1 + sizeY; }
            case "east" -> { u1 = texU; v1 = texV + sizeZ; u2 = u1 + sizeZ; v2 = v1 + sizeY; }
            case "west" -> { u1 = texU + sizeZ + sizeX; v1 = texV + sizeZ; u2 = u1 + sizeZ; v2 = v1 + sizeY; }
            case "up" -> { u1 = texU + sizeZ; v1 = texV; u2 = u1 + sizeX; v2 = v1 + sizeZ; }
            case "down" -> { u1 = texU + sizeZ + sizeX; v1 = texV; u2 = u1 + sizeX; v2 = v1 + sizeZ; }
            default -> { u1 = 0; v1 = 0; u2 = 0; v2 = 0; }
        }
        return new float[]{u1 / texW, v1 / texH, u2 / texW, v2 / texH};
    }

    private float[] mirrorUV(float[] uv) {
        return new float[]{uv[2], uv[1], uv[0], uv[3]};
    }

    private void addFace(BufferBuilder buffer, float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         float[] uv, float nx, float ny, float nz) {
        buffer.pos(x1, y1, z1).tex(uv[2], uv[1]).normal(nx, ny, nz).endVertex();
        buffer.pos(x2, y2, z2).tex(uv[0], uv[1]).normal(nx, ny, nz).endVertex();
        buffer.pos(x3, y3, z3).tex(uv[0], uv[3]).normal(nx, ny, nz).endVertex();
        buffer.pos(x4, y4, z4).tex(uv[2], uv[3]).normal(nx, ny, nz).endVertex();
    }
}