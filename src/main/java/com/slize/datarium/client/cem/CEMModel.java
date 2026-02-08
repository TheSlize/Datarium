package com.slize.datarium.client.cem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CEMModel {
    public int[] textureSize;
    public List<CEMModelPart> parts;
    public Map<String, CEMModelPart> partById;
    public List<CEMAnimation> animations;
    public String credit;

    public CEMModel() {
        this.textureSize = new int[]{64, 64};
        this.parts = new ArrayList<>();
        this.partById = new HashMap<>();
        this.animations = new ArrayList<>();
    }

    public void indexParts() {
        partById.clear();
        for (CEMModelPart part : parts) {
            indexPartRecursive(part);
        }
    }

    private void indexPartRecursive(CEMModelPart part) {
        if (part.id != null && !part.id.isEmpty()) {
            partById.put(part.id, part);
        }
        for (CEMModelPart sub : part.submodels) {
            indexPartRecursive(sub);
        }
    }
}