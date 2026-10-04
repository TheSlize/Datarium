package com.slize.datarium.client.cit;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Pattern;

public record CITEntry(ResourceLocation propertiesLocation, CITType citType, Map<Item, Set<Integer>> items,
                       @Nullable ResourceLocation texture, @Nullable ResourceLocation model,
                       Map<String, ResourceLocation> subTextures, Map<String, ResourceLocation> subModels,
                       int weight, int layer, @Nullable CITRangeList damage, boolean damagePercent, int damageMask,
                       @Nullable CITRangeList stackSize, @Nullable CITRangeList enchantmentIds,
                       @Nullable CITRangeList enchantmentLevels, List<NBTCondition> nbtConditions, Hand hand,
                       Blend blend, float speed, float rotation, float duration,
                       float glintR, float glintG, float glintB, float glintA, boolean glintBlur, boolean glintUseGlint) {

    public enum MatchType {
        EXACT, PATTERN, IPATTERN, REGEX, IREGEX
    }

    public enum CITType {
        ITEM, ARMOR, ELYTRA, ENCHANTMENT
    }

    public enum Hand {
        ANY, MAIN, OFF
    }

    public enum Blend {
        ALPHA, ADD, SUBTRACT, MULTIPLY, DODGE, BURN, SCREEN, OVERLAY, REPLACE, GLINT;

        public static Blend parse(@Nullable String str) {
            if (str == null) return ADD;
            return switch (str.trim().toLowerCase(Locale.ROOT)) {
                case "alpha" -> ALPHA;
                case "subtract" -> SUBTRACT;
                case "multiply" -> MULTIPLY;
                case "dodge" -> DODGE;
                case "burn" -> BURN;
                case "screen" -> SCREEN;
                case "overlay" -> OVERLAY;
                case "replace" -> REPLACE;
                case "glint" -> GLINT;
                default -> ADD;
            };
        }
    }

    public static final class NBTCondition {
        private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9a-f]{6}$");

        public final String path;
        public final String rawValue;
        public final MatchType matchType;
        public final String matchValue;
        private final String[] parts;
        private final boolean negative;
        private final boolean hexColor;
        private final boolean displayText;
        @Nullable
        private final Pattern pattern;

        public NBTCondition(String path, String rawValue) {
            this.path = path;
            this.rawValue = rawValue;
            this.parts = Arrays.stream(path.split("\\.")).filter(s -> !s.isEmpty()).toArray(String[]::new);
            this.displayText = path.equals("display.Name") || path.startsWith("display.Lore");

            String value = rawValue;
            boolean neg = false;
            if (value.startsWith("!")) {
                neg = true;
                value = value.substring(1);
            }
            MatchType type = MatchType.EXACT;
            if (value.startsWith("pattern:")) {
                type = MatchType.PATTERN;
                value = value.substring("pattern:".length());
            } else if (value.startsWith("ipattern:")) {
                type = MatchType.IPATTERN;
                value = value.substring("ipattern:".length());
            } else if (value.startsWith("regex:")) {
                type = MatchType.REGEX;
                value = value.substring("regex:".length());
            } else if (value.startsWith("iregex:")) {
                type = MatchType.IREGEX;
                value = value.substring("iregex:".length());
            }
            value = unescape(value);

            this.negative = neg;
            this.matchType = type;
            this.matchValue = value;
            this.hexColor = type == MatchType.EXACT && HEX_COLOR.matcher(value).matches();
            int ci = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            this.pattern = switch (type) {
                case PATTERN -> Pattern.compile(globToRegex(value), Pattern.DOTALL);
                case IPATTERN -> Pattern.compile(globToRegex(value), Pattern.DOTALL | ci);
                case REGEX -> Pattern.compile(value);
                case IREGEX -> Pattern.compile(value, ci);
                case EXACT -> null;
            };
        }

        public boolean matches(@Nullable NBTTagCompound root) {
            boolean result = root != null && parts.length > 0 && matchesAt(root, 0);
            return negative != result;
        }

        private boolean matchesAt(NBTBase node, int idx) {
            if (idx == parts.length) return matchesBase(node);
            String part = parts[idx];
            if (part.equals("*")) {
                if (node instanceof NBTTagCompound compound) {
                    for (String key : compound.getKeySet()) {
                        if (matchesAt(compound.getTag(key), idx + 1)) return true;
                    }
                } else if (node instanceof NBTTagList list) {
                    for (int i = 0; i < list.tagCount(); i++) {
                        if (matchesAt(list.get(i), idx + 1)) return true;
                    }
                }
                return false;
            }
            NBTBase child = getChild(node, part);
            return child != null && matchesAt(child, idx + 1);
        }

        @Nullable
        private static NBTBase getChild(NBTBase node, String part) {
            if (node instanceof NBTTagCompound compound) {
                return compound.getTag(part);
            }
            if (node instanceof NBTTagList list) {
                if (part.equals("count")) return new NBTTagInt(list.tagCount());
                try {
                    int index = Integer.parseInt(part);
                    return index >= 0 && index < list.tagCount() ? list.get(index) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }

        private boolean matchesBase(@Nullable NBTBase nbt) {
            if (nbt == null) return false;
            if (nbt instanceof NBTTagString str) {
                String s = str.getString();
                if (matchesValue(s)) return true;
                if (displayText) {
                    String plain = jsonText(s);
                    if (plain != null && matchesValue(plain)) return true;
                    String stripped = TextFormatting.getTextWithoutFormattingCodes(plain != null ? plain : s);
                    return stripped != null && !stripped.equals(s) && matchesValue(stripped);
                }
                return false;
            }
            if (matchesValue(nbtString(nbt))) return true;
            return nbt instanceof NBTPrimitive && matchesValue(nbt.toString());
        }

        private boolean matchesValue(String value) {
            return pattern == null ? value.equals(matchValue) : pattern.matcher(value).matches();
        }

        private String nbtString(NBTBase nbt) {
            if (nbt instanceof NBTTagInt i) {
                if (!hexColor) return Integer.toString(i.getInt());
                String hex = Integer.toHexString(i.getInt());
                return "#" + "0".repeat(Math.max(0, 6 - hex.length())) + hex;
            }
            if (nbt instanceof NBTTagByte b) return Byte.toString(b.getByte());
            if (nbt instanceof NBTTagShort s) return Short.toString(s.getShort());
            if (nbt instanceof NBTTagLong l) return Long.toString(l.getLong());
            if (nbt instanceof NBTTagFloat f) return Float.toString(f.getFloat());
            if (nbt instanceof NBTTagDouble d) return Double.toString(d.getDouble());
            return nbt.toString();
        }

        @Nullable
        private static String jsonText(String s) {
            if (s.isEmpty()) return null;
            char c = s.charAt(0);
            if (c != '{' && c != '[' && c != '"') return null;
            try {
                ITextComponent component = ITextComponent.Serializer.jsonToComponent(s);
                return component == null ? null : component.getUnformattedText();
            } catch (Exception e) {
                return null;
            }
        }

        private static String globToRegex(String glob) {
            StringBuilder sb = new StringBuilder();
            StringBuilder literal = new StringBuilder();
            for (int i = 0; i < glob.length(); i++) {
                char c = glob.charAt(i);
                if (c == '*' || c == '?') {
                    if (!literal.isEmpty()) {
                        sb.append(Pattern.quote(literal.toString()));
                        literal.setLength(0);
                    }
                    sb.append(c == '*' ? ".*" : ".");
                } else {
                    literal.append(c);
                }
            }
            if (!literal.isEmpty()) sb.append(Pattern.quote(literal.toString()));
            return sb.toString();
        }

        private static String unescape(String s) {
            if (s.indexOf('\\') < 0) return s;
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c != '\\' || i + 1 >= s.length()) {
                    sb.append(c);
                    continue;
                }
                char n = s.charAt(i + 1);
                switch (n) {
                    case '\\' -> { sb.append('\\'); i++; }
                    case 'n' -> { sb.append('\n'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 'b' -> { sb.append('\b'); i++; }
                    case 'f' -> { sb.append('\f'); i++; }
                    case '"', '\'' -> { sb.append(n); i++; }
                    case 'u' -> {
                        int code = i + 6 <= s.length() ? parseHex(s.substring(i + 2, i + 6)) : -1;
                        if (code >= 0) {
                            sb.append((char) code);
                            i += 5;
                        } else {
                            sb.append(c);
                        }
                    }
                    default -> sb.append(c);
                }
            }
            return sb.toString();
        }

        private static int parseHex(String s) {
            try {
                return Integer.parseInt(s, 16);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    public boolean matches(ItemStack stack, boolean offHand) {
        if (stack.isEmpty()) return false;

        if (!items.isEmpty()) {
            Set<Integer> metas = items.get(stack.getItem());
            if (metas == null) return false;
            if (!metas.isEmpty() && !metas.contains(stack.getMetadata())) return false;
        }

        if (hand == Hand.MAIN && offHand) return false;
        if (hand == Hand.OFF && !offHand) return false;

        if (damage != null) {
            int dmg = CITPotions.getItemDamage(stack);
            if (dmg < 0) return false;
            if (damageMask != 0) dmg &= damageMask;
            if (damagePercent) {
                int max = stack.getMaxDamage();
                if (max <= 0) return false;
                dmg = (int) (dmg * 100.0 / max);
            }
            if (!damage.contains(dmg)) return false;
        }

        if (stackSize != null && !stackSize.contains(stack.getCount())) return false;

        if ((enchantmentIds != null || enchantmentLevels != null) && enchantmentLevel(stack) < 0) return false;

        if (!nbtConditions.isEmpty()) {
            NBTTagCompound tag = stack.getTagCompound();
            for (NBTCondition cond : nbtConditions) {
                if (!cond.matches(tag)) return false;
            }
        }

        return true;
    }

    public int enchantmentLevel(ItemStack stack) {
        NBTTagList list = enchantmentList(stack);
        int best = -1;
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int id = tag.getShort("id");
            int lvl = tag.getShort("lvl");
            if (enchantmentIds != null && !enchantmentIds.contains(id)) continue;
            if (enchantmentLevels != null && !enchantmentLevels.contains(lvl)) continue;
            best = Math.max(best, lvl);
        }
        return best;
    }

    public static NBTTagList enchantmentList(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return new NBTTagList();
        if (stack.getItem() == Items.ENCHANTED_BOOK) return tag.getTagList("StoredEnchantments", 10);
        NBTTagList list = tag.getTagList("ench", 10);
        return list.tagCount() == 0 ? tag.getTagList("StoredEnchantments", 10) : list;
    }
}
