package com.slize.datarium.util;

import com.google.gson.*;
import com.slize.datarium.mixin.IAbstractResourcePackAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RespackOptsManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().setStrictness(Strictness.LENIENT).create();

    private static final Map<String, Boolean> FLAGS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> USER_OVERRIDES = new ConcurrentHashMap<>();

    private static final File CONFIG_FILE = new File(Minecraft.getMinecraft().gameDir, "config/datarium/respackopts_user.json");
    private static boolean loaded = false;
    private static boolean changesPending = false;

    public static void reload() {
        FLAGS.clear();
        loadUserConfig();
        loaded = true;
        changesPending = false;

        List<ResourcePackRepository.Entry> entries = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries();
        for (ResourcePackRepository.Entry entry : entries) {
            IResourcePack pack = entry.getResourcePack();
            loadFromPack(pack);
        }
    }

    public static boolean hasChanges() {
        return changesPending;
    }

    public static void markChanges() {
        changesPending = true;
    }

    private static void loadUserConfig() {
        USER_OVERRIDES.clear();
        if (CONFIG_FILE.exists()) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE.toPath())) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json != null) {
                    for (String key : json.keySet()) {
                        USER_OVERRIDES.put(key, json.get(key).getAsBoolean());
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void saveUserConfig() {
        try {
            if (!CONFIG_FILE.getParentFile().exists()) {
                CONFIG_FILE.getParentFile().mkdirs();
            }
            JsonObject json = new JsonObject();
            for (Map.Entry<String, Boolean> entry : USER_OVERRIDES.entrySet()) {
                json.addProperty(entry.getKey(), entry.getValue());
            }
            try (Writer writer = Files.newBufferedWriter(CONFIG_FILE.toPath())) {
                GSON.toJson(json, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void setUserFlag(String key, boolean value) {
        USER_OVERRIDES.put(key, value);
        FLAGS.put(key, value);
        saveUserConfig();
        markChanges();
    }

    public static boolean getFlag(String key) {
        return FLAGS.getOrDefault(key, false);
    }

    public static void removeOverride(String key, boolean defaultValue) {
        USER_OVERRIDES.remove(key);
        FLAGS.put(key, defaultValue); // Reset runtime to default immediately
        saveUserConfig();
        markChanges();
    }

    public static JsonObject getPackConfiguration(IResourcePack pack) {
        if (pack instanceof AbstractResourcePack) {
            IAbstractResourcePackAccessor accessor = (IAbstractResourcePackAccessor) pack;
            if (accessor.invokeHasResourceName("respackopts.json5")) {
                try (InputStream is = accessor.invokeGetInputStreamByName("respackopts.json5")) {
                    if (is != null) {
                        String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                        String json = Json5Helper.cleanJson5(content);
                        return GSON.fromJson(json, JsonObject.class);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    private static void loadFromPack(IResourcePack pack) {
        JsonObject root = getPackConfiguration(pack);
        if (root != null) {
            String packId = root.has("id") ? root.get("id").getAsString() : "";
            if (root.has("conf")) {
                parseConf(root.getAsJsonObject("conf"), packId);
            }
        }
    }

    private static void parseConf(JsonObject obj, String prefix) {
        for (String key : obj.keySet()) {
            JsonElement el = obj.get(key);
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

            if (el.isJsonObject()) {
                parseConf(el.getAsJsonObject(), fullKey);
            } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isBoolean()) {
                boolean defaultValue = el.getAsBoolean();
                FLAGS.put(fullKey, USER_OVERRIDES.getOrDefault(fullKey, defaultValue));
            }
        }
    }

    public static boolean checkCondition(String condition) {
        if (!loaded) reload();
        if (condition == null || condition.trim().isEmpty()) return true;
        return new ConditionParser(condition).parse();
    }

    public static void invalidate() {
        loaded = false;
    }

    // ConditionParser class (Same as before)
    private static class ConditionParser {
        private final String expression;
        private int pos = 0;

        public ConditionParser(String expression) {
            this.expression = expression.replaceAll("\\s+", "");
        }

        public boolean parse() {
            try {
                return parseOr();
            } catch (Exception e) {
                return false;
            }
        }

        private boolean parseOr() {
            boolean left = parseAnd();
            while (pos < expression.length() && expression.charAt(pos) == '|') {
                pos++;
                boolean right = parseAnd();
                left = left || right;
            }
            return left;
        }

        private boolean parseAnd() {
            boolean left = parseFactor();
            while (pos < expression.length() && expression.charAt(pos) == '&') {
                pos++;
                boolean right = parseFactor();
                left = left && right;
            }
            return left;
        }

        private boolean parseFactor() {
            if (pos >= expression.length()) return false;
            char c = expression.charAt(pos);
            if (c == '!') {
                pos++;
                return !parseFactor();
            } else if (c == '(') {
                pos++;
                boolean result = parseOr();
                if (pos < expression.length() && expression.charAt(pos) == ')') {
                    pos++;
                }
                return result;
            } else {
                int start = pos;
                while (pos < expression.length() && isIdentChar(expression.charAt(pos))) {
                    pos++;
                }
                String key = expression.substring(start, pos);
                return FLAGS.getOrDefault(key, false);
            }
        }

        private boolean isIdentChar(char c) {
            return Character.isJavaIdentifierPart(c) || c == '.';
        }
    }
}