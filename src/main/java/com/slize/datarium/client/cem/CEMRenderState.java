package com.slize.datarium.client.cem;

import com.slize.datarium.client.cem.expr.CEMRenderContext;
import net.minecraft.client.model.ModelRenderer;

import java.util.HashMap;
import java.util.Map;

public class CEMRenderState {
    public final CEMRenderContext context;
    public final Map<String, CEMPartTransform> transforms;
    public final Map<String, OriginalPartState> originalStates;
    public final Map<String, Map<String, CEMPartTransform>> secondaryTransforms = new HashMap<>();
    public long lastUpdateTime;
    public long lastRenderFrame = Long.MIN_VALUE;

    /** Set once per frame by MixinRenderLivingBase, read by the main and layer-model passes. See CEMThrottle. */
    public boolean animateThisFrame = true;

    /** Animation entries worth running for the textures this entity was last drawn with, see CEMModelWrapper.updatePruning. */
    public Object pruneOwner;
    public int[] prunedEntries;
    public String[] prunedParts;
    public String[] applyParts;
    public CEMPartTransform[] applyList;
    public CEMTextureMask[] pruneMasks;
    public int pruneSecondaries;
    public int pruneVersion;

    public CEMRenderState() {
        this.context = new CEMRenderContext();
        this.transforms = new HashMap<>();
        this.originalStates = new HashMap<>();
        this.lastUpdateTime = 0;
    }

    public void clearTransforms() {
        for (CEMPartTransform transform : transforms.values()) {
            transform.reset();
        }
    }

    public void saveOriginalState(String partName, ModelRenderer renderer) {
        OriginalPartState state = originalStates.computeIfAbsent(partName, k -> new OriginalPartState());
        state.rotateAngleX = renderer.rotateAngleX;
        state.rotateAngleY = renderer.rotateAngleY;
        state.rotateAngleZ = renderer.rotateAngleZ;
        state.rotationPointX = renderer.rotationPointX;
        state.rotationPointY = renderer.rotationPointY;
        state.rotationPointZ = renderer.rotationPointZ;
    }

    public static class OriginalPartState {
        public float rotateAngleX;
        public float rotateAngleY;
        public float rotateAngleZ;
        public float rotationPointX;
        public float rotationPointY;
        public float rotationPointZ;
        public boolean showModel;
    }
}