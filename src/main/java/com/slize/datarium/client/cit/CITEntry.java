package com.slize.datarium.client.cit;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;


public record CITEntry(ResourceLocation propertiesLocation, CITType citType, List<Item> items, ResourceLocation texture,
                       ResourceLocation model, Map<String, ResourceLocation> subTextures,
                       Map<String, ResourceLocation> subModels, int weight, Integer damage, Integer damageMin,
                       Integer damageMax, boolean damagePercent, Integer damageMask, Integer stackSize,
                       Integer stackSizeMin, Integer stackSizeMax, Hand hand, Map<Enchantment, int[]> enchantments,
                       List<NBTCondition> nbtConditions, int glintLayer, float glintSpeed, float glintRotation,
                       float glintR, float glintG, float glintB, float glintA, boolean glintBlur, boolean glintUseGlint,
                       String glintBlend) {

    public enum MatchType {
        EXACT, PATTERN, IPATTERN, REGEX, IREGEX
    }

    public enum CITType {
        ITEM, ARMOR, ELYTRA, ENCHANTMENT
    }

    public enum Hand {
        ANY, MAIN, OFF
    }

    public static class NBTCondition {
        public final String path;
        public final String rawValue;
        public final MatchType matchType;
        public final String matchValue;
        public final Pattern pattern;

        public NBTCondition(String path, String rawValue) {
            this.path = path;
            this.rawValue = rawValue;

            MatchType mt = MatchType.EXACT;
            String mv = rawValue;
            if (rawValue.startsWith("ipattern:")) {
                mt = MatchType.IPATTERN;
                mv = rawValue.substring("ipattern:".length());
            } else if (rawValue.startsWith("pattern:")) {
                mt = MatchType.PATTERN;
                mv = rawValue.substring("pattern:".length());
            } else if (rawValue.startsWith("iregex:")) {
                mt = MatchType.IREGEX;
                mv = rawValue.substring("iregex:".length());
            } else if (rawValue.startsWith("regex:")) {
                mt = MatchType.REGEX;
                mv = rawValue.substring("regex:".length());
            }
            this.matchType = mt;
            this.matchValue = mv;

            Pattern p = null;
            if (mt == MatchType.PATTERN) p = Pattern.compile(globToRegex(mv));
            else if (mt == MatchType.IPATTERN) p = Pattern.compile(globToRegex(mv), Pattern.CASE_INSENSITIVE);
            else if (mt == MatchType.REGEX) p = Pattern.compile(mv);
            else if (mt == MatchType.IREGEX) p = Pattern.compile(mv, Pattern.CASE_INSENSITIVE);
            this.pattern = p;
        }

        private static String globToRegex(String glob) {
            StringBuilder sb = new StringBuilder("^");
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                switch (c) {
                    case '*' -> sb.append(".*");
                    case '?' -> sb.append(".");
                    case '.', '(', ')', '+', '|', '^', '$', '@', '%', '[', ']', '{', '}', '\\' -> sb.append("\\").append(c);
                    default -> sb.append(c);
                }
            }
            return sb.append("$").toString();
        }
    }

    public CITEntry(ResourceLocation propertiesLocation, CITType citType, List<Item> items,
                    @Nullable ResourceLocation texture, @Nullable ResourceLocation model,
                    Map<String, ResourceLocation> subTextures, Map<String, ResourceLocation> subModels,
                    int weight,
                    @Nullable Integer damage, @Nullable Integer damageMin, @Nullable Integer damageMax,
                    boolean damagePercent, @Nullable Integer damageMask,
                    @Nullable Integer stackSize, @Nullable Integer stackSizeMin, @Nullable Integer stackSizeMax,
                    Hand hand,
                    @Nullable Map<Enchantment, int[]> enchantments,
                    List<NBTCondition> nbtConditions,
                    int glintLayer, float glintSpeed, float glintRotation,
                    float glintR, float glintG, float glintB, float glintA,
                    boolean glintBlur, boolean glintUseGlint, String glintBlend) {
        this.propertiesLocation = propertiesLocation;
        this.citType = citType;
        this.items = items;
        this.texture = texture;
        this.model = model;
        this.subTextures = subTextures;
        this.subModels = subModels;
        this.weight = weight;
        this.damage = damage;
        this.damageMin = damageMin;
        this.damageMax = damageMax;
        this.damagePercent = damagePercent;
        this.damageMask = damageMask;
        this.stackSize = stackSize;
        this.stackSizeMin = stackSizeMin;
        this.stackSizeMax = stackSizeMax;
        this.hand = hand;
        this.enchantments = enchantments;
        this.nbtConditions = nbtConditions;
        this.glintLayer = glintLayer;
        this.glintSpeed = glintSpeed;
        this.glintRotation = glintRotation;
        this.glintA = glintA;
        this.glintR = glintR;
        this.glintG = glintG;
        this.glintB = glintB;
        this.glintBlur = glintBlur;
        this.glintUseGlint = glintUseGlint;
        this.glintBlend = glintBlend;
    }

    public boolean matches(ItemStack stack) {
        return matches(stack, Hand.ANY);
    }

    public boolean matches(ItemStack stack, Hand heldHand) {
        if (stack.isEmpty()) return false;

        if (!items.isEmpty() && !items.contains(stack.getItem())) return false;

        // hand check
        if (hand != Hand.ANY && hand != heldHand) return false;

        // stackSize
        int count = stack.getCount();
        if (stackSize != null && count != stackSize) return false;
        if (stackSizeMin != null && count < stackSizeMin) return false;
        if (stackSizeMax != null && count > stackSizeMax) return false;

        // damage
        int rawDmg = stack.getItemDamage();
        int maskedDmg = damageMask != null ? (rawDmg & damageMask) : rawDmg;
        if (damagePercent) {
            int maxDmg = stack.getMaxDamage();
            if (maxDmg > 0) {
                int pct = (maskedDmg * 100) / maxDmg;
                if (damage != null && pct != damage) return false;
                if (damageMin != null && pct < damageMin) return false;
                if (damageMax != null && pct > damageMax) return false;
            }
        } else {
            if (damage != null && maskedDmg != damage) return false;
            if (damageMin != null && maskedDmg < damageMin) return false;
            if (damageMax != null && maskedDmg > damageMax) return false;
        }

        // nbt conditions
        for (NBTCondition cond : nbtConditions) {
            if (!matchesNBTFull(stack, cond)) return false;
        }

        // enchantments
        if (enchantments != null && !enchantments.isEmpty()) {
            return matchesEnchantments(stack);
        }

        return true;
    }

    private boolean matchesNBT(ItemStack stack, NBTCondition cond) {
        if (!stack.hasTagCompound()) return false;
        NBTBase nbt = resolveNBTPath(stack.getTagCompound(), cond.path);
        if (nbt == null) return false;

        String strVal = nbtToString(nbt, cond.path);
        return matchString(strVal, cond);
    }

    @Nullable
    private NBTBase resolveNBTPath(NBTTagCompound root, String path) {
        String[] parts = path.split("\\.");
        NBTBase current = root;
        for (String part : parts) {
            if (current instanceof NBTTagCompound compound) {
                if (part.equals("*")) {
                    // wildcard in compound: return first found (handled in matchesNBTWildcard)
                    return null;
                }
                current = compound.getTag(part);
                if (current == null) return null;
            } else if (current instanceof NBTTagList list) {
                if (part.equals("*")) return null; // wildcards handled separately
                try {
                    int idx = Integer.parseInt(part);
                    if (idx < 0 || idx >= list.tagCount()) return null;
                    current = list.get(idx);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    private String nbtToString(NBTBase nbt, String path) {
        String raw = nbt.toString();
        // For display.Name and display.Lore.*, try to extract plain text from JSON component
        if (path.equals("display.Name") || path.startsWith("display.Lore.")) {
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                raw = raw.substring(1, raw.length() - 1).replace("\\\"", "\"");
            }
            try {
                ITextComponent comp = ITextComponent.Serializer.jsonToComponent(raw);
                if (comp != null) return comp.getUnformattedComponentText();
            } catch (Exception ignored) {
            }
        }
        // Strip surrounding quotes for NBTTagString
        if (nbt instanceof NBTTagString nbtstr) {
            return nbtstr.getString();
        }
        return raw;
    }

    private boolean matchString(String value, NBTCondition cond) {
        return switch (cond.matchType) {
            case EXACT -> value.equals(cond.matchValue);
            case PATTERN, IPATTERN, REGEX, IREGEX -> cond.pattern != null && cond.pattern.matcher(value).matches();
        };
    }

    private boolean matchesNBTWildcard(ItemStack stack, NBTCondition cond) {
        if (!stack.hasTagCompound()) return false;
        String[] parts = cond.path.split("\\.");
        return matchWildcardRecursive(stack.getTagCompound(), parts, 0, cond);
    }

    private boolean matchWildcardRecursive(NBTBase current, String[] parts, int idx, NBTCondition cond) {
        if (idx == parts.length) {
            if (current == null) return false;
            String strVal = current instanceof NBTTagString nbtstr ? nbtstr.getString() : current.toString();
            return matchString(strVal, cond);
        }
        String part = parts[idx];
        if (part.equals("*")) {
            if (current instanceof NBTTagCompound compound) {
                for (String key : compound.getKeySet()) {
                    if (matchWildcardRecursive(compound.getTag(key), parts, idx + 1, cond)) return true;
                }
            } else if (current instanceof NBTTagList list) {
                for (int i = 0; i < list.tagCount(); i++) {
                    if (matchWildcardRecursive(list.get(i), parts, idx + 1, cond)) return true;
                }
            }
        } else {
            if (current instanceof NBTTagCompound compound) {
                NBTBase next = compound.getTag(part);
                return next != null && matchWildcardRecursive(next, parts, idx + 1, cond);
            } else if (current instanceof NBTTagList list) {
                try {
                    int i = Integer.parseInt(part);
                    if (i >= 0 && i < list.tagCount())
                        return matchWildcardRecursive(list.get(i), parts, idx + 1, cond);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return false;
    }

    // Override matches to route wildcard nbt correctly
    // (call this version from matchesNBT above):
    // Actually integrate wildcard into matchesNBT:
    private boolean matchesNBTFull(ItemStack stack, NBTCondition cond) {
        if (cond.path.contains("*")) return matchesNBTWildcard(stack, cond);
        return matchesNBT(stack, cond);
    }

    private boolean matchesEnchantments(ItemStack stack) {
        if (!stack.hasTagCompound()) return false;
        NBTTagCompound tag = stack.getTagCompound();
        NBTTagList enchList = null;
        if (tag.hasKey("ench", 9)) enchList = tag.getTagList("ench", 10);
        else if (tag.hasKey("StoredEnchantments", 9)) enchList = tag.getTagList("StoredEnchantments", 10);
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
                    found = levelRange.length == 1 ? lvl == levelRange[0]
                            : lvl >= levelRange[0] && lvl <= levelRange[1];
                    break;
                }
            }
            if (!found) return false;
        }
        return true;
    }

    public CITType getCITType() {
        return citType;
    }
}