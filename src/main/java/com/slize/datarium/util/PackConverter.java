package com.slize.datarium.util;

import com.google.common.collect.MapMaker;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.mixin.accessors.IAbstractResourcePackAccessor;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.client.resources.data.PackMetadataSection;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Translates resource names between 1.12.2 (pack format <= 3) and modern (pack format >= 4) packs,
 * per pack, using the pack's declared format and the table {@code assets/datarium/legacy_names.txt}.
 * Only paths and ids are renamed.
 * <p>
 * Converts:
 * <ul>
 * <li>vanilla texture and item model paths, together with their {@code .mcmeta} files;</li>
 * <li>{@code textures/blocks|items/} <-> {@code textures/block|item/} for any namespace;</li>
 * <li>modern item definitions ({@code items/<name>.json}) for legacy item models;</li>
 * <li>1.20.2+ gui sprites and 1.14+ mob effect icons, drawn back into the legacy gui sheets by {@link GuiSheetComposer};</li>
 * <li>entity textures whose modern layout needs a CEM model, only while that model is loaded;</li>
 * <li>layered villager textures, flattened into the legacy texture: for the CEM model while it is loaded,
 * otherwise for the 1.12.2 model once a modern pack supplies a layer;</li>
 * <li>modern item and block ids to legacy id + metadata / block state, for packs with format >= 4;</li>
 * <li>1.12.2 entity ids and vanilla model fields to CEM model and part names.</li>
 * </ul>
 * Does not convert:
 * <ul>
 * <li>block models and blockstates, or references inside any JSON;</li>
 * <li>table renames outside the "minecraft" namespace;</li>
 * <li>textures that differ in content rather than name (e.g. water, enchantment glint);</li>
 * <li>legacy names that modern packs reuse for something else;</li>
 * <li>anything missing from the table.</li>
 * </ul>
 * @author Th3_Sl1ze
 */
public final class PackConverter {
    public static final int LEGACY = 3;
    public static final int FLATTENING = 4;
    public static final int SPLIT_CHESTS = 5;

    private static final String VANILLA = "minecraft";
    private static final String BUNDLED = "datarium";
    private static final String TABLE = "/assets/datarium/legacy_names.txt";
    private static final String TEXTURES = "textures/";
    private static final String ITEM_MODELS = "models/item/";
    private static final String METADATA = ".mcmeta";
    private static final String[] FORMAT_KEYS = {"pack_format", "min_format", "supported_formats"};

    private static final Map<String, Entry> BY_LEGACY = new HashMap<>();
    private static final Map<String, Entry> BY_MODERN = new HashMap<>();
    private static final Map<String, GuiSheet> GUI_SHEETS = new HashMap<>();
    private static final Map<String, LegacyItem> ITEMS = new HashMap<>();
    private static final Map<String, BlockMatch[]> BLOCKS = new HashMap<>();
    private static final Map<String, String[]> ENTITY_MODELS = new HashMap<>();
    private static final Map<String, List<ModelPart>> MODEL_PARTS = new HashMap<>();
    private static final Map<String, String> PART_HOSTS = new HashMap<>();
    private static final Map<String, Map<String, String>> TRANSFORM_ALIASES = new HashMap<>();
    private static final Map<String, Map<String, String>> REPARENTS = new HashMap<>();

    private static final Map<IResourcePack, Integer> FORMATS = new MapMaker().weakKeys().makeMap();
    private static final Map<String, IResourcePack> PACKS = new MapMaker().weakValues().makeMap();
    private static final Map<ResourceLocation, ResourceLocation> ENTITY_TEXTURES = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, ResourceLocation> MODERN_LAYOUTS = new ConcurrentHashMap<>();

