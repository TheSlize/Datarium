package com.slize.datarium.client.cem;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class CEMModelPart {
    public String part;
    public String id;
    @Nullable public String modelPath;
    public boolean attach;
    public String invertAxis;
    public String mirrorTexture;
    public float[] translate;
    public float[] rotate;
    public float[] scale;
    public List<CEMBox> boxes;
    public List<CEMModelPart> submodels;

    // New field to track hierarchy
    @Nullable public CEMModelPart parent;

    public CEMModelPart() {
        this.translate = new float[]{0, 0, 0};
        this.rotate = new float[]{0, 0, 0};
        this.scale = new float[]{1, 1, 1};
        this.boxes = new ArrayList<>();
        this.submodels = new ArrayList<>();
        this.invertAxis = "";
        this.mirrorTexture = "";
        this.attach = false;
        this.parent = null;
    }
}