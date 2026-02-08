package com.slize.datarium.util;

import com.slize.datarium.client.model.nodes.ModernModelNode;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;

public class DatariumContext {
    public static final ThreadLocal<ModernModelNode> CURRENT_LOGIC = new ThreadLocal<>();
    public static final ThreadLocal<ItemCameraTransforms.TransformType> CURRENT_TRANSFORM = new ThreadLocal<>();
}
