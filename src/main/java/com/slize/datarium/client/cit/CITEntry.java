package com.slize.datarium.client.cit;

import com.slize.datarium.DatariumMain;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CITEntry {
    public enum MatchType {
        EXACT, PATTERN, IPATTERN, REGEX, IREGEX
    }

    private final ResourceLocation propertiesLocation;
    private final List<Item> items;
    private final ResourceLocation texture;
    private final ResourceLocation model;
    private final String nameMatch;
    private final MatchType nameMatchType;
    private final Pattern namePattern;
    private final int weight;
    private final Integer damage;
    private final Integer damageMin;
    private final Integer damageMax;
    private final Map<Enchantment, int[]> enchantments;

    public CITEntry(ResourceLocation propertiesLocation, List<Item> items,
                    @Nullable ResourceLocation texture, @Nullable ResourceLocation model,
                    @Nullable String nameMatch, MatchType nameMatchType,
                    int weight, @Nullable Integer damage,
                    @Nullable Integer damageMin, @Nullable Integer damageMax,
                    @Nullable Map<Enchantment, int[]> enchantments) {
        this.propertiesLocation = propertiesLocation;
        this.items = items;
        this.texture = texture;
        this.model = model;
        this.nameMatch = nameMatch;
        this.nameMatchType = nameMatchType;
        this.weight = weight;
        this.damage = damage;
        this.damageMin = damageMin;
        this.damageMax = damageMax;
        this.enchantments = enchantments;
        this.namePattern = compilePattern(nameMatch, nameMatchType);
    }

    @Nullable
    private static Pattern compilePattern(@Nullable String match, MatchType type) {
        if (match == null) return null;

        return switch (type) {
            case EXACT -> null;
            case PATTERN -> Pattern.compile(globToRegex(match));
            case IPATTERN -> Pattern.compile(globToRegex(match), Pattern.CASE_INSENSITIVE);
            case REGEX -> Pattern.compile(match);
            case IREGEX -> Pattern.compile(match, Pattern.CASE_INSENSITIVE);
        };
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> sb.append(".*");
                case '?' -> sb.append(".");
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}', '\\'
                        -> sb.append("\\").append(c);
                default -> sb.append(c);
            }
        }
        return sb.append("$").toString();
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) return false;

        if (!items.isEmpty() && !items.contains(stack.getItem())) {
            return false;
        }

        if (damage != null && stack.getItemDamage() != damage) {
            return false;
        }
        if (damageMin != null && stack.getItemDamage() < damageMin) {
            return false;
        }
        if (damageMax != null && stack.getItemDamage() > damageMax) {
            return false;
        }

        if (nameMatch != null) {
            if (!stack.hasDisplayName()) {
                return false;
            }
            String displayName = stack.getDisplayName();

            boolean nameMatches = switch (nameMatchType) {
                case EXACT -> displayName.equals(nameMatch);
                case PATTERN, IPATTERN, REGEX, IREGEX ->
                        namePattern != null && namePattern.matcher(displayName).matches();
            };
            if (!nameMatches) return false;
        }

        if (enchantments != null && !enchantments.isEmpty()) {
            return matchesEnchantments(stack);
        }

        return true;
    }

    private boolean matchesEnchantments(ItemStack stack) {
        if (!stack.hasTagCompound()) return false;

        NBTTagCompound tag = stack.getTagCompound();
        NBTTagList enchList = null;

        if (tag.hasKey("ench", 9)) {
            enchList = tag.getTagList("ench", 10);
        } else if (tag.hasKey("StoredEnchantments", 9)) {
            enchList = tag.getTagList("StoredEnchantments", 10);
        }

        if (enchList == null) return false;

        for (Map.Entry<Enchantment, int[]> entry : enchantments.entrySet()) {
            Enchantment reqEnch = entry.getKey();
            int[] levelRange = entry.getValue();

            boolean found = false;
            for (int i = 0; i < enchList.tagCount(); i++) {
                NBTTagCompound enchTag = enchList.getCompoundTagAt(i);
                int id = enchTag.getShort("id");
                int lvl = enchTag.getShort("lvl");

                Enchantment ench = Enchantment.getEnchantmentByID(id);
                if (ench == reqEnch) {
                    if (levelRange.length == 1) {
                        found = lvl == levelRange[0];
                    } else if (levelRange.length == 2) {
                        found = lvl >= levelRange[0] && lvl <= levelRange[1];
                    }
                    break;
                }
            }

            if (!found) return false;
        }

        return true;
    }

    public ResourceLocation getTexture() { return texture; }
    public ResourceLocation getModel() { return model; }
    public int getWeight() { return weight; }
    public ResourceLocation getPropertiesLocation() { return propertiesLocation; }
    public List<Item> getItems() { return items; }
}