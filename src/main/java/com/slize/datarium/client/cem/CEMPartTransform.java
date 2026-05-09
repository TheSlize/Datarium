package com.slize.datarium.client.cem;

public class CEMPartTransform {
    public float rotateX = 0;
    public float rotateY = 0;
    public float rotateZ = 0;
    public float translateX = 0;
    public float translateY = 0;
    public float translateZ = 0;
    public float scaleX = 1;
    public float scaleY = 1;
    public float scaleZ = 1;
    public boolean visible = true;

    public boolean hasRotateX = false;
    public boolean hasRotateY = false;
    public boolean hasRotateZ = false;
    public boolean hasTranslateX = false;
    public boolean hasTranslateY = false;
    public boolean hasTranslateZ = false;
    public boolean hasScaleX = false;
    public boolean hasScaleY = false;
    public boolean hasScaleZ = false;
    public boolean hasVisible = false;

    public void reset() {
        rotateX = rotateY = rotateZ = 0;
        translateX = translateY = translateZ = 0;
        scaleX = scaleY = scaleZ = 1;
        visible = true;
        hasRotateX = hasRotateY = hasRotateZ = false;
        hasTranslateX = hasTranslateY = hasTranslateZ = false;
        hasScaleX = hasScaleY = hasScaleZ = false;
        hasVisible = false;
    }
}