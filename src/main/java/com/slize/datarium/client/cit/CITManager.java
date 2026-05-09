package com.slize.datarium.client.cit;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.util.UndoFlattenUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CITManager {

    private static final ConcurrentHashMap<Long, CITEntry> matchCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, List<CITEntry>> matchTypeCache = new ConcurrentHashMap<>();

    private static long stackCacheKey(ItemStack stack, CITEntry.Hand hand) {
        int itemId = Item.getIdFromItem(stack.getItem());
        int damage = stack.getItemDamage();
        int count = stack.getCount();
        int nbtHash = stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0;
        int handOrd = hand.ordinal();
        return ((long)(itemId & 0xFFFF) << 48)
                | ((long)(damage & 0xFFFF) << 32)
                | ((long)(count   & 0xFF)  << 24)
                | ((long)(handOrd & 0xFF)  << 16)
                | ((long)(nbtHash & 0xFFFF));
    }

    private static long stackTypeCacheKey(ItemStack stack, CITEntry.CITType type) {
        int itemId = Item.getIdFromItem(stack.getItem());
        int damage = stack.getItemDamage();
        int nbtHash = stack.hasTagCompound() ? stack.getTagCompound().hashCode() : 0;
        int typeOrd = type.ordinal();
        return ((long)(itemId & 0xFFFF) << 48)
                | ((long)(damage & 0xFFFF) << 32)
                | ((long)(typeOrd & 0xFF)  << 24)
                | ((long)(nbtHash & 0xFFFF));
    }

    private static final List<CITEntry> entries = new ArrayList<>();
    private static boolean loaded = false;

    public static void reload() {
        entries.clear();
        matchCache.clear();
        matchTypeCache.clear();
        loaded = true;

        List<IResourcePack> packs = new ArrayList<>();

        try {
            Field defaultPackField = ResourcePackRepository.class.getDeclaredField("rprDefaultResourcePack");
            defaultPackField.setAccessible(true);
            IResourcePack defaultPack = (IResourcePack) defaultPackField.get(Minecraft.getMinecraft().getResourcePackRepository());
            if (defaultPack != null) {
                packs.add(defaultPack);
            }
        } catch (Exception ignored) {}

        for (ResourcePackRepository.Entry entry : Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries()) {
            packs.add(entry.getResourcePack());
        }

        for (IResourcePack pack : packs) {
            scanPack(pack);
        }

        entries.sort((a, b) -> {
            int weightCmp = Integer.compare(b.getWeight(), a.getWeight());
            if (weightCmp != 0) return weightCmp;
            int specA = datariumSpecificity(a);
            int specB = datariumSpecificity(b);
            return Integer.compare(specB, specA);
        });

        DatariumMain.LOGGER.info("Datarium: Loaded {} CIT entries", entries.size());
    }

    private static void scanPack(IResourcePack pack) {
        if (pack instanceof FileResourcePack) {
            scanZipPack((FileResourcePack) pack);
        } else if (pack instanceof FolderResourcePack) {
            scanFolderPack((FolderResourcePack) pack);
        }
    }

    private static void scanZipPack(FileResourcePack pack) {
        try {
            Field fileField = AbstractResourcePack.class.getDeclaredField("resourcePackFile");
            fileField.setAccessible(true);
            File file = (File) fileField.get(pack);

            try (ZipFile zip = new ZipFile(file)) {
                Enumeration<? extends ZipEntry> zipEntries = zip.entries();
                while (zipEntries.hasMoreElements()) {
                    ZipEntry entry = zipEntries.nextElement();
                    String name = entry.getName();

                    if (isCITPropertiesPath(name) && name.endsWith(".properties")) {
                        try (InputStream is = zip.getInputStream(entry)) {
                            byte[] bytes = readAllBytes(is);
                            String content = new String(bytes, StandardCharsets.UTF_8);
                            ResourceLocation loc = pathToResourceLocation(name);
                            if (loc != null) {
                                parseAndAddEntry(loc, content);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore errors for packs we can't read
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int bytesRead;
        while ((bytesRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, bytesRead);
        }
        return buffer.toByteArray();
    }

    private static void scanFolderPack(FolderResourcePack pack) {
        try {
            Field fileField = AbstractResourcePack.class.getDeclaredField("resourcePackFile");
            fileField.setAccessible(true);
            File folder = (File) fileField.get(pack);

            File assetsFolder = new File(folder, "assets");
            if (assetsFolder.exists() && assetsFolder.isDirectory()) {
                scanFolderRecursive(assetsFolder.toPath(), folder.toPath());
            }
        } catch (Exception e) {
            // Ignore errors
        }
    }

    private static void scanFolderRecursive(Path assetsPath, Path packRoot) {
        try {
            Files.walk(assetsPath)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".properties"))
                    .forEach(path -> {
                        String relativePath = packRoot.relativize(path).toString().replace('\\', '/');
                        if (isCITPropertiesPath(relativePath)) {
                            try {
                                String content = Files.readString(path);
                                ResourceLocation loc = pathToResourceLocation(relativePath);
                                if (loc != null) {
                                    parseAndAddEntry(loc, content);
                                }
                            } catch (IOException e) {
                                // Ignore
                            }
                        }
                    });
        } catch (IOException e) {
            // Ignore
        }
    }

    private static boolean isCITPropertiesPath(String path) {
        return path.contains("/optifine/cit/") ||
                path.contains("/citresewn/cit/") ||
                path.contains("/mcpatcher/cit/");
    }

    @Nullable
    private static ResourceLocation pathToResourceLocation(String path) {
        if (!path.startsWith("assets/")) return null;

        String afterAssets = path.substring("assets/".length());
        int slashIdx = afterAssets.indexOf('/');
        if (slashIdx == -1) return null;

        String namespace = afterAssets.substring(0, slashIdx);
        String filePath = afterAssets.substring(slashIdx + 1);

        return new ResourceLocation(namespace, filePath);
    }

    private static void parseAndAddEntry(ResourceLocation location, String content) {
        Properties props = new Properties();
        try {
            props.load(new StringReader(content));
        } catch (IOException e) {
            return;
        }

        String typeStr = props.getProperty("type", "item").trim().toLowerCase(Locale.ROOT);
        CITEntry.CITType citType = switch (typeStr) {
            case "armor" -> CITEntry.CITType.ARMOR;
            case "elytra" -> CITEntry.CITType.ELYTRA;
            case "enchantment" -> CITEntry.CITType.ENCHANTMENT;
            default -> CITEntry.CITType.ITEM;
        };

        // items
        // items
        List<Item> items = new ArrayList<>();
        Integer translatedDamage = null;

        String itemsStr = props.getProperty("items", props.getProperty("matchItems", "")).trim();
        if (itemsStr.isEmpty()) {
            String path = location.getPath();
            String fileName = path.substring(path.lastIndexOf('/') + 1);
            if (fileName.endsWith(".properties")) fileName = fileName.substring(0, fileName.length() - ".properties".length());
            String autoId = fileName.contains(":") ? fileName : "minecraft:" + fileName;

            UndoFlattenUtil.FlattenedItem fi = UndoFlattenUtil.getUnflattenedItem(autoId);
            if (fi != null) {
                autoId = fi.itemId();
                translatedDamage = fi.damage();
            }

            Item autoItem = Item.getByNameOrId(autoId);
            if (autoItem != null) items.add(autoItem);
        } else {
            for (String itemId : itemsStr.split("\\s+")) {
                itemId = itemId.trim();
                if (itemId.isEmpty()) continue;
                if (!itemId.contains(":")) itemId = "minecraft:" + itemId;

                UndoFlattenUtil.FlattenedItem fi = UndoFlattenUtil.getUnflattenedItem(itemId);
                if (fi != null) {
                    itemId = fi.itemId();
                    if (translatedDamage == null) {
                        translatedDamage = fi.damage();
                    }
                }

                Item item = Item.getByNameOrId(itemId);
                if (item != null) items.add(item);
            }
        }

        // texture
        String textureStr = props.getProperty("texture", props.getProperty("tile", "")).trim();
        ResourceLocation texture = null;
        if (!textureStr.isEmpty()) {
            texture = resolveAssetPath(location, textureStr, ".png");
        }

        // model
        String modelStr = props.getProperty("model", "").trim();
        ResourceLocation model = null;
        if (!modelStr.isEmpty()) {
            model = resolveAssetPath(location, modelStr, ".json");
        }

        // auto-discovery: if neither texture nor model declared, look for same-named file
        if (texture == null && model == null) {
            String path = location.getPath();
            String base = path.substring(0, path.lastIndexOf('.'));
            ResourceLocation autoModel = new ResourceLocation(location.getNamespace(), base + ".json");
            ResourceLocation autoTexture = new ResourceLocation(location.getNamespace(), base + ".png");
            // We'll store both as candidates; at apply time check which exists
            // For now store in texture/model fields — prefer model
            model = autoModel;
            texture = autoTexture;
            // Mark as auto (we'll check existence at apply time in MixinItemOverrideList)
            // We can use a convention: store both and let the mixin try model first, then texture
        }

        // sub textures: texture.<name>=...
        Map<String, ResourceLocation> subTextures = new HashMap<>();
        Map<String, ResourceLocation> subModels = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("texture.") && key.length() > "texture.".length()) {
                String subName = key.substring("texture.".length());
                String subVal = props.getProperty(key).trim();
                if (!subVal.isEmpty()) subTextures.put(subName, resolveAssetPath(location, subVal, ".png"));
            } else if (key.startsWith("model.") && key.length() > "model.".length()) {
                String subName = key.substring("model.".length());
                String subVal = props.getProperty(key).trim();
                if (!subVal.isEmpty()) subModels.put(subName, resolveAssetPath(location, subVal, ".json"));
            }
        }

        // weight
        int weight = 0;
        try { weight = Integer.parseInt(props.getProperty("weight", "0").trim()); } catch (NumberFormatException ignored) {}

        // damage
        Integer damage = null, damageMin = null, damageMax = null;
        boolean damagePercent = false;
        Integer damageMask = null;
        String damageStr = props.getProperty("damage", "").trim();
        if (!damageStr.isEmpty()) {
            if (damageStr.endsWith("%")) {
                damagePercent = true;
                damageStr = damageStr.substring(0, damageStr.length() - 1).trim();
            }
            if (damageStr.contains("-")) {
                String[] parts = damageStr.split("-", 2);
                try {
                    damageMin = Integer.parseInt(parts[0].trim());
                    damageMax = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
            } else {
                try { damage = Integer.parseInt(damageStr); } catch (NumberFormatException ignored) {}
            }
        }
        String damageMaskStr = props.getProperty("damageMask", "").trim();
        if (!damageMaskStr.isEmpty()) {
            try { damageMask = Integer.parseInt(damageMaskStr); } catch (NumberFormatException ignored) {}
        }

        if (damage == null && damageMin == null && damageMax == null && translatedDamage != null) {
            damage = translatedDamage;
        }

        // stackSize
        Integer stackSize = null, stackSizeMin = null, stackSizeMax = null;
        String stackStr = props.getProperty("stackSize", "").trim();
        if (!stackStr.isEmpty()) {
            if (stackStr.contains("-")) {
                String[] parts = stackStr.split("-", 2);
                try {
                    String lo = parts[0].trim(), hi = parts[1].trim();
                    stackSizeMin = lo.isEmpty() ? null : Integer.parseInt(lo);
                    stackSizeMax = hi.isEmpty() ? null : Integer.parseInt(hi);
                } catch (Exception ignored) {}
            } else {
                try { stackSize = Integer.parseInt(stackStr); } catch (NumberFormatException ignored) {}
            }
        }

        // hand
        CITEntry.Hand hand = CITEntry.Hand.ANY;
        String handStr = props.getProperty("hand", "").trim().toLowerCase(Locale.ROOT);
        if (handStr.equals("main")) hand = CITEntry.Hand.MAIN;
        else if (handStr.equals("off")) hand = CITEntry.Hand.OFF;

        // enchantments
        Map<Enchantment, int[]> enchantments = parseEnchantments(props);

        // nbt conditions (generic)
        List<CITEntry.NBTCondition> nbtConditions = new ArrayList<>();
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("nbt.") && key.length() > "nbt.".length()) {
                String nbtPath = key.substring("nbt.".length());
                String nbtVal = props.getProperty(key);
                nbtConditions.add(new CITEntry.NBTCondition(nbtPath, nbtVal));
            }
        }

        int glintLayer = 0;
        float glintSpeed = 0f, glintRotation = 0f;
        float glintR = 1f, glintG = 1f, glintB = 1f, glintA = 1f;
        boolean glintBlur = false, glintUseGlint = false;
        String glintBlend = "add";
        if (citType == CITEntry.CITType.ENCHANTMENT) {
            try { glintLayer = Integer.parseInt(props.getProperty("layer", "0").trim()); } catch (NumberFormatException ignored) {}
            try { glintSpeed = Float.parseFloat(props.getProperty("speed", "0.0").trim()); } catch (NumberFormatException ignored) {}
            try { glintRotation = Float.parseFloat(props.getProperty("rotation", "0.0").trim()); } catch (NumberFormatException ignored) {}
            try { glintR = Float.parseFloat(props.getProperty("r", "1.0").trim()); } catch (NumberFormatException ignored) {}
            try { glintG = Float.parseFloat(props.getProperty("g", "1.0").trim()); } catch (NumberFormatException ignored) {}
            try { glintB = Float.parseFloat(props.getProperty("b", "1.0").trim()); } catch (NumberFormatException ignored) {}
            try { glintA = Float.parseFloat(props.getProperty("a", "1.0").trim()); } catch (NumberFormatException ignored) {}
            glintBlur = Boolean.parseBoolean(props.getProperty("blur", "false").trim());
            glintUseGlint = Boolean.parseBoolean(props.getProperty("useGlint", "false").trim());
            glintBlend = props.getProperty("blend", "add").trim();
        }

        CITEntry entry = new CITEntry(location, citType, items, texture, model,
                subTextures, subModels, weight,
                damage, damageMin, damageMax, damagePercent, damageMask,
                stackSize, stackSizeMin, stackSizeMax,
                hand, enchantments, nbtConditions,
                glintLayer, glintSpeed, glintRotation,
                glintR, glintG, glintB, glintA,
                glintBlur, glintUseGlint, glintBlend);
        entries.add(entry);
    }

    @Nullable
    private static Map<Enchantment, int[]> parseEnchantments(Properties props) {
        Map<Enchantment, int[]> result = new HashMap<>();

        String enchIds = props.getProperty("enchantmentIDs", props.getProperty("enchantments", ""));
        String enchLevels = props.getProperty("enchantmentLevels", "");

        if (!enchIds.isEmpty()) {
            String[] ids = enchIds.split("\\s+");
            String[] levels = enchLevels.isEmpty() ? new String[0] : enchLevels.split("\\s+");

            for (int i = 0; i < ids.length; i++) {
                String enchName = ids[i].trim();
                if (enchName.isEmpty()) continue;

                if (!enchName.contains(":")) {
                    enchName = "minecraft:" + enchName;
                }

                Enchantment ench = Enchantment.getEnchantmentByLocation(enchName);
                if (ench != null) {
                    int[] levelRange;
                    if (i < levels.length) {
                        String levelStr = levels[i].trim();
                        if (levelStr.contains("-")) {
                            String[] parts = levelStr.split("-");
                            levelRange = new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
                        } else {
                            int lvl = Integer.parseInt(levelStr);
                            levelRange = new int[]{lvl, lvl};
                        }
                    } else {
                        levelRange = new int[]{1, 255};
                    }
                    result.put(ench, levelRange);
                }
            }
        }

        return result.isEmpty() ? null : result;
    }

    public static Set<ResourceLocation> collectModelTextures(CITEntry entry) {
        Set<ResourceLocation> result = new HashSet<>();
        ResourceLocation modelLoc = entry.getModel();
        if (modelLoc == null) return result;

        ResourceLocation jsonLoc = modelLoc.getPath().endsWith(".json") ? modelLoc
                : new ResourceLocation(modelLoc.getNamespace(), modelLoc.getPath() + ".json");

        try (InputStreamReader reader = new InputStreamReader(
                Minecraft.getMinecraft().getResourceManager().getResource(jsonLoc).getInputStream(),
                StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject json = new com.google.gson.GsonBuilder().create()
                    .fromJson(reader, com.google.gson.JsonObject.class);
            if (json.has("textures")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : json.getAsJsonObject("textures").entrySet()) {
                    String texPath = e.getValue().getAsString();
                    if (texPath.startsWith("#")) continue;
                    ResourceLocation texLoc;
                    if (texPath.contains(":")) {
                        String[] parts = texPath.split(":", 2);
                        String p = parts[1].endsWith(".png") ? parts[1] : parts[1] + ".png";
                        texLoc = new ResourceLocation(parts[0], "textures/" + p);
                    } else if (texPath.startsWith("./") || texPath.startsWith("../") || !texPath.contains("/")) {
                        texLoc = resolveAssetPath(entry.getPropertiesLocation(), texPath, ".png");
                    } else {
                        String p = texPath.endsWith(".png") ? texPath : texPath + ".png";
                        texLoc = new ResourceLocation("minecraft", "textures/" + p);
                    }
                    if (texLoc != null) result.add(texLoc);
                }
            }
        } catch (Exception ignored) {}

        return result;
    }

    public static ResourceLocation resolveAssetPath(ResourceLocation propsLocation, String path, String ext) {
        String domain = propsLocation.getNamespace();
        String basePath = propsLocation.getPath();
        int lastSlash = basePath.lastIndexOf('/');
        String dir = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "";

        String resolved;
        String resolvedDomain = domain;

        if (path.contains(":")) {
            // namespaced: either "ns:path" or "assets/ns/path"
            String[] colonParts = path.split(":", 2);
            resolvedDomain = colonParts[0];
            resolved = colonParts[1];
        } else if (path.startsWith("assets/")) {
            // full resourcepack path
            String afterAssets = path.substring("assets/".length());
            int slash = afterAssets.indexOf('/');
            if (slash == -1) return null;
            resolvedDomain = afterAssets.substring(0, slash);
            resolved = afterAssets.substring(slash + 1);
        } else if (path.startsWith("/")) {
            resolved = path.substring(1);
        } else {
            resolved = dir + path;
        }

        resolved = normalizePath(resolved);

        if (!resolved.endsWith(ext)) resolved += ext;

        return new ResourceLocation(resolvedDomain, resolved);
    }

    private static String normalizePath(String path) {
        String[] parts = path.split("/");
        Deque<String> stack = new ArrayDeque<>();
        for (String part : parts) {
            if (part.equals("..")) {
                if (!stack.isEmpty()) stack.pollLast();
            } else if (!part.equals(".") && !part.isEmpty()) {
                stack.addLast(part);
            }
        }
        return String.join("/", stack);
    }

    @Nullable
    public static CITEntry getMatch(ItemStack stack) {
        return getMatch(stack, CITEntry.Hand.ANY);
    }

    @Nullable
    public static CITEntry getMatch(ItemStack stack, CITEntry.Hand hand) {
        if (!loaded) reload();
        long key = stackCacheKey(stack, hand);
        if (matchCache.containsKey(key)) return matchCache.get(key);
        CITEntry result = null;
        for (CITEntry entry : entries) {
            if (entry.matches(stack, hand)) { result = entry; break; }
        }
        if (result != null) matchCache.put(key, result);
        return result;
    }

    public static List<CITEntry> getMatchesOfType(ItemStack stack, CITEntry.CITType type) {
        if (!loaded) reload();
        long key = stackTypeCacheKey(stack, type);
        if (matchTypeCache.containsKey(key)) return matchTypeCache.get(key);
        List<CITEntry> result = new ArrayList<>();
        for (CITEntry entry : entries) {
            if (entry.getCITType() == type && entry.matches(stack)) result.add(entry);
        }
        matchTypeCache.put(key, result);
        return result;
    }

    public static void invalidate() {
        loaded = false;
        entries.clear();
        matchCache.clear();
        matchTypeCache.clear();
    }

    public static List<CITEntry> getEntries() {
        if (!loaded) reload();
        return Collections.unmodifiableList(entries);
    }

    private static int datariumSpecificity(CITEntry entry) {
        int score = 0;
        for (CITEntry.NBTCondition cond : entry.getNbtConditions()) {
            score += cond.matchValue.length();
        }
        return score;
    }

    public static Set<ResourceLocation> getAllCITTextures() {
        if (!loaded) reload();
        Set<ResourceLocation> textures = new HashSet<>();
        for (CITEntry entry : entries) {
            if (entry.getTexture() != null) textures.add(entry.getTexture());
            textures.addAll(entry.getSubTextures().values());
        }
        return textures;
    }
}