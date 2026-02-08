package com.slize.datarium.client.cem;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CEMModelRenderer extends ModelRenderer {
    private final CEMModelPart cemPart;
    private final int textureWidth;
    private final int textureHeight;
    private final List<CEMModelRenderer> cemChildren;
    private CEMPartTransform transform;

    // Debug throttling
    private static long lastLogTime = 0;

    public CEMModelRenderer(ModelBase model, CEMModelPart cemPart, int texWidth, int texHeight) {
        super(model);
        this.cemPart = cemPart;
        this.textureWidth = texWidth;
        this.textureHeight = texHeight;
        this.cemChildren = new ArrayList<>();
        this.transform = null;

        // Set initial values for compatibility
        this.rotationPointX = cemPart.translate[0];
        this.rotationPointY = cemPart.translate[1];
        this.rotationPointZ = cemPart.translate[2];
        this.rotateAngleX = (float) Math.toRadians(cemPart.rotate[0]);
        this.rotateAngleY = (float) Math.toRadians(cemPart.rotate[1]);
        this.rotateAngleZ = (float) Math.toRadians(cemPart.rotate[2]);

        for (CEMModelPart sub : cemPart.submodels) {
            CEMModelRenderer childRenderer = new CEMModelRenderer(model, sub, texWidth, texHeight);
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
        renderWithVanilla(scale, 0, 0, 0);
    }

    public void renderWithVanilla(float scale, float vanillaX, float vanillaY, float vanillaZ) {
        if (!this.showModel) return;
        if (transform != null && transform.hasVisible && !transform.visible) return;

        GlStateManager.pushMatrix();

        boolean invX = cemPart.invertAxis.contains("x");
        boolean invY = cemPart.invertAxis.contains("y");
        boolean invZ = cemPart.invertAxis.contains("z");

        // --- Translation ---
        float tx, ty, tz;

        if (transform != null && transform.hasTranslateX) {
            tx = transform.translateX;
        } else {
            tx = cemPart.translate[0];
            if (invX) tx = -tx;
        }

        if (transform != null && transform.hasTranslateY) {
            ty = transform.translateY;
        } else {
            ty = cemPart.translate[1];
            if (invY) ty = -ty;
        }

        if (transform != null && transform.hasTranslateZ) {
            tz = transform.translateZ;
        } else {
            tz = cemPart.translate[2];
            if (invZ) tz = -tz;
        }

        GlStateManager.translate(tx * scale, ty * scale, tz * scale);

        // --- Rotation ---
        float rx, ry, rz;

        if (transform != null && transform.hasRotateX) {
            rx = transform.rotateX;
        } else {
            rx = (float) Math.toRadians(cemPart.rotate[0]);
            if (invX) rx = -rx;
        }

        if (transform != null && transform.hasRotateY) {
            ry = transform.rotateY;
        } else {
            ry = (float) Math.toRadians(cemPart.rotate[1]);
            if (invY) ry = -ry;
        }

        if (transform != null && transform.hasRotateZ) {
            rz = transform.rotateZ;
        } else {
            rz = (float) Math.toRadians(cemPart.rotate[2]);
            if (invZ) rz = -rz;
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

        renderBoxes(scale, invX, invY, invZ);

        for (CEMModelRenderer child : cemChildren) {
            child.renderWithVanilla(scale, 0, 0, 0);
        }

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

            // Inverted Axis Logic:
            // 1. Mirror the geometry: pos = -(pos + size)
            // 2. Do NOT compensate for translation. Coordinates are relative to the pivot.

            if (invX) {
                x = -(x + w);
            }
            if (invY) {
                y = -(y + h);
            }
            if (invZ) {
                z = -(z + d);
            }

            float x1 = (x - inflate) * scale;
            float y1 = (y - inflate) * scale;
            float z1 = (z - inflate) * scale;
            float x2 = (x + w + inflate) * scale;
            float y2 = (y + h + inflate) * scale;
            float z2 = (z + d + inflate) * scale;

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

    public static void resetDebugLog() {
        lastLogTime = 0;
    }
}