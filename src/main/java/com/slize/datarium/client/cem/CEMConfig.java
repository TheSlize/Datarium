package com.slize.datarium.client.cem;

import com.slize.datarium.Reference;
import net.minecraftforge.common.config.Config;

@Config(modid = Reference.MOD_ID, name = Reference.MOD_ID + "/custom_entity_models", category = "cem")
public final class CEMConfig {

    @Config.Comment("Enable the CEM debug mode: the ';' debug key, part overlay, value dumps, model export and the expression profiler")
    public static boolean debugMode = false;

    private CEMConfig() {}
}
