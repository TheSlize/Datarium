package com.slize.datarium.util;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class RpoHandler {

    private static final Gson GSON = new GsonBuilder().setStrictness(Strictness.LENIENT).create();

    @Nullable
    public static ResourceLocation getRedirect(ResourceLocation original) {
        String path = original.getPath();

        // Append .rpo if it ends in .json, otherwise .json.rpo (handles both item defs and models)
        String rpoPath = path.endsWith(".json") ? path + ".rpo" : path + ".json.rpo";
        ResourceLocation rpoLoc = new ResourceLocation(original.getNamespace(), rpoPath);

        try (IResource res = getResource(rpoLoc)) {
            if (res == null) return null;

            String jsonStr;
            try (InputStream stream = res.getInputStream()) {
                if (stream == null) return null;
                jsonStr = IOUtils.toString(stream, StandardCharsets.UTF_8);
            }

            jsonStr = Json5Helper.cleanJson5(jsonStr);
            JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);

            // 1. Check condition of the current file.
            // If condition matches (TRUE), the original file is VALID -> RETURN NULL (No redirect).
            if (json.has("condition")) {
                String condition = json.get("condition").getAsString();
                if (RespackOptsManager.checkCondition(condition)) {
                    return null;
                }
            } else {
                return null;
            }

            // 2. Condition failed (original file invalid), check fallbacks.

            // Single fallback
            if (json.has("fallback")) {
                String fallbackPath = json.get("fallback").getAsString();
                ResourceLocation candidate = parseFallback(fallbackPath);
                if (isConditionMet(candidate)) {
                    return candidate;
                }
            }

            // Multiple fallbacks
            if (json.has("fallbacks")) {
                JsonArray fallbacks = json.getAsJsonArray("fallbacks");
                for (JsonElement e : fallbacks) {
                    String fallbackPath = e.getAsString();
                    ResourceLocation candidate = parseFallback(fallbackPath);
                    if (isConditionMet(candidate)) {
                        return candidate;
                    }
                }
            }

        } catch (Exception ignored) {
        }

        return null;
    }

    private static IResource getResource(ResourceLocation loc) {
        try {
            return Minecraft.getMinecraft().getResourceManager().getResource(loc);
        } catch (IOException e) {
            return null;
        }
    }

    private static boolean isConditionMet(ResourceLocation loc) {
        String path = loc.getPath();
        if (!path.endsWith(".json")) {
            path += ".json";
        }

        ResourceLocation rpoLoc = new ResourceLocation(loc.getNamespace(), path + ".rpo");

        try (IResource res = getResource(rpoLoc)) {
            if (res == null) return true;

            try (InputStream stream = res.getInputStream()) {
                if (stream == null) return true;
                String jsonStr = IOUtils.toString(stream, StandardCharsets.UTF_8);
                jsonStr = Json5Helper.cleanJson5(jsonStr);
                JsonObject json = GSON.fromJson(jsonStr, JsonObject.class);

                if (json.has("condition")) {
                    return RespackOptsManager.checkCondition(json.get("condition").getAsString());
                }
            }
        } catch (Exception e) {
        }

        return true;
    }

    private static ResourceLocation parseFallback(String path) {
        if (path.startsWith("assets/")) {
            path = path.substring("assets/".length());
        }

        int slashIndex = path.indexOf('/');
        if (slashIndex == -1) return new ResourceLocation(path);

        String domain = path.substring(0, slashIndex);
        String remaining = path.substring(slashIndex + 1);

        if (remaining.endsWith(".json")) {
            remaining = remaining.substring(0, remaining.length() - ".json".length());
        }
        return new ResourceLocation(domain, remaining);
    }
}