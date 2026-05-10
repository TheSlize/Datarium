package com.slize.datarium;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;

@IFMLLoadingPlugin.Name("DatariumCore")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public class DatariumLoadingPlugin implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public List<String> getMixinConfigs() {
        return Collections.singletonList("datarium.early.mixin.json");
    }
}
