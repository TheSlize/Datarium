package com.slize.datarium.client.model;

import com.slize.datarium.client.model.nodes.ModernModelNode;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.util.ResourceLocation;

import java.util.Collections;
/**
 * A dummy ItemOverride that acts as a carrier for the ModernModelNode logic.
 * It uses a property that will unlikely ever match (datarium:internal_carrier = 42.0),
 * so it won't interfere with vanilla override logic if it remains in the list.
 */
public class LogicCarrierOverride extends ItemOverride {
    public final ModernModelNode logic;

    public LogicCarrierOverride(ModernModelNode logic) {
        super(new ResourceLocation("datarium", "logic_carrier"),
                Collections.singletonMap(new ResourceLocation("datarium", "internal_carrier"), 42.0F));
        this.logic = logic;
    }
}
