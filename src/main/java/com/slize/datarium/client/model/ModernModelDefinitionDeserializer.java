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
        String type = json.get("type").getAsString();

        return switch (type) {
            case "minecraft:condition" -> {
                String property = json.get("property").getAsString();
                ModernModelNode onTrue = parseModernNodeStatic(json.get("on_true").getAsJsonObject(), collector);
                ModernModelNode onFalse = parseModernNodeStatic(json.get("on_false").getAsJsonObject(), collector);
                yield new ConditionNode(property, onTrue, onFalse);
            }
            case "minecraft:range_dispatch" -> {
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
                yield new RangeDispatchNode(property, entries, fallback);
            }
            case "minecraft:select" -> {
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
                yield new SelectNode(property, componentKey, cases, fallback);
            }
            case "minecraft:composite" -> {
                JsonArray modelsJson = json.getAsJsonArray("models");
                List<ModernModelNode> models = new ArrayList<>();
                for (JsonElement m : modelsJson) {
                    models.add(parseModernNodeStatic(m.getAsJsonObject(), collector));
                }
                yield new CompositeNode(models);
            }
            case "minecraft:model" -> {
                String modelPath = json.get("model").getAsString();
                ResourceLocation loc = new ResourceLocation(modelPath);
                collector.add(new ItemOverride(loc, Collections.emptyMap()));
                yield new ModelLeafNode(loc);
            }
            default -> throw new JsonParseException("Unknown modern model type: " + type);
        };
    }
}