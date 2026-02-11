package com.slize.datarium.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.slize.datarium.client.model.nodes.*;
import net.minecraft.client.renderer.block.model.ItemOverride;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModernModelDefinitionDeserializer {
    public static ModernModelNode parseModernJsonStatic(JsonObject json, List<ItemOverride> collector) {
        if (json.has("model") && json.get("model").isJsonObject()) {
            return parseModernNodeStatic(json.get("model").getAsJsonObject(), collector);
        }
        return null;
    }

    private static ModernModelNode parseModernNodeStatic(JsonObject json, List<ItemOverride> collector) {
        String type = normalizeType(json.get("type").getAsString());

        return switch (type) {
            case "condition" -> parseConditionNode(json, collector);
            case "range_dispatch" -> parseRangeDispatchNode(json, collector);
            case "select" -> parseSelectNode(json, collector);
            case "composite" -> parseCompositeNode(json, collector);
            case "model" -> parseModelLeafNode(json, collector);
            case "empty" -> new EmptyNode();
            default -> throw new JsonParseException("Unknown modern model type: " + type);
        };
    }

    private static ModernModelNode parseConditionNode(JsonObject json, List<ItemOverride> collector) {
        String property = json.get("property").getAsString();
        String predicate = json.has("predicate") ? json.get("predicate").getAsString() : null;
        String component = json.has("component") ? json.get("component").getAsString() : null;
        List<ConditionNode.EnchantmentValue> values = new ArrayList<>();

        if (json.has("value") && json.get("value").isJsonArray()) {
            JsonArray valueArray = json.getAsJsonArray("value");
            for (JsonElement ve : valueArray) {
                if (ve.isJsonObject()) {
                    JsonObject vo = ve.getAsJsonObject();
                    if (vo.has("enchantments")) {
                        values.add(new ConditionNode.EnchantmentValue(vo.get("enchantments").getAsString()));
                    }
                }
            }
        }

        ModernModelNode onTrue = parseModernNodeStatic(json.get("on_true").getAsJsonObject(), collector);
        ModernModelNode onFalse = parseModernNodeStatic(json.get("on_false").getAsJsonObject(), collector);
        return new ConditionNode(property, predicate, component, values, onTrue, onFalse);
    }

    private static ModernModelNode parseRangeDispatchNode(JsonObject json, List<ItemOverride> collector) {
        String property = json.get("property").getAsString();
        JsonArray entriesJson = json.getAsJsonArray("entries");
        List<RangeDispatchNode.Entry> entries = new ArrayList<>();
        ModernModelNode fallback = json.has("fallback") ? parseModernNodeStatic(json.get("fallback").getAsJsonObject(), collector) : null;

        for (JsonElement e : entriesJson) {
            JsonObject entryObj = e.getAsJsonObject();
            float threshold = entryObj.get("threshold").getAsFloat();
            ModernModelNode model = parseModernNodeStatic(entryObj.get("model").getAsJsonObject(), collector);
            entries.add(new RangeDispatchNode.Entry(threshold, model));
        }
        return new RangeDispatchNode(property, entries, fallback);
    }

    private static ModernModelNode parseSelectNode(JsonObject json, List<ItemOverride> collector) {
        String property = json.get("property").getAsString();
        String componentKey = json.has("component") ? json.get("component").getAsString() : null;
        JsonArray casesJson = json.getAsJsonArray("cases");
        List<SelectNode.Case> cases = new ArrayList<>();
        ModernModelNode fallback = json.has("fallback") ? parseModernNodeStatic(json.get("fallback").getAsJsonObject(), collector) : null;

        for (JsonElement c : casesJson) {
            JsonObject caseObj = c.getAsJsonObject();
            JsonElement whenEl = caseObj.get("when");
            List<String> values = new ArrayList<>();
            List<SelectNode.EnchantmentCondition> enchantmentConditions = new ArrayList<>();

            if (whenEl != null) {
                if (whenEl.isJsonArray()) {
                    for (JsonElement v : whenEl.getAsJsonArray()) {
                        if (v.isJsonPrimitive()) {
                            values.add(v.getAsString());
                        } else if (v.isJsonObject()) {
                            JsonObject enchObj = v.getAsJsonObject();
                            for (String key : enchObj.keySet()) {
                                int level = enchObj.get(key).getAsInt();
                                enchantmentConditions.add(new SelectNode.EnchantmentCondition(key, level));
                            }
                        }
                    }
                } else if (whenEl.isJsonPrimitive()) {
                    values.add(whenEl.getAsString());
                }
            }

            ModernModelNode model = parseModernNodeStatic(caseObj.get("model").getAsJsonObject(), collector);
            cases.add(new SelectNode.Case(values, enchantmentConditions, model));
        }
        return new SelectNode(property, componentKey, cases, fallback);
    }

    private static ModernModelNode parseCompositeNode(JsonObject json, List<ItemOverride> collector) {
        JsonArray modelsJson = json.getAsJsonArray("models");
        List<ModernModelNode> models = new ArrayList<>();
        for (JsonElement m : modelsJson) {
            models.add(parseModernNodeStatic(m.getAsJsonObject(), collector));
        }
        return new CompositeNode(models);
    }

    private static ModernModelNode parseModelLeafNode(JsonObject json, List<ItemOverride> collector) {
        String modelPath = json.get("model").getAsString();
        ResourceLocation loc = new ResourceLocation(modelPath);
        collector.add(new ItemOverride(loc, Collections.emptyMap()));
        return new ModelLeafNode(loc);
    }

    private static String normalizeType(String type) {
        if (type.startsWith("minecraft:")) {
            return type.substring("minecraft:".length());
        }
        return type;
    }
}