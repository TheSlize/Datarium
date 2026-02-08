package com.slize.datarium.util;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import java.io.IOException;
import java.io.InputStream;

public class ResourceHelper {

    public static InputStream getModernItemDefinition(ResourceLocation modelLoc) {
        String domain = modelLoc.getNamespace();
        String path = modelLoc.getPath();

        if (path.startsWith("models/item/")) {
            path = path.substring("models/item/".length());
        }
        else if (path.startsWith("item/")) {
            path = path.substring("item/".length());
        }

        ResourceLocation modernLoc = new ResourceLocation(domain, "items/" + path + ".json");
        ResourceLocation redirect = RpoHandler.getRedirect(modernLoc);
        ResourceLocation target = redirect != null ? redirect : modernLoc;

        if (!target.getPath().endsWith(".json")) {
            target = new ResourceLocation(target.getNamespace(), target.getPath() + ".json");
        }

        try {
            return Minecraft.getMinecraft().getResourceManager().getResource(target).getInputStream();
        } catch (IOException e) {
            return null;
        }
    }
}