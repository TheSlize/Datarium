package com.slize.datarium.client.model;

import com.slize.datarium.client.model.nodes.ModernModelNode;
import net.minecraft.client.renderer.block.model.ItemOverride;

import java.util.ArrayList;

public class ModernOverrideListWrapper extends ArrayList<ItemOverride> {
    private final ModernModelNode logic;

    public ModernOverrideListWrapper(ModernModelNode logic) {
        super();
        this.logic = logic;
    }

    public ModernModelNode getLogic() {
        return logic;
    }
}
