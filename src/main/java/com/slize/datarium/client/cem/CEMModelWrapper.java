package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CEMModelWrapper {
    private final CEMModel cemModel;
    private final Map<String, CEMModelRenderer> partRenderers;
    private final Map<String, ModelRenderer> vanillaPartBackup;

    private static final Map<String, String> FIELD_TO_CEM = new HashMap<>();
    private static final Map<String, String> CEM_TO_FIELD = new HashMap<>();

    static {
        addBidirectionalMapping("leg1", "right_hind_leg");
        addBidirectionalMapping("leg2", "left_hind_leg");
        addBidirectionalMapping("leg3", "right_front_leg");
        addBidirectionalMapping("leg4", "left_front_leg");
        addBidirectionalMapping("bipedHead", "head");
        addBidirectionalMapping("bipedHeadwear", "headwear");
        addBidirectionalMapping("bipedBody", "body");
        addBidirectionalMapping("bipedRightArm", "right_arm");
        addBidirectionalMapping("bipedLeftArm", "left_arm");
        addBidirectionalMapping("bipedRightLeg", "right_leg");
        addBidirectionalMapping("bipedLeftLeg", "left_leg");
        addBidirectionalMapping("villagerHead", "head");
        addBidirectionalMapping("villagerBody", "body");
        addBidirectionalMapping("villagerArms", "arms");
        addBidirectionalMapping("rightVillagerLeg", "right_leg");
        addBidirectionalMapping("leftVillagerLeg", "left_leg");
        addBidirectionalMapping("villagerNose", "nose");
    }

    private static void addBidirectionalMapping(String fieldName, String cemName) {
        FIELD_TO_CEM.put(fieldName, cemName);
        CEM_TO_FIELD.put(cemName, fieldName);
    }

    public CEMModelWrapper(CEMModel cemModel, ModelBase vanillaModel) {
        this.cemModel = cemModel;
        this.partRenderers = new HashMap<>();
        this.vanillaPartBackup = new HashMap<>();

        buildRenderers(vanillaModel);
    }

    private void buildRenderers(ModelBase vanillaModel) {
        int texW = cemModel.textureSize[0];
        int texH = cemModel.textureSize[1];

        for (CEMModelPart part : cemModel.parts) {
            // Pass null as parent for root parts
            CEMModelRenderer renderer = new CEMModelRenderer(vanillaModel, part, texW, texH, null);
            registerRendererRecursive(renderer, part, null);
        }

        DatariumMain.LOGGER.info("[CEM] Registered {} part   renderers: {}", partRenderers.size(), partRenderers.keySet());
    }

    private void registerRendererRecursive(CEMModelRenderer renderer, CEMModelPart part, @Nullable String parentPath) {
        String id = part.id != null && !part.id.isEmpty() ? part.id : null;
        String partName = part.part != null && !part.part.isEmpty() ? part.part : null;

        String currentPath = null;
        if (id != null) {
            currentPath = parentPath != null ? parentPath + "." + id : id;
        } else if (partName != null) {
            currentPath = parentPath != null ? parentPath + "." + partName : partName;
        }

        if (id != null) {
            partRenderers.put(id, renderer);
        }

        if (partName != null) {
            partRenderers.put(partName, renderer);

            String cemCanonical = FIELD_TO_CEM.get(partName);
            if (cemCanonical != null && !partRenderers.containsKey(cemCanonical)) {
                partRenderers.put(cemCanonical, renderer);
            }

            String fieldName = CEM_TO_FIELD.get(partName);
            if (fieldName != null && !partRenderers.containsKey(fieldName)) {
                partRenderers.put(fieldName, renderer);
            }
        }

        if (currentPath != null && !currentPath.equals(id) && !currentPath.equals(partName)) {
            partRenderers.put(currentPath, renderer);
        }

        List<CEMModelRenderer> children = renderer.getCemChildren();
        for (int i = 0; i < children.size(); i++) {
            CEMModelRenderer child = children.get(i);
            CEMModelPart childPart = part.submodels.get(i);
            registerRendererRecursive(child, childPart, currentPath);
        }
    }

    @Nullable
    public CEMModelRenderer getPartRenderer(String partName) {
        CEMModelRenderer result = partRenderers.get(partName);
        if (result != null) return result;

        String cemName = FIELD_TO_CEM.get(partName);
        if (cemName != null) {
            result = partRenderers.get(cemName);
            if (result != null) return result;
        }

        String fieldName = CEM_TO_FIELD.get(partName);
        if (fieldName != null) {
            result = partRenderers.get(fieldName);
            if (result != null) return result;
        }

        return null;
    }

    public void applyTransforms(Map<String, CEMPartTransform> transforms) {
        for (Map.Entry<String, CEMPartTransform> entry : transforms.entrySet()) {
            CEMModelRenderer renderer = getPartRenderer(entry.getKey());
            if (renderer != null) {
                renderer.setTransform(entry.getValue());
            }
        }
    }

    public void renderDebug(float scale) {
        // Only iterate over root parts defined in the CEM model
        for (CEMModelPart part : cemModel.parts) {
            // Find the renderer corresponding to this root part
            String key = part.id != null ? part.id : part.part;
            CEMModelRenderer renderer = getPartRenderer(key);
            if (renderer != null) {
                renderer.renderDebugOnly(scale);
            }
        }
    }

    public Map<String, CEMModelRenderer> getAllParts() {
        return partRenderers;
    }

    public CEMModel getCemModel() {
        return cemModel;
    }
}