    static {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(PackConverter.class.getResourceAsStream(TABLE)), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') continue;
                String[] parts = line.split(" ");
                switch (parts[0]) {
                    case "T" -> readNames(parts, TEXTURES, ".png");
                    case "M" -> readNames(parts, ITEM_MODELS, ".json");
                    case "L" -> readLayers(parts);
                    case "G" -> readGuiSprite(parts);
                    case "I" -> readItem(parts);
                    case "B" -> readBlock(parts);
                    case "E" -> ENTITY_MODELS.put(parts[1], Arrays.copyOfRange(parts, 2, parts.length));
                    case "P" -> readPart(parts);
                    case "H" -> PART_HOSTS.put(parts[1], parts[2]);
                    case "A" -> readRelation(parts, TRANSFORM_ALIASES);
                    case "R" -> readRelation(parts, REPARENTS);
                    default -> {
                    }
                }
            }
        } catch (Exception e) {
            DatariumMain.LOGGER.error("[Converter] failed to read {}", TABLE, e);
        }
        for (Entry entry : BY_LEGACY.values()) {
            Entry other = BY_MODERN.get(entry.legacy);
            if (other != null && other != entry) entry.reused = true;
        }
    }

    private PackConverter() {}

    public record LegacyItem(String id, @Nullable Integer meta) {
    }

    public record ModelPart(String field, @Nullable String srg, int index, String[] parts) {
        public static final int WHOLE = -1;
    }

    public record GuiSprite(String texture, int x, int y, int width, int height, int border, boolean blend) {
    }

    public record GuiSheet(int size, List<GuiSprite> sprites) {
    }

    private record Name(String path, int since, @Nullable String gate) {
        boolean interchangeable() {
            return gate == null;
        }
    }

    private static final class Entry {
        final String legacy;
        Name[] names = new Name[0];
        boolean reused;
        @Nullable String layerModel;
        String[] layers = new String[0];
        int[][] moves = new int[0][];

        Entry(String legacy) {
            this.legacy = legacy;
        }
    }

    private static void readNames(String[] parts, String prefix, String suffix) {
        boolean reused = parts[1].startsWith("!");
        Entry entry = BY_LEGACY.computeIfAbsent(prefix + (reused ? parts[1].substring(1) : parts[1]) + suffix, Entry::new);
        entry.reused = reused;
        entry.names = new Name[parts.length - 2];
        for (int i = 2; i < parts.length; i++) {
            int colon = parts[i].indexOf(':');
            int at = parts[i].indexOf('@');
            Name name = new Name(prefix + parts[i].substring(colon + 1, at < 0 ? parts[i].length() : at) + suffix,
                    Integer.parseInt(parts[i].substring(0, colon)), at < 0 ? null : parts[i].substring(at + 1));
            entry.names[i - 2] = name;
            if (name.interchangeable() && !name.path.equals(entry.legacy)) BY_MODERN.putIfAbsent(name.path, entry);
        }
    }

    private static void readLayers(String[] parts) {
        Entry entry = BY_LEGACY.computeIfAbsent(TEXTURES + parts[1] + ".png", Entry::new);
        entry.layerModel = parts[2];
        List<String> layers = new ArrayList<>();
        List<int[]> moves = new ArrayList<>();
        for (int i = 3; i < parts.length; i++) {
            if (parts[i].indexOf('>') < 0) {
                layers.add(TEXTURES + parts[i] + ".png");
            } else {
                moves.add(Arrays.stream(parts[i].split("[,>]")).mapToInt(Integer::parseInt).toArray());
            }
        }
        entry.layers = layers.toArray(new String[0]);
        entry.moves = moves.toArray(new int[0][]);
    }

    private static void readGuiSprite(String[] parts) {
        int colon = parts[1].indexOf(':');
        int size = colon < 0 ? 256 : Integer.parseInt(parts[1].substring(colon + 1));
        boolean blend = parts[2].startsWith("+");
        GUI_SHEETS.computeIfAbsent(TEXTURES + (colon < 0 ? parts[1] : parts[1].substring(0, colon)) + ".png", _ -> new GuiSheet(size, new ArrayList<>()))
                .sprites().add(new GuiSprite(TEXTURES + (blend ? parts[2].substring(1) : parts[2]) + ".png", Integer.parseInt(parts[3]),
                        Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6]),
                        parts.length > 7 ? Integer.parseInt(parts[7]) : 0, blend));
    }

    private static void readItem(String[] parts) {
        int colon = parts[2].indexOf(':');
        ITEMS.put(VANILLA + ":" + parts[1], colon < 0 ? new LegacyItem(VANILLA + ":" + parts[2], null)
                : new LegacyItem(VANILLA + ":" + parts[2].substring(0, colon), Integer.valueOf(parts[2].substring(colon + 1))));
    }

    private static void readBlock(String[] parts) {
        BlockMatch[] matches = new BlockMatch[parts.length - 2];
        for (int i = 2; i < parts.length; i++) matches[i - 2] = new BlockMatch(parts[i]);
        BLOCKS.put(parts[1], matches);
    }

    private static void readPart(String[] parts) {
        String field = parts[2];
        int index = ModelPart.WHOLE;
        int bracket = field.indexOf('[');
        if (bracket >= 0) {
            if (bracket < field.length() - 2) index = Integer.parseInt(field.substring(bracket + 1, field.length() - 1));
            field = field.substring(0, bracket);
        }
        int bar = field.indexOf('|');
        MODEL_PARTS.computeIfAbsent(parts[1], _ -> new ArrayList<>()).add(new ModelPart(bar < 0 ? field : field.substring(0, bar),
                bar < 0 ? null : field.substring(bar + 1), index, Arrays.copyOfRange(parts, 3, parts.length)));
    }

    private static void readRelation(String[] parts, Map<String, Map<String, String>> relations) {
        for (int i = 3; i < parts.length; i++) relations.computeIfAbsent(parts[i], _ -> new HashMap<>()).put(parts[1], parts[2]);
    }

    public static void track(IResourcePack pack) {
        FORMATS.remove(pack);
        PACKS.put(pack.getPackName(), pack);
    }

    public static void invalidate() {
        ENTITY_TEXTURES.clear();
        MODERN_LAYOUTS.clear();
    }

    public static int formatOf(@Nullable IResourcePack pack) {
        if (pack == null) return LEGACY;
        Integer cached = FORMATS.get(pack);
        if (cached != null) return cached;
        int format = readFormat(pack);
        FORMATS.put(pack, format);
        return format;
    }

    public static int formatOf(@Nullable String packName) {
        return packName == null ? LEGACY : formatOf(PACKS.get(packName));
    }

    public static int formatOf(@Nullable ResourceLocation location) {
        return formatOf(packOf(location));
    }

    private static int readFormat(IResourcePack pack) {
        try {
            if (pack instanceof AbstractResourcePack) {
                try (InputStream in = ((IAbstractResourcePackAccessor) pack).invokeGetInputStreamByName("pack.mcmeta")) {
                    return readFormat(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
                }
            }
            PackMetadataSection section = pack.getPackMetadata(
                    Minecraft.getMinecraft().getResourcePackRepository().rprMetadataSerializer, "pack");
            return section == null ? LEGACY : section.getPackFormat();
        } catch (Exception e) {
            return LEGACY;
        }
    }

    static int readFormat(JsonElement metadata) {
        int format = declaredFormat(metadata.getAsJsonObject().getAsJsonObject("pack"));
        return format > 0 ? format : LEGACY;
    }

    public static int declaredFormat(JsonObject pack) {
        for (String key : FORMAT_KEYS) {
            int format = lowest(pack.get(key));
            if (format > 0) return format;
        }
        return 0;
    }

    private static int lowest(@Nullable JsonElement element) {
        if (element == null) return 0;
        if (element.isJsonPrimitive()) return element.getAsInt();
        if (element.isJsonArray()) return element.getAsJsonArray().isEmpty() ? 0 : lowest(element.getAsJsonArray().get(0));
        if (element.isJsonObject()) return lowest(element.getAsJsonObject().get("min_inclusive"));
        return 0;
    }

    public static ResourceLocation localize(IResourcePack pack, ResourceLocation location) {
        String path = location.getPath();
        if (!path.startsWith(TEXTURES) && !path.startsWith(ITEM_MODELS)) return location;
        String namespace = location.getNamespace();
        String relinked = relink(path, formatOf(pack), VANILLA.equals(namespace),
                candidate -> pack.resourceExists(new ResourceLocation(namespace, candidate)));
        return relinked.equals(path) ? location : new ResourceLocation(namespace, relinked);
    }

    static String relink(String path, int format, boolean vanilla, Predicate<String> present) {
        if (!path.endsWith(METADATA)) return relinkFile(path, format, vanilla, present);
        return relinkFile(path.substring(0, path.length() - METADATA.length()), format, vanilla, present) + METADATA;
    }

    private static String relinkFile(String path, int format, boolean vanilla, Predicate<String> present) {
        Entry entry = vanilla ? BY_LEGACY.get(path) : null;
        boolean legacyRequest = entry != null;
        if (entry == null && vanilla) entry = BY_MODERN.get(path);
        if (entry == null) {
            String swapped = swapDirectory(path, format);
            return swapped == null || present.test(path) || !present.test(swapped) ? path : swapped;
        }

        boolean modernPack = format >= FLATTENING;
        boolean repurposed = modernPack && legacyRequest && entry.reused;
        if (!repurposed && present.test(path)) return path;
        if (!modernPack) return !legacyRequest && present.test(entry.legacy) ? entry.legacy : path;

        String absent = path;
        int newest = entry.names.length - 1;
        while (newest >= 0 && entry.names[newest].since > format) newest--;
        for (int step = 0; step < entry.names.length; step++) {
            Name name = entry.names[step <= newest ? newest - step : step];
            if (!name.interchangeable() || name.path.equals(path)) continue;
            if (present.test(name.path)) return name.path;
            absent = name.path;
        }
        if (!legacyRequest && !entry.reused && present.test(entry.legacy)) return entry.legacy;
        return repurposed ? absent : path;
    }

    @Nullable
    private static String swapDirectory(String path, int format) {
        if (!path.startsWith(TEXTURES)) return null;
        boolean modernPack = format >= FLATTENING;
        String from = modernPack ? "s/" : "/";
        String to = modernPack ? "/" : "s/";
        for (String directory : new String[]{"block", "item"}) {
            if (path.startsWith(directory + from, TEXTURES.length())) {
                return TEXTURES + directory + to + path.substring(TEXTURES.length() + directory.length() + from.length());
            }
        }
        return null;
    }

    public static List<ResourceLocation> modernTextures(ResourceLocation legacy) {
        Entry entry = VANILLA.equals(legacy.getNamespace()) ? BY_LEGACY.get(legacy.getPath()) : null;
        if (entry == null) return List.of();
        List<ResourceLocation> modern = new ArrayList<>(entry.names.length);
        for (int i = entry.names.length - 1; i >= 0; i--) {
            if (!entry.names[i].path.equals(entry.legacy)) modern.add(new ResourceLocation(entry.names[i].path));
        }
        return modern;
    }

    @Nullable
    public static GuiSheet guiSheet(ResourceLocation location) {
        return VANILLA.equals(location.getNamespace()) ? GUI_SHEETS.get(location.getPath()) : null;
    }

    @Nullable
    public static ResourceLocation entityTexture(@Nullable ResourceLocation original) {
        if (original == null) return null;
        ResourceLocation cached = ENTITY_TEXTURES.get(original);
        if (cached != null) return cached;

        Entry entry = VANILLA.equals(original.getNamespace()) ? BY_LEGACY.get(original.getPath()) : null;
        ResourceLocation result = entry == null ? original : entityTexture(original, entry);
        ENTITY_TEXTURES.put(original, result);
        return result;
    }

    private static ResourceLocation entityTexture(ResourceLocation original, Entry entry) {
        if (entry.layerModel != null) {
            boolean modelled = CEMManager.getModel(entry.layerModel) != null;
            boolean supplied = modelled;
            int legacy = modelled ? 0 : priority(packOf(original));
            List<String> layers = new ArrayList<>();
            for (String layer : entry.layers) {
                ResourceLocation modern = new ResourceLocation(layer);
                ResourceLocation location = modernOrBundled(modern, FLATTENING, BY_LEGACY.containsKey(layer));
                if (location == null) continue;
                layers.add(location.toString());
                if (!supplied && location == modern && priority(packOf(modern)) >= legacy) supplied = true;
            }
            if (supplied && layers.size() > 1) {
                ResourceLocation composed = new ResourceLocation(BUNDLED, (modelled ? "cem/" : "legacy/") + entry.legacy.replace('/', '_'));
                Minecraft.getMinecraft().getTextureManager().loadTexture(composed,
                        new LayeredEntityTexture(layers, modelled ? new int[0][] : entry.moves));
                return composed;
            }
        }
        for (int i = entry.names.length - 1; i >= 0; i--) {
            Name name = entry.names[i];
            if (name.gate == null || name.gate.isEmpty() || CEMManager.getModel(name.gate) == null) continue;
            ResourceLocation location = modernOrBundled(new ResourceLocation(name.path), name.since, name.path.equals(entry.legacy));
            if (location != null) return location;
        }
        return original;
    }

    public static ResourceLocation modernLayout(ResourceLocation location) {
        ResourceLocation cached = MODERN_LAYOUTS.get(location);
        if (cached != null) return cached;
        ResourceLocation modern = modernOrBundled(location, FLATTENING, true);
        if (modern == null) modern = location;
        MODERN_LAYOUTS.put(location, modern);
        return modern;
    }

    @Nullable
    private static ResourceLocation modernOrBundled(ResourceLocation location, int since, boolean sharedName) {
        String pack = packOf(location);
        if (pack != null && (!sharedName || formatOf(pack) >= since)) return location;
        ResourceLocation bundled = new ResourceLocation(BUNDLED, location.getPath());
        return exists(bundled) ? bundled : null;
    }

    @Nullable
    public static InputStream itemDefinition(ResourceLocation model) {
        String namespace = model.getNamespace();
        String name = model.getPath();
        if (name.startsWith(ITEM_MODELS)) {
            name = name.substring(ITEM_MODELS.length());
        } else if (name.startsWith("item/")) {
            name = name.substring("item/".length());
        }

        List<String> candidates = new ArrayList<>();
        Entry entry = VANILLA.equals(namespace) ? BY_LEGACY.get(ITEM_MODELS + name + ".json") : null;
        if (entry != null) {
            for (int i = entry.names.length - 1; i >= 0; i--) {
                String path = entry.names[i].path;
                candidates.add(path.substring(ITEM_MODELS.length(), path.length() - ".json".length()));
            }
        }
        if (entry == null || !entry.reused) candidates.add(name);

        for (String candidate : candidates) {
            ResourceLocation definition = new ResourceLocation(namespace, "items/" + candidate + ".json");
            ResourceLocation redirect = RpoHandler.getRedirect(definition);
            ResourceLocation target = redirect != null ? redirect : definition;
            if (!target.getPath().endsWith(".json")) {
                target = new ResourceLocation(target.getNamespace(), target.getPath() + ".json");
            }
            try {
                return Minecraft.getMinecraft().getResourceManager().getResource(target).getInputStream();
            } catch (IOException ignored) {
            }
        }
        return null;
    }

    @Nullable
    public static LegacyItem legacyItem(String id, int format) {
        return format < FLATTENING ? null : ITEMS.get(id);
    }

    @Nullable
    public static BlockMatch[] legacyBlocks(String name, int format) {
        return format < FLATTENING ? null : BLOCKS.get(name);
    }

    public static String[] entityModels(String id) {
        String[] models = ENTITY_MODELS.get(id);
        return models != null ? models : new String[]{id};
    }

    public static List<ModelPart> modelParts(String modelClass) {
        return MODEL_PARTS.getOrDefault(modelClass, List.of());
    }

    @Nullable
    public static String partHost(String part) {
        return PART_HOSTS.get(part);
    }

    @Nullable
    public static Map<String, String> transformAliases(String model) {
        return TRANSFORM_ALIASES.get(model);
    }

    @Nullable
    public static Map<String, String> reparents(String model) {
        return REPARENTS.get(model);
    }

    public static boolean exists(@Nullable ResourceLocation location) {
        return packOf(location) != null;
    }

    @Nullable
    private static String packOf(@Nullable ResourceLocation location) {
        if (location == null) return null;
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
            return resource.getResourcePackName();
        } catch (Exception e) {
            return null;
        }
    }

    private static int priority(@Nullable String pack) {
        List<ResourcePackRepository.Entry> entries = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).getResourcePackName().equals(pack)) return i;
        }
        return -1;
    }

    public static final class BlockMatch {
        private final String block;
        private final String[] state;

        private BlockMatch(String definition) {
            String[] parts = definition.split(":");
            block = parts[0];
            state = new String[(parts.length - 1) * 2];
            for (int i = 1; i < parts.length; i++) {
                int split = parts[i].indexOf('=');
                state[(i - 1) * 2] = parts[i].substring(0, split);
                state[(i - 1) * 2 + 1] = parts[i].substring(split + 1);
            }
        }

        public boolean matches(IBlockState blockState) {
            ResourceLocation id = blockState.getBlock().getRegistryName();
            if (id == null || !VANILLA.equals(id.getNamespace()) || !block.equals(id.getPath())) return false;
            for (int i = 0; i < state.length; i += 2) {
                if (!state[i + 1].equals(valueOf(blockState, state[i]))) return false;
            }
            return true;
        }

        @Nullable
        private static String valueOf(IBlockState blockState, String name) {
            for (IProperty<?> property : blockState.getPropertyKeys()) {
                if (property.getName().equals(name)) return valueName(blockState, property);
            }
            return null;
        }

        private static <T extends Comparable<T>> String valueName(IBlockState blockState, IProperty<T> property) {
            return property.getName(blockState.getValue(property));
        }
    }
}
