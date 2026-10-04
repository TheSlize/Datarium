package com.slize.datarium.client.cet.property;

import com.slize.datarium.client.cet.CETNbt;
import com.slize.datarium.client.cet.CETSubject;
import com.slize.datarium.client.cet.CETUtils;
import com.slize.datarium.mixin.accessors.AccessorEntityVillager;
import com.slize.datarium.util.PackConverter;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityShulker;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.entity.monster.EntityZombieVillager;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.passive.EntitySheep;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.VillagerRegistry;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OptiFineProperties {
    private static final Pattern QUOTED = Pattern.compile("([^\"]\\S*|\".+?\")\\s*");
    private static final Map<String, String> MODERN_BIOMES = new HashMap<>();

    static {
        String[][] aliases = {
                {"nether_wastes", "hell"}, {"the_end", "sky"}, {"the_void", "void"},
                {"snowy_plains", "ice_flats"}, {"snowy_tundra", "ice_flats"}, {"ice_plains", "ice_flats"},
                {"ice_spikes", "mutated_ice_flats"}, {"snowy_mountains", "ice_mountains"}, {"snowy_slopes", "ice_mountains"},
                {"mushroom_fields", "mushroom_island"}, {"mushroom_field_shore", "mushroom_island_shore"},
                {"windswept_hills", "extreme_hills"}, {"mountains", "extreme_hills"}, {"stony_peaks", "extreme_hills"},
                {"windswept_forest", "extreme_hills_with_trees"}, {"wooded_mountains", "extreme_hills_with_trees"},
                {"windswept_gravelly_hills", "mutated_extreme_hills"}, {"gravelly_mountains", "mutated_extreme_hills"},
                {"meadow", "smaller_extreme_hills"}, {"mountain_edge", "smaller_extreme_hills"}, {"extreme_hills_edge", "smaller_extreme_hills"},
                {"badlands", "mesa"}, {"wooded_badlands", "mesa_rock"}, {"wooded_badlands_plateau", "mesa_rock"},
                {"badlands_plateau", "mesa_clear_rock"}, {"eroded_badlands", "mutated_mesa"},
                {"snowy_taiga", "taiga_cold"}, {"snowy_taiga_hills", "taiga_cold_hills"}, {"snowy_beach", "cold_beach"},
                {"stony_shore", "stone_beach"}, {"stone_shore", "stone_beach"}, {"beach", "beaches"},
                {"sparse_jungle", "jungle_edge"}, {"old_growth_pine_taiga", "redwood_taiga"}, {"giant_tree_taiga", "redwood_taiga"},
                {"old_growth_spruce_taiga", "mutated_redwood_taiga"}, {"giant_spruce_taiga", "mutated_redwood_taiga"},
                {"sunflower_plains", "mutated_plains"}, {"flower_forest", "mutated_forest"}, {"dark_forest", "roofed_forest"},
                {"dark_forest_hills", "mutated_roofed_forest"}, {"savanna_plateau", "savanna_rock"},
                {"windswept_savanna", "mutated_savanna"}, {"shattered_savanna", "mutated_savanna"},
                {"wooded_hills", "forest_hills"}, {"swamp", "swampland"}, {"swamp_hills", "mutated_swampland"},
                {"old_growth_birch_forest", "mutated_birch_forest"}, {"tall_birch_forest", "mutated_birch_forest"},
                {"desert_lakes", "mutated_desert"}, {"taiga_mountains", "mutated_taiga"}, {"snowy_taiga_mountains", "mutated_taiga_cold"},
                {"modified_jungle", "mutated_jungle"}, {"modified_jungle_edge", "mutated_jungle_edge"}
        };
        for (String[] alias : aliases) MODERN_BIOMES.put(alias[0], alias[1]);
    }

    private OptiFineProperties() {}

    public static final class Baby extends GenericProperties.BooleanProperty {
        Baby(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "baby"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            EntityLivingBase living = subject.living();
            return living != null ? living.isChild() : null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"baby"};
        }
    }

    public static final class Biomes extends GenericProperties.StringArrayOrRegexProperty {
        Biomes(String data) throws Invalid {
            super(data);
        }

        static Biomes create(Properties p, int n) throws Invalid {
            String data = read(p, n, "biomes", "biome");
            if (data.startsWith("regex:") || data.startsWith("pattern:")) return new Biomes(data);
            boolean prints = data.startsWith("print:");
            StringBuilder builder = new StringBuilder(prints ? "print:" : "");
            for (String token : (prints ? data.substring(6) : data).split("\\s+")) {
                builder.append(normalizeBiome(token)).append(' ');
            }
            return new Biomes(builder.toString().trim().toLowerCase(Locale.ROOT));
        }

        private static String normalizeBiome(String token) {
            String negate = token.startsWith("!") ? "!" : "";
            String name = token.substring(negate.length()).trim().replaceAll("^minecraft:", "");
            if (!name.contains("_") && !name.equals(name.toLowerCase(Locale.ROOT))) {
                name = name.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
            }
            name = name.toLowerCase(Locale.ROOT);
            return negate + MODERN_BIOMES.getOrDefault(name, name);
        }

        @Override
        protected boolean forceLowerCase() {
            return true;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            World world = subject.world();
            if (world == null) return null;
            ResourceLocation id = world.getBiome(subject.blockPos()).getRegistryName();
            return id == null ? null : id.toString().replace("minecraft:", "");
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"biomes", "biome"};
        }
    }

    public static class BlocksProperty extends GenericProperties.StringArrayOrRegexProperty {
        private final boolean deepStateCheck;
        private final List<PackConverter.BlockMatch> legacy = new ArrayList<>();
        private final List<PackConverter.BlockMatch> legacyExcluded = new ArrayList<>();

        protected BlocksProperty(Properties p, int n, String... ids) throws Invalid {
            super(read(p, n, ids).replaceAll("(?<=(^| ))minecraft:", ""));
            boolean deep = false;
            if (!usesRegex) {
                int format = CETUtils.formatOf(p);
                for (String value : new ArrayList<>(values)) {
                    boolean negated = value.startsWith("!");
                    PackConverter.BlockMatch[] translated = PackConverter.legacyBlocks(negated ? value.substring(1) : value, format);
                    if (translated == null) continue;
                    values.remove(value);
                    Collections.addAll(negated ? legacyExcluded : legacy, translated);
                }
                for (String value : values) {
                    if (value.contains(":")) {
                        deep = true;
                        break;
                    }
                }
            }
            deepStateCheck = deep;
        }

        static BlocksProperty create(Properties p, int n) throws Invalid {
            return new BlocksProperty(p, n, "blocks", "block");
        }

        protected String blockName(IBlockState state) {
            ResourceLocation id = state.getBlock().getRegistryName();
            String name = id == null ? "" : id.toString().replaceFirst("minecraft:", "");
            if (doPrint) CETUtils.log("Blocks property print (no blockstate data): [" + name + "]");
            return name;
        }

        protected String blockNameWithState(IBlockState state) {
            StringBuilder name = new StringBuilder(blockName(state));
            for (Map.Entry<IProperty<?>, Comparable<?>> entry : state.getProperties().entrySet()) {
                name.append(':').append(entry.getKey().getName()).append('=').append(propertyValue(entry.getKey(), entry.getValue()));
            }
            if (doPrint) CETUtils.log("Blocks property print (with blockstate data): [" + name + "]");
            return name.toString();
        }

        @SuppressWarnings("unchecked")
        private static <T extends Comparable<T>> String propertyValue(IProperty<T> property, Comparable<?> value) {
            return property.getName((T) value);
        }

        protected boolean matches(IBlockState state) {
            for (PackConverter.BlockMatch match : legacyExcluded) {
                if (match.matches(state)) return false;
            }
            for (PackConverter.BlockMatch match : legacy) {
                if (match.matches(state)) return true;
            }
            String name = blockName(state);
            if (!usesRegex && values.contains("!" + name)) return false;
            if (matcher.test(name) || !legacyExcluded.isEmpty()) return true;
            if (usesRegex) return matcher.test(blockNameWithState(state));
            if (!deepStateCheck) return false;
            String full = blockNameWithState(state);
            for (String value : values) {
                if (!value.contains(":")) continue;
                boolean all = true;
                for (String part : value.split(":")) {
                    if (!full.contains(part)) {
                        all = false;
                        break;
                    }
                }
                if (all) return true;
            }
            return false;
        }

        @Nullable
        protected IBlockState[] testingBlocks(CETSubject subject) {
            if (subject.isSpawner()) return new IBlockState[]{Blocks.MOB_SPAWNER.getDefaultState()};
            World world = subject.world();
            if (world == null) return null;
            TileEntity tile = subject.tile();
            if (tile != null) {
                return new IBlockState[]{world.getBlockState(tile.getPos()), world.getBlockState(tile.getPos().down())};
            }
            BlockPos pos = subject.blockPos();
            return new IBlockState[]{world.getBlockState(pos), world.getBlockState(pos.down())};
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            IBlockState[] states = testingBlocks(subject);
            if (states == null) {
                if (doPrint) CETUtils.log("Blocks property print result: [false], because null");
                return false;
            }
            for (IBlockState state : states) {
                if (matches(state)) {
                    if (doPrint) CETUtils.log("Blocks property print result: [true]");
                    return true;
                }
            }
            if (doPrint) CETUtils.log("Blocks property print result: [false]");
            return false;
        }

        @Override
        protected boolean forceLowerCase() {
            return true;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"blocks", "block"};
        }
    }

    public static final class Colors extends GenericProperties.StringArrayOrRegexProperty {
        Colors(Properties p, int n) throws Invalid {
            super(read(p, n, "colors", "collarColors"));
            if (!usesRegex && values.contains("silver")) values.add("light_gray");
            if (!usesRegex && values.contains("lightgray")) values.add("light_gray");
        }

        @Override
        protected boolean forceLowerCase() {
            return true;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            Entity entity = subject.entity();
            EnumDyeColor color = null;
            if (entity instanceof EntityLlama llama) color = llama.getColor();
            else if (entity instanceof EntityShulker shulker) color = shulker.getColor();
            else if (entity instanceof EntityWolf wolf) color = wolf.getCollarColor();
            else if (entity instanceof EntitySheep sheep) color = sheep.getFleeceColor();
            if (color == null) return null;
            return color == EnumDyeColor.SILVER ? "light_gray" : color.getName();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"colors", "collarColors"};
        }
    }

    public static final class Health extends GenericProperties.FloatRangeProperty {
        private final boolean isPercentage;

        Health(Properties p, int n) throws Invalid {
            super(read(p, n, "health"));
            isPercentage = originalInput.contains("%");
        }

        @Nullable
        @Override
        protected Float getValue(CETSubject subject) {
            EntityLivingBase living = subject.living();
            if (living == null) return null;
            float health = living.getHealth();
            return isPercentage ? (float) Math.ceil(health / living.getMaxHealth() * 100) : health;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"health"};
        }
    }

    public static final class Height extends GenericProperties.IntegerArrayProperty {
        Height(Properties p, int n) throws Invalid {
            super(GenericProperties.readIntegers(p, n, "heights", "height"));
        }

        static Height create(Properties p, int n) throws Invalid {
            if (!(p.containsKey("heights." + n) || p.containsKey("height." + n))
                    && (p.containsKey("minHeight." + n) || p.containsKey("maxHeight." + n))) {
                String min = p.containsKey("minHeight." + n) ? p.getProperty("minHeight." + n).trim() : "-64";
                String max = p.containsKey("maxHeight." + n) ? p.getProperty("maxHeight." + n).trim() : "319";
                p.put("heights." + n, min + "-" + max);
            }
            return new Height(p, n);
        }

        @Override
        protected int getValue(CETSubject subject) {
            return subject.blockY();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"heights", "height"};
        }
    }

    public static final class MoonPhase extends GenericProperties.IntegerArrayProperty {
        MoonPhase(Properties p, int n) throws Invalid {
            super(GenericProperties.readIntegers(p, n, "moonPhase"));
        }

        @Override
        protected int getValue(CETSubject subject) {
            World world = subject.world();
            return world == null ? Integer.MIN_VALUE : world.provider.getMoonPhase(world.getWorldTime());
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"moonPhase"};
        }
    }

    public static final class Name extends GenericProperties.StringArrayOrRegexProperty {
        Name(String data) throws Invalid {
            super(data);
        }

        static Name create(Properties p, int n) throws Invalid {
            String data = read(p, n, "name", "names");
            List<String> names = new ArrayList<>();
            if (data.startsWith("regex:") || data.startsWith("pattern:")) {
                names.add(data);
            } else {
                Matcher m = QUOTED.matcher(data);
                while (m.find()) names.add(m.group(1).replace("\"", "").trim());
            }
            return new Name(String.join(" ", names).trim());
        }

        @Override
        protected boolean forceLowerCase() {
            return false;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            return subject.customName();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"name", "names"};
        }
    }

    public static class Nbt extends CETProperty {
        private final Map<String, CETNbt.Tester> testers = new LinkedHashMap<>();
        private final Function<CETSubject, NBTTagCompound> source;
        private final String prefix;

        Nbt(Properties p, int n, String prefix, Function<CETSubject, NBTTagCompound> source) throws Invalid {
            this.prefix = prefix;
            this.source = source;
            String keyPrefix = prefix + "." + n + ".";
            for (String key : p.stringPropertyNames()) {
                if (!key.startsWith(keyPrefix)) continue;
                String path = key.substring(keyPrefix.length());
                String instruction = p.getProperty(key).trim();
                CETNbt.Tester tester = CETNbt.Tester.of(path, instruction);
                if (tester == null) throw new Invalid("NBT failed, instruction was invalid: " + key + "=" + instruction);
                testers.put(path, tester);
            }
            if (testers.isEmpty()) throw new Invalid("NBT failed as the final testing map was empty");
        }

        static Nbt create(Properties p, int n) throws Invalid {
            return new Nbt(p, n, "nbt", CETSubject::nbt);
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            NBTTagCompound tag = source.apply(subject);
            if (tag == null || tag.isEmpty()) return false;
            for (CETNbt.Tester tester : testers.values()) {
                if (!tester.test(tag)) return false;
            }
            return true;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{prefix};
        }
    }

    public static final class Professions extends GenericProperties.StringArrayOrRegexProperty {
        private static final Map<String, String> CAREER_TO_MODERN = new HashMap<>();

        static {
            CAREER_TO_MODERN.put("armor", "armorer");
            CAREER_TO_MODERN.put("weapon", "weaponsmith");
            CAREER_TO_MODERN.put("tool", "toolsmith");
            CAREER_TO_MODERN.put("leather", "leatherworker");
            CAREER_TO_MODERN.put("priest", "cleric");
        }

        Professions(Properties p, int n) throws Invalid {
            super(read(p, n, "professions"));
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            Entity entity = subject.entity();
            VillagerRegistry.VillagerProfession profession = null;
            int careerId = 0;
            int level = 0;
            if (entity instanceof EntityVillager villager) {
                profession = villager.getProfessionForge();
                Entity server = CETNbt.serverEntity(villager);
                AccessorEntityVillager data = (AccessorEntityVillager) (server instanceof EntityVillager ? server : villager);
                careerId = data.datarium$getCareerId();
                level = data.datarium$getCareerLevel();
            } else if (entity instanceof EntityZombieVillager zombie) {
                profession = zombie.getForgeProfession();
            }
            if (profession == null) return false;

            List<String> names = new ArrayList<>();
            ResourceLocation id = profession.getRegistryName();
            if (id != null) names.add(id.getPath().toLowerCase(Locale.ROOT));
            if (careerId > 0) {
                VillagerRegistry.VillagerCareer career = profession.getCareer(careerId - 1);
                if (career != null) {
                    String careerName = career.getName().toLowerCase(Locale.ROOT);
                    names.add(careerName);
                    String modern = CAREER_TO_MODERN.get(careerName);
                    if (modern != null) names.add(modern);
                }
            }
            if (names.contains("priest")) names.add("cleric");

            for (String raw : values) {
                if (raw == null) continue;
                String str = raw.toLowerCase(Locale.ROOT).replaceAll("\\s*", "").replace("minecraft:", "");
                int colon = str.indexOf(':');
                String wanted = colon >= 0 ? str.substring(0, colon) : str;
                if (wanted.isEmpty()) continue;
                for (String name : names) {
                    if (!(name.contains(wanted) || wanted.contains(name))) continue;
                    if (colon < 0) return true;
                    for (String levels : str.substring(colon + 1).split(",")) {
                        if (levels.contains("-")) {
                            int[] range = GenericProperties.intRange(levels);
                            if (level >= range[0] && level <= range[1]) return true;
                        } else if (!levels.replaceAll("\\D", "").isEmpty() && Integer.parseInt(levels.replaceAll("\\D", "")) == level) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        @Override
        protected boolean forceLowerCase() {
            return false;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"professions"};
        }
    }

    public static final class Size extends GenericProperties.IntegerArrayProperty {
        Size(Properties p, int n) throws Invalid {
            super(GenericProperties.readIntegers(p, n, "sizes", "size"));
        }

        @Override
        protected int getValue(CETSubject subject) {
            return subject.entity() instanceof EntitySlime slime ? slime.getSlimeSize() - 1 : 0;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"sizes", "size"};
        }
    }

    public static final class DayTime extends GenericProperties.LongRangeProperty {
        DayTime(Properties p, int n) throws Invalid {
            super(read(p, n, "dayTime"));
        }

        @Nullable
        @Override
        protected Long getValue(CETSubject subject) {
            World world = subject.world();
            return world == null ? null : world.getWorldTime() % 24000;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"dayTime"};
        }
    }

    public static final class Weather extends GenericProperties.StringArrayOrRegexProperty {
        Weather(Properties p, int n) throws Invalid {
            super(read(p, n, "weather"));
            if (!usesRegex && values.contains("rain")) values.add("thunder");
        }

        @Override
        protected boolean forceLowerCase() {
            return true;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            World world = subject.world();
            if (world == null) return null;
            if (world.isThundering()) return "thunder";
            if (world.isRaining()) return "rain";
            return "clear";
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"weather"};
        }
    }
}
