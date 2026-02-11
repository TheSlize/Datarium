package com.slize.datarium.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.Locale;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Mixin(Locale.class)
public class MixinLocale {

    @Shadow
    Map<String, String> properties;

    @Unique
    private static final Map<String, String> datarium$KEY_MAPPINGS = new HashMap<>();

    static {
        // Enchantments
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.sharpness", "enchantment.damage.all");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.smite", "enchantment.damage.undead");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.bane_of_arthropods", "enchantment.damage.arthropods");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.knockback", "enchantment.knockback");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.fire_aspect", "enchantment.fire");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.looting", "enchantment.lootBonus");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.sweeping_edge", "enchantment.sweeping");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.efficiency", "enchantment.digging");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.silk_touch", "enchantment.untouching");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.unbreaking", "enchantment.durability");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.fortune", "enchantment.lootBonusDigger");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.power", "enchantment.arrowDamage");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.punch", "enchantment.arrowKnockback");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.flame", "enchantment.arrowFire");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.infinity", "enchantment.arrowInfinite");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.luck_of_the_sea", "enchantment.lootBonusFishing");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.lure", "enchantment.fishingSpeed");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.protection", "enchantment.protect.all");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.fire_protection", "enchantment.protect.fire");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.feather_falling", "enchantment.protect.fall");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.blast_protection", "enchantment.protect.explosion");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.projectile_protection", "enchantment.protect.projectile");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.respiration", "enchantment.oxygen");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.aqua_affinity", "enchantment.waterWorker");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.thorns", "enchantment.thorns");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.depth_strider", "enchantment.waterWalker");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.frost_walker", "enchantment.frostWalker");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.mending", "enchantment.mending");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.binding_curse", "enchantment.binding_curse");
        datarium$KEY_MAPPINGS.put("enchantment.minecraft.vanishing_curse", "enchantment.vanishing_curse");

        // Effects
        datarium$KEY_MAPPINGS.put("effect.minecraft.speed", "effect.moveSpeed");
        datarium$KEY_MAPPINGS.put("effect.minecraft.slowness", "effect.moveSlowdown");
        datarium$KEY_MAPPINGS.put("effect.minecraft.haste", "effect.digSpeed");
        datarium$KEY_MAPPINGS.put("effect.minecraft.mining_fatigue", "effect.digSlowDown");
        datarium$KEY_MAPPINGS.put("effect.minecraft.strength", "effect.damageBoost");
        datarium$KEY_MAPPINGS.put("effect.minecraft.instant_health", "effect.heal");
        datarium$KEY_MAPPINGS.put("effect.minecraft.instant_damage", "effect.harm");
        datarium$KEY_MAPPINGS.put("effect.minecraft.jump_boost", "effect.jump");
        datarium$KEY_MAPPINGS.put("effect.minecraft.nausea", "effect.confusion");
        datarium$KEY_MAPPINGS.put("effect.minecraft.regeneration", "effect.regeneration");
        datarium$KEY_MAPPINGS.put("effect.minecraft.resistance", "effect.resistance");
        datarium$KEY_MAPPINGS.put("effect.minecraft.fire_resistance", "effect.fireResistance");
        datarium$KEY_MAPPINGS.put("effect.minecraft.water_breathing", "effect.waterBreathing");
        datarium$KEY_MAPPINGS.put("effect.minecraft.invisibility", "effect.invisibility");
        datarium$KEY_MAPPINGS.put("effect.minecraft.blindness", "effect.blindness");
        datarium$KEY_MAPPINGS.put("effect.minecraft.night_vision", "effect.nightVision");
        datarium$KEY_MAPPINGS.put("effect.minecraft.hunger", "effect.hunger");
        datarium$KEY_MAPPINGS.put("effect.minecraft.weakness", "effect.weakness");
        datarium$KEY_MAPPINGS.put("effect.minecraft.poison", "effect.poison");
        datarium$KEY_MAPPINGS.put("effect.minecraft.wither", "effect.wither");
        datarium$KEY_MAPPINGS.put("effect.minecraft.health_boost", "effect.healthBoost");
        datarium$KEY_MAPPINGS.put("effect.minecraft.absorption", "effect.absorption");
        datarium$KEY_MAPPINGS.put("effect.minecraft.saturation", "effect.saturation");
        datarium$KEY_MAPPINGS.put("effect.minecraft.glowing", "effect.glowing");
        datarium$KEY_MAPPINGS.put("effect.minecraft.levitation", "effect.levitation");
        datarium$KEY_MAPPINGS.put("effect.minecraft.luck", "effect.luck");
        datarium$KEY_MAPPINGS.put("effect.minecraft.unluck", "effect.unluck");

        // Biomes
        datarium$KEY_MAPPINGS.put("biome.minecraft.ocean", "biome.ocean.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.plains", "biome.plains.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.desert", "biome.desert.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.mountains", "biome.extremeHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.forest", "biome.forest.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.taiga", "biome.taiga.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.swamp", "biome.swampland.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.river", "biome.river.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.nether_wastes", "biome.hell.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.the_end", "biome.sky.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.frozen_ocean", "biome.frozenOcean.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.frozen_river", "biome.frozenRiver.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.snowy_tundra", "biome.icePlains.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.snowy_mountains", "biome.iceMountains.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.mushroom_fields", "biome.mushroomIsland.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.mushroom_field_shore", "biome.mushroomIslandShore.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.beach", "biome.beach.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.desert_hills", "biome.desertHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.wooded_hills", "biome.forestHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.taiga_hills", "biome.taigaHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.mountain_edge", "biome.extremeHillsEdge.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.jungle", "biome.jungle.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.jungle_hills", "biome.jungleHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.jungle_edge", "biome.jungleEdge.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.deep_ocean", "biome.deepOcean.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.stone_shore", "biome.stoneBeach.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.snowy_beach", "biome.coldBeach.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.birch_forest", "biome.birchForest.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.birch_forest_hills", "biome.birchForestHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.dark_forest", "biome.roofedForest.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.snowy_taiga", "biome.coldTaiga.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.snowy_taiga_hills", "biome.coldTaigaHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.giant_tree_taiga", "biome.megaTaiga.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.giant_tree_taiga_hills", "biome.megaTaigaHills.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.wooded_mountains", "biome.extremeHillsPlus.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.savanna", "biome.savanna.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.savanna_plateau", "biome.savannaPlateau.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.badlands", "biome.mesa.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.wooded_badlands_plateau", "biome.mesaPlateau_F.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.badlands_plateau", "biome.mesaPlateau.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.small_end_islands", "biome.sky.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.end_midlands", "biome.sky.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.end_highlands", "biome.sky.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.end_barrens", "biome.sky.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.the_void", "biome.void.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.sunflower_plains", "biome.plains.name");
        datarium$KEY_MAPPINGS.put("biome.minecraft.flower_forest", "biome.forest.name");

        // Attributes
        datarium$KEY_MAPPINGS.put("attribute.name.generic.max_health", "attribute.name.generic.maxHealth");
        datarium$KEY_MAPPINGS.put("attribute.name.generic.follow_range", "attribute.name.generic.followRange");
        datarium$KEY_MAPPINGS.put("attribute.name.generic.knockback_resistance", "attribute.name.generic.knockbackResistance");
        datarium$KEY_MAPPINGS.put("attribute.name.generic.movement_speed", "attribute.name.generic.movementSpeed");
        datarium$KEY_MAPPINGS.put("attribute.name.generic.attack_damage", "attribute.name.generic.attackDamage");
        datarium$KEY_MAPPINGS.put("attribute.name.generic.attack_speed", "attribute.name.generic.attackSpeed");
        datarium$KEY_MAPPINGS.put("attribute.name.generic.armor_toughness", "attribute.name.generic.armorToughness");

        // Creative Tabs
        datarium$KEY_MAPPINGS.put("itemGroup.coloredBlocks", "itemGroup.decorations");
        datarium$KEY_MAPPINGS.put("itemGroup.functional", "itemGroup.decorations");
        datarium$KEY_MAPPINGS.put("itemGroup.natural", "itemGroup.decorations");
        datarium$KEY_MAPPINGS.put("itemGroup.foodAndDrink", "itemGroup.food");
        datarium$KEY_MAPPINGS.put("itemGroup.ingredients", "itemGroup.materials");
        datarium$KEY_MAPPINGS.put("itemGroup.spawnEggs", "itemGroup.misc");
        datarium$KEY_MAPPINGS.put("itemGroup.op", "itemGroup.misc");
    }

    @Unique
    private String datarium$remapKey(String key) {
        return datarium$KEY_MAPPINGS.getOrDefault(key, key);
    }

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
                                    String remappedKey = datarium$remapKey(entry.getKey());
                                    this.properties.put(remappedKey, value);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }
}