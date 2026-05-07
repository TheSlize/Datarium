package com.slize.datarium.client.cit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class GlobalCITProperties {
    private static boolean useGlint = true;
    private static int cap = Integer.MAX_VALUE;
    private static String method = "average";
    private static float fade = 0.5f;
    private static boolean loaded = false;

    private static final ResourceLocation[] LOCATIONS = {
            new ResourceLocation("minecraft", "citresewn/cit.properties"),
            new ResourceLocation("minecraft", "optifine/cit.properties"),
            new ResourceLocation("minecraft", "mcpatcher/cit.properties")
    };

    public static void reload() {
        useGlint = true;
        cap = Integer.MAX_VALUE;
        method = "average";
        fade = 0.5f;
        loaded = true;

        IResourceManager rm = Minecraft.getMinecraft().getResourceManager();
        for (ResourceLocation loc : LOCATIONS) {
            try (InputStream is = rm.getResource(loc).getInputStream()) {
                Properties props = new Properties();
                props.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                useGlint = Boolean.parseBoolean(props.getProperty("useGlint", "true"));
                String capStr = props.getProperty("cap", "").trim();
                if (!capStr.isEmpty()) {
                    try { cap = Integer.parseInt(capStr); } catch (NumberFormatException ignored) {}
                }
                method = props.getProperty("method", "average").trim();
                String fadeStr = props.getProperty("fade", "0.5").trim();
                try { fade = Float.parseFloat(fadeStr); } catch (NumberFormatException ignored) {}
                return; // first match wins
            } catch (Exception ignored) {}
        }
    }

    public static void invalidate() { loaded = false; }

    public static boolean isUseGlint() { if (!loaded) reload(); return useGlint; }
    public static int getCap() { if (!loaded) reload(); return cap; }
    public static String getMethod() { if (!loaded) reload(); return method; }
    public static float getFade() { if (!loaded) reload(); return fade; }
}