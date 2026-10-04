package com.slize.datarium.client.cit;

import com.slize.datarium.mixin.accessors.IAbstractResourcePackAccessor;
import com.slize.datarium.util.PackConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CITManager {

    public enum AssetKind {
        TEXTURE(".png", "textures/"), MODEL(".json", "models/");

        final String extension;
        final String directory;

        AssetKind(String extension, String directory) {
            this.extension = extension;
            this.directory = directory;
        }
    }

    private record MatchKey(Item item, int meta, int count, boolean offHand, CITEntry.CITType type,
                            @Nullable NBTTagCompound nbt) {
    }

    private static final Pattern POTION_IMAGE = Pattern.compile(
            "^assets/([^/]+)/(?:optifine|mcpatcher)/cit/potion/(normal|splash|linger)/([^/]+)\\.png$",
            Pattern.CASE_INSENSITIVE);
    private static final Set<Integer> ANY_META = Collections.emptySet();
    private static final int MAX_CACHE_SIZE = 8192;

    private static final List<CITEntry> entries = new ArrayList<>();
    private static final Map<MatchKey, List<CITEntry>> matchCache = new HashMap<>();
    private static final Map<ResourceLocation, Boolean> existsCache = new HashMap<>();
    private static boolean loaded = false;
    private static boolean renderOffHand = false;

    public static void setRenderOffHand(boolean offHand) {
        renderOffHand = offHand;
    }

    public static void reload() {
        entries.clear();
        matchCache.clear();
        existsCache.clear();
        CITGlintRenderer.clearCache();
        loaded = true;

        Map<ResourceLocation, byte[]> propertyFiles = new LinkedHashMap<>();
        Map<ResourceLocation, String[]> potionImages = new LinkedHashMap<>();
        for (IResourcePack pack : collectPacks()) {
            scanPack(pack, propertyFiles, potionImages);
        }

        for (Map.Entry<ResourceLocation, byte[]> e : propertyFiles.entrySet()) {
            try {
                Properties props = new Properties();
                props.load(new InputStreamReader(new ByteArrayInputStream(e.getValue()), StandardCharsets.UTF_8));
                CITEntry entry = parseEntry(e.getKey(), props);
                if (entry != null) entries.add(entry);
            } catch (Exception ignored) {
            }
        }

        for (Map.Entry<ResourceLocation, String[]> e : potionImages.entrySet()) {
            ResourceLocation png = e.getKey();
            String path = png.getPath();
            ResourceLocation propsLoc = new ResourceLocation(png.getNamespace(), path.substring(0, path.length() - 4) + ".properties");
            if (propertyFiles.containsKey(propsLoc)) continue;
            Properties props = CITPotions.makeImageProperties(e.getValue()[0], e.getValue()[1]);
            if (props == null) continue;
            try {
                CITEntry entry = parseEntry(propsLoc, props);
                if (entry != null) entries.add(entry);
            } catch (Exception ignored) {
            }
        }

        entries.sort(Comparator.comparingInt(CITEntry::weight).reversed()
                .thenComparing(e -> parentPath(e.propertiesLocation()))
                .thenComparing(e -> e.propertiesLocation().getPath()));
    }

    private static List<IResourcePack> collectPacks() {
        List<IResourcePack> packs = new ArrayList<>();
        ResourcePackRepository repo = Minecraft.getMinecraft().getResourcePackRepository();
        if (repo.rprDefaultResourcePack != null) packs.add(repo.rprDefaultResourcePack);
        for (ResourcePackRepository.Entry entry : repo.getRepositoryEntries()) {
            packs.add(entry.getResourcePack());
        }
        return packs;
    }

    private static void scanPack(IResourcePack pack, Map<ResourceLocation, byte[]> propertyFiles, Map<ResourceLocation, String[]> potionImages) {
        if (!(pack instanceof FileResourcePack) && !(pack instanceof FolderResourcePack)) return;
        File file;
        try {
            file = ((IAbstractResourcePackAccessor) pack).datarium$getResourcePackFileField();
        } catch (Exception e) {
            return;
        }
        if (file == null) return;

        if (pack instanceof FileResourcePack) {
            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> zipEntries = zip.entries();
                while (zipEntries.hasMoreElements()) {
                    ZipEntry entry = zipEntries.nextElement();
                    if (entry.isDirectory()) continue;
                    String name = entry.getName();
                    if (isCITPropertiesPath(name)) {
                        ResourceLocation loc = pathToResourceLocation(name);
                        if (loc == null) continue;
                        try (InputStream is = zip.getInputStream(entry)) {
                            propertyFiles.put(loc, is.readAllBytes());
                        } catch (IOException ignored) {
                        }
                    } else {
                        collectPotionImage(name, potionImages);
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            Path root = file.toPath();
            Path assets = root.resolve("assets");
            if (!Files.isDirectory(assets)) return;
            try (Stream<Path> walk = Files.walk(assets)) {
                walk.filter(Files::isRegularFile).forEach(path -> {
                    String name = root.relativize(path).toString().replace('\\', '/');
                    if (isCITPropertiesPath(name)) {
                        ResourceLocation loc = pathToResourceLocation(name);
                        if (loc == null) return;
                        try {
                            propertyFiles.put(loc, Files.readAllBytes(path));
                        } catch (IOException ignored) {
                        }
                    } else {
                        collectPotionImage(name, potionImages);
                    }
                });
            } catch (Exception ignored) {
            }
        }
    }

    private static void collectPotionImage(String name, Map<ResourceLocation, String[]> potionImages) {
        if (!name.endsWith(".png") && !name.endsWith(".PNG")) return;
        Matcher m = POTION_IMAGE.matcher(name);
        if (!m.matches()) return;
        ResourceLocation loc = pathToResourceLocation(name);
        if (loc != null) {
            potionImages.put(loc, new String[]{m.group(2).toLowerCase(Locale.ROOT), m.group(3).toLowerCase(Locale.ROOT)});
        }
    }

    private static boolean isCITPropertiesPath(String path) {
        return path.endsWith(".properties") && (path.contains("/optifine/cit/")
                || path.contains("/citresewn/cit/")
                || path.contains("/mcpatcher/cit/"));
    }

    @Nullable
    private static ResourceLocation pathToResourceLocation(String path) {
        if (!path.startsWith("assets/")) return null;
        String afterAssets = path.substring("assets/".length());
        int slashIdx = afterAssets.indexOf('/');
        if (slashIdx <= 0) return null;
        return new ResourceLocation(afterAssets.substring(0, slashIdx), afterAssets.substring(slashIdx + 1));
    }

    private static String parentPath(ResourceLocation loc) {
        String path = loc.getPath();
        int slash = path.lastIndexOf('/');
        return loc.getNamespace() + ":" + (slash < 0 ? "" : path.substring(0, slash));
    }

    @Nullable
    private static CITEntry parseEntry(ResourceLocation location, Properties props) {
        String path = location.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.endsWith(".properties")) name = name.substring(0, name.length() - ".properties".length());

        CITEntry.CITType type = switch (props.getProperty("type", "item").trim().toLowerCase(Locale.ROOT)) {
            case "item" -> CITEntry.CITType.ITEM;
            case "armor" -> CITEntry.CITType.ARMOR;
            case "elytra" -> CITEntry.CITType.ELYTRA;
            case "enchantment" -> CITEntry.CITType.ENCHANTMENT;
            default -> null;
        };
        if (type == null) return null;

        int format = PackConverter.formatOf(location);
        String itemsStr = props.getProperty("items", props.getProperty("matchItems"));
        Map<Item, Set<Integer>> items = new LinkedHashMap<>();
        if (itemsStr != null) {
            parseItems(itemsStr, items, format);
            if (items.isEmpty()) return null;
        } else if (type == CITEntry.CITType.ELYTRA) {
            items.put(Items.ELYTRA, ANY_META);
        } else if (type != CITEntry.CITType.ENCHANTMENT) {
            addItem(name, items, format);
            if (items.isEmpty()) return null;
        }

        Map<String, ResourceLocation> subModels = parseSubAssets(props, "model.", location, AssetKind.MODEL);
        String modelStr = props.getProperty("model");
        ResourceLocation model = null;
        if (modelStr != null && !modelStr.trim().isEmpty()) {
            model = resolveAsset(location, modelStr, AssetKind.MODEL);
        } else if (type != CITEntry.CITType.ARMOR) {
            model = subModels.get("bow_standby");
        }

        Map<String, ResourceLocation> subTextures = parseSubAssets(props, "texture.", location, AssetKind.TEXTURE);
        String textureStr = props.getProperty("texture", props.getProperty("tile", props.getProperty("source")));
        ResourceLocation texture = null;
        if (textureStr != null && !textureStr.trim().isEmpty()) {
            texture = resolveAsset(location, textureStr, AssetKind.TEXTURE);
        } else if (type != CITEntry.CITType.ARMOR) {
            texture = subTextures.get("bow_standby");
            if (texture == null && modelStr == null && subModels.isEmpty()) {
                texture = resolveAsset(location, "./" + name, AssetKind.TEXTURE);
            }
        }

        if (texture == null && model == null && subTextures.isEmpty() && subModels.isEmpty()) return null;
        if (type == CITEntry.CITType.ENCHANTMENT && texture == null) return null;

        CITRangeList damage = null;
        boolean damagePercent = false;
        int damageMask = 0;
        String damageStr = props.getProperty("damage");
        if (damageStr != null) {
            damagePercent = damageStr.contains("%");
            damage = CITRangeList.parse(damageStr.replace("%", ""));
            if (damage == null) return null;
            damageMask = parseInt(props.getProperty("damageMask"), 0);
        }

        String stackStr = props.getProperty("stackSize");
        CITRangeList stackSize = CITRangeList.parse(stackStr);
        if (stackStr != null && stackSize == null) return null;

        String enchIdsStr = props.getProperty("enchantmentIDs", props.getProperty("enchantments"));
        CITRangeList enchantmentIds = CITRangeList.parse(enchIdsStr, CITManager::enchantmentId);
        if (enchIdsStr != null && enchantmentIds == null) return null;

        String enchLevelsStr = props.getProperty("enchantmentLevels");
        CITRangeList enchantmentLevels = CITRangeList.parse(enchLevelsStr);
        if (enchLevelsStr != null && enchantmentLevels == null) return null;

        List<CITEntry.NBTCondition> nbtConditions = new ArrayList<>();
        for (String key : new TreeSet<>(props.stringPropertyNames())) {
            if (key.startsWith("nbt.") && key.length() > "nbt.".length()) {
                nbtConditions.add(new CITEntry.NBTCondition(key.substring("nbt.".length()), props.getProperty(key)));
            }
        }

        CITEntry.Hand hand = switch (props.getProperty("hand", "any").trim().toLowerCase(Locale.ROOT)) {
            case "main" -> CITEntry.Hand.MAIN;
            case "off" -> CITEntry.Hand.OFF;
            default -> CITEntry.Hand.ANY;
        };

        return new CITEntry(location, type, items, texture, model, subTextures, subModels,
                parseInt(props.getProperty("weight"), 0),
                parseInt(props.getProperty("layer"), 0),
                damage, damagePercent, damageMask, stackSize, enchantmentIds, enchantmentLevels,
                nbtConditions, hand,
                CITEntry.Blend.parse(props.getProperty("blend")),
                parseFloat(props.getProperty("speed"), 0f),
                parseFloat(props.getProperty("rotation"), 0f),
                parseFloat(props.getProperty("duration"), 1f),
                parseFloat(props.getProperty("r"), 1f),
                parseFloat(props.getProperty("g"), 1f),
                parseFloat(props.getProperty("b"), 1f),
                parseFloat(props.getProperty("a"), 1f),
                Boolean.parseBoolean(props.getProperty("blur", "false").trim()),
                Boolean.parseBoolean(props.getProperty("useGlint", "false").trim()));
    }

    private static void parseItems(String str, Map<Item, Set<Integer>> items, int format) {
        for (String token : str.trim().split("\\s+")) {
            if (token.isEmpty()) continue;
            if (token.matches("\\d+(-\\d+)?")) {
                String[] parts = token.split("-");
                try {
                    int a = Integer.parseInt(parts[0]);
                    int b = parts.length > 1 ? Integer.parseInt(parts[1]) : a;
                    for (int id = Math.min(a, b); id <= Math.max(a, b); id++) {
                        Item item = Item.getItemById(id);
                        if (item != null && item != Items.AIR) items.put(item, ANY_META);
                    }
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            addItem(token, items, format);
        }
    }

    private static void addItem(String token, Map<Item, Set<Integer>> items, int format) {
        String id = token.indexOf(':') >= 0 ? token : "minecraft:" + token;
        Integer meta = null;
        PackConverter.LegacyItem legacy = PackConverter.legacyItem(id, format);
        if (legacy != null) {
            id = legacy.id();
            meta = legacy.meta();
        }
        Item item = Item.getByNameOrId(id);
        if (item == null || item == Items.AIR) return;
        Set<Integer> metas = items.get(item);
        if (meta == null) {
            items.put(item, ANY_META);
        } else if (metas == null) {
            Set<Integer> set = new HashSet<>();
            set.add(meta);
            items.put(item, set);
        } else if (!metas.isEmpty()) {
            metas.add(meta);
        }
    }

    private static int enchantmentId(String token) {
        Enchantment ench = Enchantment.getEnchantmentByLocation(token);
        return ench == null ? Integer.MIN_VALUE : Enchantment.getEnchantmentID(ench);
    }

    private static Map<String, ResourceLocation> parseSubAssets(Properties props, String prefix, ResourceLocation location, AssetKind kind) {
        Map<String, ResourceLocation> result = new LinkedHashMap<>();
        for (String key : new TreeSet<>(props.stringPropertyNames())) {
            if (!key.startsWith(prefix) || key.length() <= prefix.length()) continue;
            String value = props.getProperty(key).trim();
            if (value.isEmpty()) continue;
            ResourceLocation resolved = resolveAsset(location, value, kind);
            if (resolved != null) result.put(key.substring(prefix.length()), resolved);
        }
        return result;
    }

    private static int parseInt(@Nullable String str, int def) {
        if (str == null) return def;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float parseFloat(@Nullable String str, float def) {
        if (str == null) return def;
        try {
            return Float.parseFloat(str.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public static boolean exists(ResourceLocation loc) {
        return existsCache.computeIfAbsent(loc, l -> {
            try (IResource ignored = Minecraft.getMinecraft().getResourceManager().getResource(l)) {
                return true;
            } catch (Exception e) {
                return false;
            }
        });
    }

    @Nullable
    public static ResourceLocation resolveAsset(ResourceLocation base, String raw, AssetKind kind) {
        for (ResourceLocation candidate : assetCandidates(base, raw, kind)) {
            if (exists(candidate)) return candidate;
        }
        return null;
    }

    private static Collection<ResourceLocation> assetCandidates(ResourceLocation base, String raw, AssetKind kind) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        String p = raw.trim().replace('\\', '/');
        if (p.endsWith(kind.extension)) p = p.substring(0, p.length() - kind.extension.length());
        if (p.isEmpty()) return out;

        String ns = base.getNamespace();
        String basePath = base.getPath();
        int slash = basePath.lastIndexOf('/');
        String dir = slash >= 0 ? basePath.substring(0, slash + 1) : "";

        if (p.startsWith("assets/")) {
            String rest = p.substring("assets/".length());
            int s = rest.indexOf('/');
            if (s > 0) addCandidate(out, rest.substring(0, s), rest.substring(s + 1), kind);
        } else if (p.startsWith("./") || p.startsWith("../")) {
            addCandidate(out, ns, dir + p, kind);
        } else if (p.startsWith("~/") || p.startsWith("/~/")) {
            String rest = p.substring(p.indexOf("~/") + 2);
            addCandidate(out, ns, "optifine/" + rest, kind);
            addCandidate(out, ns, "mcpatcher/" + rest, kind);
            addCandidate(out, "minecraft", "optifine/" + rest, kind);
            addCandidate(out, "minecraft", "mcpatcher/" + rest, kind);
        } else if (p.startsWith("/")) {
            String rest = p.substring(1);
            addCandidate(out, ns, "optifine/" + rest, kind);
            addCandidate(out, ns, "mcpatcher/" + rest, kind);
            addCandidate(out, ns, rest, kind);
        } else if (p.indexOf(':') >= 0) {
            String ns2 = p.substring(0, p.indexOf(':'));
            String p2 = p.substring(p.indexOf(':') + 1);
            addCandidate(out, ns2, p2, kind);
            addCandidate(out, ns2, kind.directory + p2, kind);
            if (kind == AssetKind.TEXTURE && p2.indexOf('/') < 0) addCandidate(out, ns2, "textures/items/" + p2, kind);
        } else {
            boolean absoluteFirst = p.startsWith("textures/") || p.startsWith("models/")
                    || p.startsWith("mcpatcher/") || p.startsWith("optifine/") || p.startsWith("citresewn/")
                    || (kind == AssetKind.MODEL && (p.startsWith("item/") || p.startsWith("block/")));
            if (!absoluteFirst) addCandidate(out, ns, dir + p, kind);
            addCandidate(out, ns, p, kind);
            addCandidate(out, ns, kind.directory + p, kind);
            if (!ns.equals("minecraft")) {
                addCandidate(out, "minecraft", p, kind);
                addCandidate(out, "minecraft", kind.directory + p, kind);
            }
            if (absoluteFirst) addCandidate(out, ns, dir + p, kind);
            if (kind == AssetKind.TEXTURE && p.indexOf('/') < 0) {
                addCandidate(out, ns, "textures/items/" + p, kind);
                addCandidate(out, "minecraft", "textures/items/" + p, kind);
            }
        }
        return out;
    }

    private static void addCandidate(Set<ResourceLocation> out, String ns, String path, AssetKind kind) {
        String normalized = normalizePath(path);
        if (normalized.isEmpty()) return;
        try {
            out.add(new ResourceLocation(ns, normalized + kind.extension));
            if (normalized.startsWith("mcpatcher/")) {
                out.add(new ResourceLocation(ns, "optifine/" + normalized.substring("mcpatcher/".length()) + kind.extension));
            } else if (normalized.startsWith("optifine/")) {
                out.add(new ResourceLocation(ns, "mcpatcher/" + normalized.substring("optifine/".length()) + kind.extension));
            }
        } catch (Exception ignored) {
        }
    }

    private static String normalizePath(String path) {
        Deque<String> stack = new ArrayDeque<>();
        for (String part : path.split("/")) {
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.pollLast();
            } else if (!part.equals(".") && !part.isEmpty()) {
                stack.addLast(part);
            }
        }
        return String.join("/", stack);
    }

    public static String spriteName(ResourceLocation texture) {
        String path = texture.getPath();
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        return texture.getNamespace() + ":" + path;
    }

    @Nullable
    public static ModelResourceLocation registeredItemModel(ResourceLocation modelFile) {
        String path = modelFile.getPath();
        if (!path.startsWith("models/item/") || !path.endsWith(".json")) return null;
        return new ModelResourceLocation(new ResourceLocation(modelFile.getNamespace(),
                path.substring("models/item/".length(), path.length() - ".json".length())), "inventory");
    }

    public static List<ResourceLocation> getTextureLayers(CITEntry entry, ItemStack stack, @Nullable String subKey) {
        Map<String, ResourceLocation> sub = entry.subTextures();
        ResourceLocation main = subKey != null ? sub.get(subKey) : null;
        if (main == null) main = entry.texture();
        Item item = stack.getItem();

        if (item instanceof ItemPotion) {
            String bottleKey = item == Items.SPLASH_POTION ? "potion_bottle_splash"
                    : item == Items.LINGERING_POTION ? "potion_bottle_lingering" : "potion_bottle_drinkable";
            ResourceLocation bottle = sub.get(bottleKey);
            if (bottle == null && item == Items.LINGERING_POTION) bottle = sub.get("potion_bottle_drinkable");
            ResourceLocation overlay = sub.get("potion_overlay");
            if (bottle != null || overlay != null) {
                if (bottle == null) bottle = main != null ? main : vanillaTexture("items/" + bottleKey);
                return List.of(overlay != null ? overlay : vanillaTexture("items/potion_overlay"), bottle);
            }
        }

        if (item instanceof ItemArmor armor && armor.getArmorMaterial() == ItemArmor.ArmorMaterial.LEATHER) {
            String piece = switch (armor.armorType) {
                case HEAD -> "helmet";
                case CHEST -> "chestplate";
                case LEGS -> "leggings";
                default -> "boots";
            };
            ResourceLocation base = sub.getOrDefault("leather_" + piece, main);
            if (base != null) {
                ResourceLocation overlay = sub.get("leather_" + piece + "_overlay");
                return List.of(base, overlay != null ? overlay : vanillaTexture("items/leather_" + piece + "_overlay"));
            }
        }

        return main == null ? List.of() : List.of(main);
    }

    private static ResourceLocation vanillaTexture(String path) {
        return new ResourceLocation("minecraft", "textures/" + path + ".png");
    }

    @Nullable
    public static CITEntry getMatch(ItemStack stack, CITEntry.CITType type) {
        List<CITEntry> matches = getMatchesOfType(stack, type);
        return matches.isEmpty() ? null : matches.getFirst();
    }

    public static List<CITEntry> getMatchesOfType(ItemStack stack, CITEntry.CITType type) {
        if (!loaded) reload();
        if (stack.isEmpty() || entries.isEmpty()) return List.of();
        NBTTagCompound tag = stack.getTagCompound();
        boolean offHand = renderOffHand;
        MatchKey key = new MatchKey(stack.getItem(), stack.getMetadata(), stack.getCount(), offHand, type, tag);
        List<CITEntry> cached = matchCache.get(key);
        if (cached != null) return cached;

        List<CITEntry> result = new ArrayList<>();
        for (CITEntry entry : entries) {
            if (entry.citType() == type && entry.matches(stack, offHand)) result.add(entry);
        }
        result = result.isEmpty() ? List.of() : List.copyOf(result);
        if (matchCache.size() >= MAX_CACHE_SIZE) matchCache.clear();
        matchCache.put(new MatchKey(stack.getItem(), stack.getMetadata(), stack.getCount(), offHand, type,
                tag == null ? null : tag.copy()), result);
        return result;
    }

    public static void invalidate() {
        loaded = false;
        entries.clear();
        matchCache.clear();
    }

    public static Set<ResourceLocation> getAllCITTextures() {
        if (!loaded) reload();
        Set<ResourceLocation> textures = new LinkedHashSet<>();
        for (CITEntry entry : entries) {
            if (entry.citType() != CITEntry.CITType.ITEM) continue;
            if (entry.texture() != null) textures.add(entry.texture());
            textures.addAll(entry.subTextures().values());
            if (entry.model() != null) textures.addAll(CITModelLoader.collectTextures(entry.model()));
            for (ResourceLocation subModel : entry.subModels().values()) {
                textures.addAll(CITModelLoader.collectTextures(subModel));
            }
        }
        return textures;
    }
}
