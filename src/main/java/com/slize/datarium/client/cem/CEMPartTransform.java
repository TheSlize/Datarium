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
    public boolean visibleBoxes = true;

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
    public boolean hasVisibleBoxes = false;

    CEMModelWrapper boundWrapper;
    CEMModelRenderer boundRenderer;

    public boolean isEmpty() {
        return !(hasRotateX || hasRotateY || hasRotateZ || hasTranslateX || hasTranslateY || hasTranslateZ
                || hasScaleX || hasScaleY || hasScaleZ || hasVisible || hasVisibleBoxes);
    }

    public void reset() {
        rotateX = rotateY = rotateZ = 0;
        translateX = translateY = translateZ = 0;
        scaleX = scaleY = scaleZ = 1;
        visible = true;
        visibleBoxes = true;
        hasRotateX = hasRotateY = hasRotateZ = false;
        hasTranslateX = hasTranslateY = hasTranslateZ = false;
        hasScaleX = hasScaleY = hasScaleZ = false;
        hasVisible = false;
        hasVisibleBoxes = false;
    }

    public void copyUnsetFrom(CEMPartTransform src) {
        if (!hasRotateX && src.hasRotateX) { rotateX = src.rotateX; hasRotateX = true; }
        if (!hasRotateY && src.hasRotateY) { rotateY = src.rotateY; hasRotateY = true; }
        if (!hasRotateZ && src.hasRotateZ) { rotateZ = src.rotateZ; hasRotateZ = true; }
        if (!hasTranslateX && src.hasTranslateX) { translateX = src.translateX; hasTranslateX = true; }
        if (!hasTranslateY && src.hasTranslateY) { translateY = src.translateY; hasTranslateY = true; }
        if (!hasTranslateZ && src.hasTranslateZ) { translateZ = src.translateZ; hasTranslateZ = true; }
        if (!hasScaleX && src.hasScaleX) { scaleX = src.scaleX; hasScaleX = true; }
        if (!hasScaleY && src.hasScaleY) { scaleY = src.scaleY; hasScaleY = true; }
        if (!hasScaleZ && src.hasScaleZ) { scaleZ = src.scaleZ; hasScaleZ = true; }
    }
}