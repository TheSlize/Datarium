package com.slize.datarium.util;

import net.minecraft.client.renderer.block.model.ItemCameraTransforms;

// TODO dude a whole class for a single field? definitely do smth about it
public class DatariumContext {
    public static final ThreadLocal<ItemCameraTransforms.TransformType> CURRENT_TRANSFORM = new ThreadLocal<>();
}
