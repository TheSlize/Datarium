package com.slize.datarium.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slize.datarium.DatariumMain;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Locale;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(Locale.class)
public class MixinLocale {

    @Shadow
    Map<String, String> properties;

    @Inject(method = "loadLocaleDataFiles", at = @At("TAIL"))
    private void onLoadLocaleDataFiles(IResourceManager resourceManager, List<String> languageList, CallbackInfo ci) {
        for (String lang : languageList) {
            String jsonPath = String.format("lang/%s.json", lang);

            Set<String> domains = resourceManager.getResourceDomains();
            for (String domain : domains) {
                ResourceLocation jsonLoc = new ResourceLocation(domain, jsonPath);

                try {
                    List<IResource> resources = resourceManager.getAllResources(jsonLoc);
                    for (IResource resource : resources) {
                        try (InputStream is = resource.getInputStream()) {
                            String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                            JsonObject json = JsonParser.parseString(content).getAsJsonObject();

                            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                                if (entry.getValue().isJsonPrimitive()) {
                                    String value = entry.getValue().getAsString();
                                    this.properties.put(entry.getKey(), value);
                                }
                            }
                        } catch (Exception e) {
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
    }
}