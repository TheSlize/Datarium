package com.slize.datarium.client.cem;

import javax.annotation.Nullable;

public class CEMBox {
    public float[] coordinates; // [x, y, z, sizeX, sizeY, sizeZ]
    public int[] textureOffset; // [u, v]
    public float sizeAdd;

    @Nullable public float[] uvNorth;
    @Nullable public float[] uvSouth;
    @Nullable public float[] uvEast;
    @Nullable public float[] uvWest;
    @Nullable public float[] uvUp;
    @Nullable public float[] uvDown;

    public CEMBox() {
        this.coordinates = new float[6];
        this.textureOffset = new int[]{0, 0};
        this.sizeAdd = 0;
    }
}