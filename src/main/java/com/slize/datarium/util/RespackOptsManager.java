package com.slize.datarium.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.slize.datarium.DatariumMain;
import com.slize.datarium.mixin.IAbstractResourcePackAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RespackOptsManager {
    private static final Gson GSON = new GsonBuilder().setLenient().create();
    private static final Map<String, Boolean> FLAGS = new HashMap<>();
    private static boolean loaded = false;

    public static void reload() {
        FLAGS.clear();
        loaded = true;

        List<ResourcePackRepository.Entry> entries = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries();
        for (ResourcePackRepository.Entry entry : entries) {
            IResourcePack pack = entry.getResourcePack();
            loadFromPack(pack);
        }
    }

    private static void loadFromPack(IResourcePack pack) {
        if (pack instanceof AbstractResourcePack) {
            IAbstractResourcePackAccessor accessor = (IAbstractResourcePackAccessor) pack;

            if (accessor.invokeHasResourceName("respackopts.json5")) {
                try (InputStream is = accessor.invokeGetInputStreamByName("respackopts.json5")) {
                    if (is != null) {
                        String content = IOUtils.toString(is, StandardCharsets.UTF_8);
                        String json = Json5Helper.cleanJson5(content);
                        JsonObject root = GSON.fromJson(json, JsonObject.class);

                        if (root != null) {
                            // Fix: Extract ID to use as namespace prefix
                            String packId = "";
                            if (root.has("id")) {
                                packId = root.get("id").getAsString();
                            }

                            if (root.has("conf")) {
                                parseConf(root.getAsJsonObject("conf"), packId);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
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
                FLAGS.put(fullKey, el.getAsBoolean());
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

    /**
     * Parser for boolean expressions (e.g. "!optionA & optionB").
     */
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