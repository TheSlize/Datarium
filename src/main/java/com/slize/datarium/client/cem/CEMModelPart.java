package com.slize.datarium.client.cem;

import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CEMModelPart {
    public String part;
    public String id;
    public boolean attach;
    public String invertAxis;
    public String mirrorTexture;
    public float[] translate;
    public float[] rotate;
    public float[] scale;
    public List<CEMBox> boxes;
    public List<CEMModelPart> submodels;
    @Nullable public ResourceLocation texture;
    @Nullable public int[] textureSize;
    public Map<String, float[]> attachments;

    @Nullable public CEMModelPart parent;

    public CEMModelPart() {
        this.translate = new float[]{0, 0, 0};
        this.rotate = new float[]{0, 0, 0};
        this.scale = new float[]{1, 1, 1};
        this.boxes = new ArrayList<>();
        this.submodels = new ArrayList<>();
        this.attachments = new LinkedHashMap<>();
        this.invertAxis = "";
        this.mirrorTexture = "";
        this.attach = false;
        this.parent = null;
    }
}
