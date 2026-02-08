package com.slize.datarium.client.cit;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class CITManager {
    private static final List<CITEntry> entries = new ArrayList<>();
    private static boolean loaded = false;

    public static void reload() {
        entries.clear();
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

        entries.sort((a, b) -> Integer.compare(b.getWeight(), a.getWeight()));

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

        String type = props.getProperty("type", "item");
        if (!"item".equals(type)) {
            return;
        }

        List<Item> items = new ArrayList<>();
        String itemsStr = props.getProperty("items", props.getProperty("matchItems", ""));
        if (!itemsStr.isEmpty()) {
            for (String itemId : itemsStr.split("\\s+")) {
                itemId = itemId.trim();
                if (itemId.isEmpty()) continue;

                if (!itemId.contains(":")) {
                    itemId = "minecraft:" + itemId;
                }

                Item item = Item.getByNameOrId(itemId);
                if (item != null) {
                    items.add(item);
                }
            }
        }

        String textureStr = props.getProperty("texture", props.getProperty("tile", ""));
        ResourceLocation texture = null;
        if (!textureStr.isEmpty()) {
            texture = resolveTexturePath(location, textureStr);
        }

        String modelStr = props.getProperty("model", "");
        ResourceLocation model = null;
        if (!modelStr.isEmpty()) {
            model = resolveModelPath(location, modelStr);
        }

        String nameMatch = null;
        CITEntry.MatchType nameMatchType = CITEntry.MatchType.EXACT;

        String nbtName = props.getProperty("nbt.display.Name", "");
        if (nbtName.isEmpty()) {
            nbtName = props.getProperty("nbt.display.name", "");
        }

        if (!nbtName.isEmpty()) {
            if (nbtName.startsWith("ipattern:")) {
                nameMatch = nbtName.substring("ipattern:".length());
                nameMatchType = CITEntry.MatchType.IPATTERN;
            } else if (nbtName.startsWith("pattern:")) {
                nameMatch = nbtName.substring("pattern:".length());
                nameMatchType = CITEntry.MatchType.PATTERN;
            } else if (nbtName.startsWith("iregex:")) {
                nameMatch = nbtName.substring("iregex:".length());
                nameMatchType = CITEntry.MatchType.IREGEX;
            } else if (nbtName.startsWith("regex:")) {
                nameMatch = nbtName.substring("regex:".length());
                nameMatchType = CITEntry.MatchType.REGEX;
            } else {
                nameMatch = nbtName;
            }
        }

        int weight = 0;
        try {
            weight = Integer.parseInt(props.getProperty("weight", "0"));
        } catch (NumberFormatException ignored) {}

        Integer damage = null;
        Integer damageMin = null;
        Integer damageMax = null;
        String damageStr = props.getProperty("damage", "");
        if (!damageStr.isEmpty()) {
            if (damageStr.contains("-")) {
                String[] parts = damageStr.split("-");
                try {
                    damageMin = Integer.parseInt(parts[0].trim());
                    damageMax = Integer.parseInt(parts[1].trim());
                } catch (Exception ignored) {}
            } else {
                try {
                    damage = Integer.parseInt(damageStr.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<Enchantment, int[]> enchantments = parseEnchantments(props);

        CITEntry entry = new CITEntry(location, items, texture, model, nameMatch, nameMatchType,
                weight, damage, damageMin, damageMax, enchantments);
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

    private static ResourceLocation resolveTexturePath(ResourceLocation propsLocation, String texture) {
        String domain = propsLocation.getNamespace();
        String basePath = propsLocation.getPath();

        int lastSlash = basePath.lastIndexOf('/');
        String dir = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "";

        if (texture.contains(":")) {
            String[] parts = texture.split(":", 2);
            String path = parts[1];
            if (!path.endsWith(".png")) path += ".png";
            return new ResourceLocation(parts[0], dir + path);
        } else if (texture.startsWith("./") || texture.startsWith("~/")) {
            String path = texture.substring(2);
            if (!path.endsWith(".png")) path += ".png";
            return new ResourceLocation(domain, dir + path);
        } else if (texture.startsWith("/")) {
            String path = texture.substring(1);
            if (!path.endsWith(".png")) path += ".png";
            return new ResourceLocation(domain, path);
        } else {
            String path = texture;
            if (!path.endsWith(".png")) path += ".png";
            return new ResourceLocation(domain, dir + path);
        }
    }

    private static ResourceLocation resolveModelPath(ResourceLocation propsLocation, String model) {
        String domain = propsLocation.getNamespace();
        String basePath = propsLocation.getPath();

        int lastSlash = basePath.lastIndexOf('/');
        String dir = lastSlash >= 0 ? basePath.substring(0, lastSlash + 1) : "";

        if (model.contains(":")) {
            String[] parts = model.split(":", 2);
            return new ResourceLocation(parts[0], parts[1]);
        } else if (model.startsWith("./") || model.startsWith("~/")) {
            String path = model.substring(2);
            return new ResourceLocation(domain, dir + path);
        } else if (model.startsWith("/")) {
            return new ResourceLocation(domain, model.substring(1));
        } else {
            return new ResourceLocation(domain, dir + model);
        }
    }

    @Nullable
    public static CITEntry getMatch(ItemStack stack) {
        if (!loaded) {
            reload();
        }

        for (CITEntry entry : entries) {
            if (entry.matches(stack)) {
                return entry;
            }
        }
        return null;
    }

    public static void invalidate() {
        loaded = false;
        entries.clear();
    }

    public static List<CITEntry> getEntries() {
        if (!loaded) reload();
        return Collections.unmodifiableList(entries);
    }

    public static Set<ResourceLocation> getAllCITTextures() {
        if (!loaded) reload();
        Set<ResourceLocation> textures = new HashSet<>();
        for (CITEntry entry : entries) {
            if (entry.getTexture() != null) {
                textures.add(entry.getTexture());
            }
        }
        return textures;
    }
}