package com.slize.datarium.client.cem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
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
    @Nullable private final CEMModelRenderer parentRenderer;
    private final int textureWidth;
    private final int textureHeight;
    private final boolean bindsTexture;
    private final List<CEMModelRenderer> cemChildren;
    private final List<CEMModelRenderer> attachedExtras = new ArrayList<>();
    private final boolean topLevel;

    private CEMPartTransform transform;
    private ModelRenderer vanillaPart;

    // JEM "translate" converted into vanilla model space (invertAxis applied).
    private final float relOffsetX, relOffsetY, relOffsetZ;

    // Rotation pivot in vanilla model space, used when no animation overrides it.
    private final float defaultPivotX, defaultPivotY, defaultPivotZ;

    // Absolute pivot in vanilla model space (debug readout only).
    private final float absPivotX, absPivotY, absPivotZ;

    private float defaultRotateX, defaultRotateY, defaultRotateZ;

    // invertAxis never changes after load
    private final boolean invX, invY, invZ;
    private final boolean hasBoxes;

    private static final IntBuffer TEXTURE_BUF = BufferUtils.createIntBuffer(16);

    public CEMModelRenderer(ModelBase model, CEMModelPart cemPart, int texWidth, int texHeight, @Nullable CEMModelRenderer parent) {
        super(model);
        if (model.boxList != null) model.boxList.remove(this);
        this.cemPart = cemPart;
        this.parentRenderer = parent;
        this.textureWidth = cemPart.textureSize != null ? cemPart.textureSize[0] : texWidth;
        this.textureHeight = cemPart.textureSize != null ? cemPart.textureSize[1] : texHeight;
        this.bindsTexture = cemPart.texture != null && (parent == null || !cemPart.texture.equals(parent.cemPart.texture));
        this.cemChildren = new ArrayList<>();
        this.transform = null;
        this.vanillaPart = null;
        this.topLevel = cemPart.parent == null;

        this.invX = cemPart.invertAxis.contains("x");
        this.invY = cemPart.invertAxis.contains("y");
        this.invZ = cemPart.invertAxis.contains("z");
        this.hasBoxes = !cemPart.boxes.isEmpty();

        this.relOffsetX = invX ? -cemPart.translate[0] : cemPart.translate[0];
        this.relOffsetY = invY ? -cemPart.translate[1] : cemPart.translate[1];
        this.relOffsetZ = invZ ? -cemPart.translate[2] : cemPart.translate[2];

        // Top level boxes sit in absolute JEM space (origin = entity feet = (0, 24, 0) vanilla),
        // "translate" only encodes the pivot: pivot = jemOrigin - relOffset.
        this.defaultPivotX = -this.relOffsetX;
        this.defaultPivotY = 24.0F - this.relOffsetY;
        this.defaultPivotZ = -this.relOffsetZ;

        if (parent == null) {
            this.absPivotX = this.defaultPivotX;
            this.absPivotY = this.defaultPivotY;
            this.absPivotZ = this.defaultPivotZ;
        } else {
            this.absPivotX = parent.absPivotX + (parent.topLevel ? parent.relOffsetX : 0.0F) + this.relOffsetX;
            this.absPivotY = parent.absPivotY + (parent.topLevel ? parent.relOffsetY : 0.0F) + this.relOffsetY;
            this.absPivotZ = parent.absPivotZ + (parent.topLevel ? parent.relOffsetZ : 0.0F) + this.relOffsetZ;
        }

        this.defaultRotateX = (float) Math.toRadians(cemPart.rotate[0]);
        this.defaultRotateY = (float) Math.toRadians(cemPart.rotate[1]);
        this.defaultRotateZ = (float) Math.toRadians(cemPart.rotate[2]);

        if (invX) this.defaultRotateX = -this.defaultRotateX;
        if (invY) this.defaultRotateY = -this.defaultRotateY;
        if (invZ) this.defaultRotateZ = -this.defaultRotateZ;

        this.rotationPointX = this.topLevel ? this.defaultPivotX : this.relOffsetX;
        this.rotationPointY = this.topLevel ? this.defaultPivotY : this.relOffsetY;
        this.rotationPointZ = this.topLevel ? this.defaultPivotZ : this.relOffsetZ;
        this.rotateAngleX = this.defaultRotateX;
        this.rotateAngleY = this.defaultRotateY;
        this.rotateAngleZ = this.defaultRotateZ;

        for (CEMModelPart sub : cemPart.submodels) {
            cemChildren.add(new CEMModelRenderer(model, sub, textureWidth, textureHeight, this));
        }
    }

    public void applyAttachmentTransform(float scale) {
        if (parentRenderer != null) {
            parentRenderer.applyAttachmentTransform(scale);
            applyPivotRotationScale(scale);
        } else {
            applyPostRender(scale);
        }
        applyJemOffset(scale);
        applyBoundJemRotation();
    }

    @Nullable private float[] pivotOverride;

    public void setPivotOverride(@Nullable float[] pivot) {
        this.pivotOverride = pivot;
    }

    public String describe() {
        return String.format("pivot=(%.2f, %.2f, %.2f) rot=(%.3f, %.3f, %.3f) jemOffset=(%.1f, %.1f, %.1f) jemRot=(%.3f, %.3f, %.3f) top=%s vanilla=%s%s override=%s anim=%s",
                effectivePivotX(), effectivePivotY(), effectivePivotZ(),
                effectiveRotateX(), effectiveRotateY(), effectiveRotateZ(),
                topLevel ? relOffsetX : 0, topLevel ? relOffsetY : 0, topLevel ? relOffsetZ : 0,
                defaultRotateX, defaultRotateY, defaultRotateZ, topLevel,
                vanillaPart == null ? "none" : Integer.toHexString(System.identityHashCode(vanillaPart)),
                vanillaPart == null ? "" : String.format("@(%.2f, %.2f, %.2f)", vanillaPart.rotationPointX, vanillaPart.rotationPointY, vanillaPart.rotationPointZ),
                pivotOverride == null ? "none" : String.format("(%.1f, %.1f, %.1f)", pivotOverride[0], pivotOverride[1], pivotOverride[2]),
                transform == null ? "none" : String.format("t[%s%s%s] r[%s%s%s]",
                        transform.hasTranslateX ? "x" : "", transform.hasTranslateY ? "y" : "", transform.hasTranslateZ ? "z" : "",
                        transform.hasRotateX ? "x" : "", transform.hasRotateY ? "y" : "", transform.hasRotateZ ? "z" : ""));
    }

    private void applyBoundJemRotation() {
        if (!topLevel || vanillaPart == null) return;
        if (defaultRotateZ != 0.0F) GlStateManager.rotate((float) Math.toDegrees(defaultRotateZ), 0.0F, 0.0F, 1.0F);
        if (defaultRotateY != 0.0F) GlStateManager.rotate((float) Math.toDegrees(defaultRotateY), 0.0F, 1.0F, 0.0F);
        if (defaultRotateX != 0.0F) GlStateManager.rotate((float) Math.toDegrees(defaultRotateX), 1.0F, 0.0F, 0.0F);
    }

    public void setTransform(@Nullable CEMPartTransform transform) {
        this.transform = transform;
    }

    private CEMModelRenderer deferredParent;

    public boolean isDeferred() { return deferredParent != null; }

    public void setDeferred(@Nullable CEMModelRenderer parent) { this.deferredParent = parent; }

    public void setVanillaPart(@Nullable ModelRenderer part) {
        this.vanillaPart = part;
        this.deferredParent = null;
    }

    @Nullable
    public ModelRenderer getVanillaPart() {
        return vanillaPart;
    }

    public CEMModelPart getCemPart() {
        return cemPart;
    }

    public boolean isAttached() {
        return cemPart.attach;
    }

    public boolean isTopLevel() {
        return topLevel;
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
        if (vanillaPart != null && (vanillaPart.isHidden || !vanillaPart.showModel)) return;
        if (transform != null && transform.hasVisible && !transform.visible) return;

        GlStateManager.pushMatrix();
        int previousTexture = -1;
        if (bindsTexture) {
            TEXTURE_BUF.clear();
            GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D, TEXTURE_BUF);
            previousTexture = TEXTURE_BUF.get(0);
            Minecraft.getMinecraft().getTextureManager().bindTexture(cemPart.texture);
        }

        applyPivotRotationScale(scale);

        // Vanilla children live in the parent's pivot space, before the JEM offset
        // (villagerNose on villagerHead).
        renderVanillaChildren(scale);
        for (int i = 0; i < attachedExtras.size(); i++) {
            attachedExtras.get(i).renderWithVanilla(scale);
        }

        applyJemOffset(scale);
        applyBoundJemRotation();

        // Pivot-only bones carry no boxes, don't call an empty display list for them.
        if (hasBoxes && (transform == null || !transform.hasVisibleBoxes || transform.visibleBoxes)) {
            renderBoxes(scale, invX, invY, invZ);
        }

        for (CEMModelRenderer child : cemChildren) {
            child.renderWithVanilla(scale);
        }

        if (previousTexture >= 0) GlStateManager.bindTexture(previousTexture);
        GlStateManager.popMatrix();
    }

    /** Separate render pass for debug info. */
    public void renderDebugOnly(float scale) {
        GlStateManager.pushMatrix();

        applyPivotRotationScale(scale);

        if (CEMDebugSystem.enabled) renderDebugInternals(scale);

        if (CEMDebugSystem.enabled && vanillaPart != null && vanillaPart.childModels != null) {
            for (ModelRenderer vanillaChild : vanillaPart.childModels) {
                CEMModelRenderer replacement = CEMRenderHooks.getReplacement(vanillaChild);
                if (replacement != null) replacement.renderDebugOnly(scale);
            }
        }

        applyJemOffset(scale);
        applyBoundJemRotation();

        for (CEMModelRenderer child : cemChildren) {
            if (CEMDebugSystem.enabled) child.renderDebugOnly(scale);
        }

        GlStateManager.popMatrix();
    }

    private void renderVanillaChildren(float scale) {
        if (vanillaPart == null || vanillaPart.childModels == null || vanillaPart.childModels.isEmpty()) return;

        for (ModelRenderer vanillaChild : vanillaPart.childModels) {
            CEMModelRenderer replacement = CEMRenderHooks.getReplacement(vanillaChild);
            if (replacement != null && !replacement.isAttached()) {
                replacement.renderWithVanilla(scale);
                continue;
            }
            // Anonymous children (witch mole, horse ears/ropes) are authored against the vanilla pivot.
            GlStateManager.pushMatrix();
            GlStateManager.translate((vanillaPart.rotationPointX - effectivePivotX()) * scale,
                    (vanillaPart.rotationPointY - effectivePivotY()) * scale,
                    (vanillaPart.rotationPointZ - effectivePivotZ()) * scale);
            vanillaChild.render(scale);
            GlStateManager.popMatrix();
        }
    }

    public float getDefaultPivotX() { return topLevel ? defaultPivotX : relOffsetX; }
    public float getDefaultPivotY() { return topLevel ? defaultPivotY : relOffsetY; }
    public float getDefaultPivotZ() { return topLevel ? defaultPivotZ : relOffsetZ; }

    public float effectiveRotateX() {
        if (transform != null && transform.hasRotateX) return transform.rotateX;
        if (topLevel && vanillaPart != null) return vanillaPart.rotateAngleX;
        return defaultRotateX;
    }

    public float effectiveRotateY() {
        if (transform != null && transform.hasRotateY) return transform.rotateY;
        if (topLevel && vanillaPart != null) return vanillaPart.rotateAngleY;
        return defaultRotateY;
    }

    public float effectiveRotateZ() {
        if (transform != null && transform.hasRotateZ) return transform.rotateZ;
        if (topLevel && vanillaPart != null) return vanillaPart.rotateAngleZ;
        return defaultRotateZ;
    }

    public float effectiveScaleX() {
        float s = cemPart.scale != null ? cemPart.scale[0] : 1.0F;
        return transform != null && transform.hasScaleX ? s * transform.scaleX : s;
    }

    public float effectiveScaleY() {
        float s = cemPart.scale != null ? cemPart.scale[1] : 1.0F;
        return transform != null && transform.hasScaleY ? s * transform.scaleY : s;
    }

    public float effectiveScaleZ() {
        float s = cemPart.scale != null ? cemPart.scale[2] : 1.0F;
        return transform != null && transform.hasScaleZ ? s * transform.scaleZ : s;
    }

    /** Live value for "part.property" lookups inside animation expressions. */
    public double getProperty(String property) {
        return switch (property) {
            case "tx" -> effectivePivotX();
            case "ty" -> effectivePivotY();
            case "tz" -> effectivePivotZ();
            case "rx" -> effectiveRotateX();
            case "ry" -> effectiveRotateY();
            case "rz" -> effectiveRotateZ();
            case "sx" -> effectiveScaleX();
            case "sy" -> effectiveScaleY();
            case "sz" -> effectiveScaleZ();
            case "visible" -> isVisible() ? 1 : 0;
            case "visible_boxes" -> transform == null || !transform.hasVisibleBoxes || transform.visibleBoxes ? 1 : 0;
            default -> 0;
        };
    }

    private boolean isVisible() {
        if (transform != null && transform.hasVisible) return transform.visible;
        if (vanillaPart != null) return vanillaPart.showModel && !vanillaPart.isHidden;
        return true;
    }

    /** Mirrors vanilla ModelRenderer.postRender: applies the transform, renders nothing. */
    public void applyPostRender(float scale) {
        float[] adj = CEMManager.getRenderAdjust(CEMRenderHooks.getActiveModelName());
        if (adj != null) {
            GlStateManager.translate(0.0F, (adj[0] - 1.0F) * -1.5078125F, 0.0F);
            GlStateManager.scale(adj[0], adj[0], adj[0]);
            GlStateManager.translate(0.0F, adj[1] * 0.0625F, 0.0F);
        }
        if (deferredParent != null) deferredParent.applyPostRender(scale);
        applyPivotRotationScale(scale);
        if (adj != null) GlStateManager.scale(1.0F / adj[0], 1.0F / adj[0], 1.0F / adj[0]);
    }

    private void applyPivotRotationScale(float scale) {
        if (vanillaPart != null && !hasAnimatedTranslate()
                && (vanillaPart.offsetX != 0.0F || vanillaPart.offsetY != 0.0F || vanillaPart.offsetZ != 0.0F)) {
            GlStateManager.translate(vanillaPart.offsetX, vanillaPart.offsetY, vanillaPart.offsetZ);
        }

        float px = effectivePivotX();
        float py = effectivePivotY();
        float pz = effectivePivotZ();
        if (px != 0.0F || py != 0.0F || pz != 0.0F) {
            GlStateManager.translate(px * scale, py * scale, pz * scale);
        }

        float rx = effectiveRotateX();
        float ry = effectiveRotateY();
        float rz = effectiveRotateZ();
        if (rz != 0.0F) GlStateManager.rotate((float) Math.toDegrees(rz), 0.0F, 0.0F, 1.0F);
        if (ry != 0.0F) GlStateManager.rotate((float) Math.toDegrees(ry), 0.0F, 1.0F, 0.0F);
        if (rx != 0.0F) GlStateManager.rotate((float) Math.toDegrees(rx), 1.0F, 0.0F, 0.0F);

        float sx = effectiveScaleX();
        float sy = effectiveScaleY();
        float sz = effectiveScaleZ();
        if (sx != 1.0F || sy != 1.0F || sz != 1.0F) {
            GlStateManager.scale(sx, sy, sz);
        }
    }

    /**
     * Top level parts walk back out to the JEM origin after rotating around the pivot.
     * Submodels already used their translate as the pivot.
     */
    private void applyJemOffset(float scale) {
        if (!topLevel) return;
        if (relOffsetX == 0.0F && relOffsetY == 0.0F && relOffsetZ == 0.0F) return;
        GlStateManager.translate(relOffsetX * scale, relOffsetY * scale, relOffsetZ * scale);
    }

    /** Where the part is actually drawn: animation override, else the pivot the pack authored. */
    public float effectivePivotX() {
        if (transform != null && transform.hasTranslateX) return transform.translateX;
        if (vanillaPart != null) return pivotOverride != null ? pivotOverride[0] : vanillaPart.rotationPointX;
        return getDefaultPivotX();
    }

    public float effectivePivotY() {
        if (transform != null && transform.hasTranslateY) return transform.translateY;
        if (vanillaPart != null) return pivotOverride != null ? pivotOverride[1] : vanillaPart.rotationPointY;
        return getDefaultPivotY();
    }

    public float effectivePivotZ() {
        if (transform != null && transform.hasTranslateZ) return transform.translateZ;
        if (vanillaPart != null) return pivotOverride != null ? pivotOverride[2] : vanillaPart.rotationPointZ;
        return getDefaultPivotZ();
    }

    public boolean hasAnimatedTranslate() {
        return transform != null && (transform.hasTranslateX || transform.hasTranslateY || transform.hasTranslateZ);
    }

    public boolean hasAnimatedRotate() {
        return transform != null && (transform.hasRotateX || transform.hasRotateY || transform.hasRotateZ);
    }

    public void clearExtras() { attachedExtras.clear(); }

    public void addExtra(CEMModelRenderer extra) { attachedExtras.add(extra); }

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

        // Boxes and submodels are drawn past the JEM offset, the bone origin is not.
        float ox = topLevel ? relOffsetX : 0.0F;
        float oy = topLevel ? relOffsetY : 0.0F;
        float oz = topLevel ? relOffsetZ : 0.0F;

        float axisLen = isSelected ? 0.4f : 0.1f;
        b.pos(0, 0, 0).color(1f, 0f, 0f, 1f).endVertex();
        b.pos(axisLen, 0, 0).color(1f, 0f, 0f, 1f).endVertex();
        b.pos(0, 0, 0).color(0f, 1f, 0f, 1f).endVertex();
        b.pos(0, axisLen, 0).color(0f, 1f, 0f, 1f).endVertex();
        b.pos(0, 0, 0).color(0f, 0f, 1f, 1f).endVertex();
        b.pos(0, 0, axisLen).color(0f, 0f, 1f, 1f).endVertex();

        // Draw wireframe for boxes when selected
        if (isSelected) {
            for (CEMBox box : cemPart.boxes) {
                float bx = box.coordinates[0];
                float by = box.coordinates[1];
                float bz = box.coordinates[2];
                float bw = box.coordinates[3];
                float bh = box.coordinates[4];
                float bd = box.coordinates[5];

                if (invX) bx = -(bx + bw);
                if (invY) by = -(by + bh);
                if (invZ) bz = -(bz + bd);

                float x1, y1, z1, x2, y2, z2;
                x1 = (ox + bx - box.sizeAddX) * scale;
                y1 = (oy + by - box.sizeAddY) * scale;
                z1 = (oz + bz - box.sizeAddZ) * scale;
                x2 = (ox + bx + bw + box.sizeAddX) * scale;
                y2 = (oy + by + bh + box.sizeAddY) * scale;
                z2 = (oz + bz + bd + box.sizeAddZ) * scale;

                drawBoxWireframe(b, x1, y1, z1, x2, y2, z2);
            }
        }

        // Bones to JEM submodels
        for (CEMModelRenderer child : cemChildren) {
            float cx = (ox + child.effectivePivotX()) * scale;
            float cy = (oy + child.effectivePivotY()) * scale;
            float cz = (oz + child.effectivePivotZ()) * scale;

            b.pos(0, 0, 0).color(1f, 1f, 0f, 0.8f).endVertex();
            b.pos(cx, cy, cz).color(1f, 1f, 0f, 0.8f).endVertex();
        }

        // Bones to vanilla children (they hang off the pivot, not the JEM origin)
        if (vanillaPart != null && vanillaPart.childModels != null) {
            for (ModelRenderer vanillaChild : vanillaPart.childModels) {
                CEMModelRenderer replacement = CEMRenderHooks.getReplacement(vanillaChild);
                float cx = (replacement != null ? replacement.effectivePivotX() : vanillaChild.rotationPointX) * scale;
                float cy = (replacement != null ? replacement.effectivePivotY() : vanillaChild.rotationPointY) * scale;
                float cz = (replacement != null ? replacement.effectivePivotZ() : vanillaChild.rotationPointZ) * scale;

                b.pos(0, 0, 0).color(1f, 0.4f, 1f, 0.8f).endVertex();
                b.pos(cx, cy, cz).color(1f, 0.4f, 1f, 0.8f).endVertex();
            }
        }
        t.draw();

        if (partName != null && !partName.isEmpty()) {
            GlStateManager.enableTexture2D();
            List<String> lines = new ArrayList<>();
            lines.add((isSelected ? ">> " : "") + partName);

            if (isSelected) {

                float dispRx = defaultRotateX;
                float dispRy = defaultRotateY;
                float dispRz = defaultRotateZ;
                if (topLevel && vanillaPart != null) {
                    dispRx = vanillaPart.rotateAngleX;
                    dispRy = vanillaPart.rotateAngleY;
                    dispRz = vanillaPart.rotateAngleZ;
                }
                float dispTx = effectivePivotX();
                float dispTy = effectivePivotY();
                float dispTz = effectivePivotZ();
                float dispSx = 1f, dispSy = 1f, dispSz = 1f;

                if (transform != null) {
                    if (transform.hasRotateX) dispRx = transform.rotateX;
                    if (transform.hasRotateY) dispRy = transform.rotateY;
                    if (transform.hasRotateZ) dispRz = transform.rotateZ;
                    if (transform.hasScaleX) dispSx = transform.scaleX;
                    if (transform.hasScaleY) dispSy = transform.scaleY;
                    if (transform.hasScaleZ) dispSz = transform.scaleZ;
                }

                lines.add("TopLevel: " + topLevel + (vanillaPart != null ? " (bound)" : " (unbound)"));
                lines.add(String.format("AbsPivot: %.1f, %.1f, %.1f", absPivotX, absPivotY, absPivotZ));
                lines.add(String.format("DefPivot: %.1f, %.1f, %.1f", topLevel ? defaultPivotX : relOffsetX, topLevel ? defaultPivotY : relOffsetY, topLevel ? defaultPivotZ : relOffsetZ));
                lines.add(String.format("Pivot (eff): %.2f, %.2f, %.2f", dispTx, dispTy, dispTz));
                lines.add(String.format("JemOffset: %.1f, %.1f, %.1f", ox, oy, oz));
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
                    lines.add(String.format("real x1, y1, z1: %.2f, %.2f, %.2f", (box.coordinates[0] - box.sizeAdd) * scale * 16f, (box.coordinates[1] - box.sizeAdd) * scale * 16f, (box.coordinates[2] - box.sizeAdd) * scale * 16f));
                    lines.add(String.format("real x2, y2, z2: %.2f, %.2f, %.2f", (box.coordinates[0] + box.coordinates[3] + box.sizeAdd) * scale * 16f, (box.coordinates[1] + box.coordinates[4] + box.sizeAdd) * scale * 16f, (box.coordinates[2] + box.coordinates[5] + box.sizeAdd) * scale * 16f));
                }

                if (cemPart.parent != null) {
                    lines.add("Parent: " + (cemPart.parent.id != null ? cemPart.parent.id : cemPart.parent.part));
                }
                lines.add("Children: " + cemChildren.size());
                CEMDebugSystem.setLastDebugLines(lines);
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

    private int displayList = -1;
    private boolean compiled;
    private float compiledScale;

    private void renderBoxes(float scale, boolean invX, boolean invY, boolean invZ) {
        if (!compiled || compiledScale != scale) {
            if (displayList >= 0) GLAllocation.deleteDisplayLists(displayList);
            displayList = GLAllocation.generateDisplayLists(1);
            GlStateManager.glNewList(displayList, GL11.GL_COMPILE);
            buildBoxes(scale, invX, invY, invZ);
            GlStateManager.glEndList();
            compiled = true;
            compiledScale = scale;
        }
        GlStateManager.callList(displayList);
    }

    private void buildBoxes(float scale, boolean invX, boolean invY, boolean invZ) {
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

            if (invX) x = -(x + w);
            if (invY) y = -(y + h);
            if (invZ) z = -(z + d);
            float x1, y1, z1, x2, y2, z2;
            x1 = (x - box.sizeAddX) * scale;
            y1 = (y - box.sizeAddY) * scale;
            z1 = (z - box.sizeAddZ) * scale;
            x2 = (x + w + box.sizeAddX) * scale;
            y2 = (y + h + box.sizeAddY) * scale;
            z2 = (z + d + box.sizeAddZ) * scale;

            // Flat parts would emit two coincident quads with opposite normals. Culling is off for
            // entities, so the back quad wins the depth test: the unlit band across the villager's face.
            boolean flatX = x1 == x2;
            boolean flatY = y1 == y2;
            boolean flatZ = z1 == z2;
            if ((flatX && flatY) || (flatX && flatZ) || (flatY && flatZ)) continue;

            boolean drawNorth = !flatX && !flatY;
            boolean drawSouth = drawNorth;
            boolean drawMinusX = !flatY && !flatZ;
            boolean drawPlusX = drawMinusX;
            boolean drawUp = !flatX && !flatZ;
            boolean drawDown = drawUp;

            if (flatZ) {
                boolean keepSouth = box.uvNorth == null && box.uvSouth != null;
                drawNorth = !keepSouth;
                drawSouth = keepSouth;
            }
            if (flatX) {
                // "uvEast" is the -X face, "uvWest" is the +X face.
                boolean keepPlusX = box.uvEast == null && box.uvWest != null;
                drawPlusX = keepPlusX;
                drawMinusX = !keepPlusX;
            }
            if (flatY) {
                boolean keepDown = box.uvUp == null && box.uvDown != null;
                drawUp = !keepDown;
                drawDown = keepDown;
            }

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
                float[] side = uvEast;
                uvEast = mirrorUV(uvWest);
                uvWest = mirrorUV(side);
                uvNorth = mirrorUV(uvNorth);
                uvSouth = mirrorUV(uvSouth);
                uvUp = mirrorUV(uvUp);
                uvDown = mirrorUV(uvDown);
            }

            if (drawNorth)  addFace(buffer, x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1, uvNorth, 0, 0, -1);
            if (drawSouth)  addFace(buffer, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2, uvSouth, 0, 0, 1);
            if (drawPlusX)  addFace(buffer, x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2, uvWest, 1, 0, 0);
            if (drawMinusX) addFace(buffer, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1, uvEast, -1, 0, 0);
            if (drawUp)     addFace(buffer, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, uvUp, 0, -1, 0);
            if (drawDown)   addFace(buffer, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, uvDown, 0, 1, 0);

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
            case "up"   -> { u1 = texU + sizeZ + sizeX; v1 = texV + sizeZ; u2 = texU + sizeZ; v2 = texV; }
            case "down" -> { u1 = texU + sizeZ + sizeX + sizeX; v1 = texV; u2 = texU + sizeZ + sizeX; v2 = texV + sizeZ; }
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