package com.slize.datarium.client.cet.property;

import com.slize.datarium.client.cet.CETManager;
import com.slize.datarium.client.cet.CETNbt;
import com.slize.datarium.client.cet.CETSubject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public final class ExternalProperties {
    private ExternalProperties() {}

    public static final class CalendarField extends GenericProperties.IntegerArrayProperty {
        private final String[] ids;
        private final int field;

        CalendarField(Properties p, int n, int field, String... ids) throws Invalid {
            super(GenericProperties.readIntegers(p, n, ids));
            this.ids = ids;
            this.field = field;
        }

        @Override
        protected int getValue(CETSubject subject) {
            return Calendar.getInstance().get(field);
        }

        @Override
        public String[] getPropertyIds() {
            return ids;
        }
    }

    public static final class IntValue extends GenericProperties.IntegerArrayProperty {
        private final String[] ids;
        private final ToIntFunction<CETSubject> value;

        IntValue(Properties p, int n, ToIntFunction<CETSubject> value, String... ids) throws Invalid {
            super(GenericProperties.readIntegers(p, n, ids));
            this.ids = ids;
            this.value = value;
        }

        @Override
        protected int getValue(CETSubject subject) {
            return value.applyAsInt(subject);
        }

        @Override
        public String[] getPropertyIds() {
            return ids;
        }
    }

    public static final class Language extends GenericProperties.StringArrayOrRegexProperty {
        Language(Properties p, int n) throws Invalid {
            super(read(p, n, "language"));
        }

        @Override
        protected String getValue(CETSubject subject) {
            return Minecraft.getMinecraft().gameSettings.language;
        }

        @Override
        protected boolean forceLowerCase() {
            return false;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"language"};
        }
    }

    public static final class ModLoaded extends GenericProperties.StringArrayOrRegexProperty {
        private final boolean matched;

        ModLoaded(Properties p, int n) throws Invalid {
            super(read(p, n, "modLoaded", "modsLoaded"));
            boolean found = false;
            for (String modId : Loader.instance().getIndexedModList().keySet()) {
                if (matcher.test(modId)) {
                    found = true;
                    break;
                }
            }
            matched = found;
        }

        @Override
        public boolean test(CETSubject subject, boolean isUpdate) {
            return matched;
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
            return new String[]{"modLoaded", "modsLoaded"};
        }
    }

    public static final class ResourcePackLoaded extends GenericProperties.StringArrayOrRegexProperty {
        ResourcePackLoaded(Properties p, int n) throws Invalid {
            super(read(p, n, "resourcepack", "resourcepackLoaded"));
        }

        @Override
        public boolean test(CETSubject subject, boolean isUpdate) {
            List<String> packs = new ArrayList<>();
            packs.add("vanilla");
            for (ResourcePackRepository.Entry entry : Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries()) {
                packs.add(entry.getResourcePackName());
                packs.add("file/" + entry.getResourcePackName());
            }
            for (String pack : packs) {
                if (matcher.test(pack)) return true;
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
            return new String[]{"resourcepack", "resourcepackLoaded"};
        }
    }

    public static final class Hardcore extends GenericProperties.BooleanProperty {
        Hardcore(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "hardcore"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            World world = subject.world();
            return world == null ? null : world.getWorldInfo().isHardcoreModeEnabled();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"hardcore"};
        }
    }

    public static final class UsingShaders extends GenericProperties.BooleanProperty {
        UsingShaders(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "usingShaders"));
        }

        @Override
        protected Boolean getValue(CETSubject subject) {
            return false;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"usingShaders"};
        }
    }

    public static final class RegionalDifficulty extends GenericProperties.FloatRangeProperty {
        RegionalDifficulty(Properties p, int n) throws Invalid {
            super(read(p, n, "regionalDifficulty", "regional_difficulty"));
        }

        @Override
        protected Float getValue(CETSubject subject) {
            World world = subject.world();
            return world == null ? 0F : world.getDifficultyForLocation(subject.blockPos()).getAdditionalDifficulty();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"regionalDifficulty", "regional_difficulty"};
        }
    }

    public static final class MinecraftVersion extends CETProperty {
        private final List<Predicate<int[]>> ranges = new ArrayList<>();
        private final int[] version = parseVersion(ForgeVersion.mcVersion);

        MinecraftVersion(Properties p, int n) throws Invalid {
            String input = read(p, n, "minecraftVersion");
            for (String token : input.replaceAll("[^0-9.\\s-]", "").trim().split("\\s+")) {
                String[] parts = token.split("(?<!^|-)-");
                if (parts.length == 0 || parts[0].isEmpty()) continue;
                int[] left = parseVersion(parts[0]);
                if (parts.length < 2) {
                    ranges.add(v -> compare(v, left) == 0 && v.length == left.length);
                } else {
                    int[] right = parseVersion(parts[1]);
                    int[] min = compare(left, right) <= 0 ? left : right;
                    int[] max = min == left ? right : left;
                    ranges.add(v -> compare(v, min) >= 0 && compare(v, max) <= 0);
                }
            }
            if (ranges.isEmpty()) throw new Invalid("minecraftVersion property was broken");
        }

        private static int[] parseVersion(String text) {
            String[] parts = text.split("\\.");
            int[] out = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                try {
                    out[i] = Integer.parseInt(parts[i]);
                } catch (NumberFormatException e) {
                    out[i] = 0;
                }
            }
            return out;
        }

        private static int compare(int[] a, int[] b) {
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                if (a[i] != b[i]) return Integer.compare(a[i], b[i]);
            }
            return Integer.compare(a.length, b.length);
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            for (Predicate<int[]> range : ranges) {
                if (range.test(version)) return true;
            }
            return false;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"minecraftVersion"};
        }
    }

    static IntValue textureSuffix(Properties p, int n) throws CETProperty.Invalid {
        return new IntValue(p, n, s -> Math.max(CETManager.lastSuffixOf(s.uuid()), 0), "textureSuffix", "texture_suffix");
    }

    static IntValue textureRule(Properties p, int n) throws CETProperty.Invalid {
        return new IntValue(p, n, s -> Math.max(CETManager.lastRuleOf(s.uuid()), 0), "textureRule", "texture_rule");
    }

    static IntValue difficulty(Properties p, int n) throws CETProperty.Invalid {
        return new IntValue(p, n, s -> s.world() == null ? 0 : s.world().getDifficulty().getId(), "difficulty");
    }

    static IntValue clientGameMode(Properties p, int n) throws CETProperty.Invalid {
        return new IntValue(p, n, s -> {
            Minecraft mc = Minecraft.getMinecraft();
            return mc.playerController == null ? -1 : mc.playerController.getCurrentGameType().getID();
        }, "clientGameMode");
    }

    static OptiFineProperties.Nbt clientNbt(Properties p, int n) throws CETProperty.Invalid {
        return new OptiFineProperties.Nbt(p, n, "nbtClient", s -> CETNbt.of(Minecraft.getMinecraft().player));
    }
}
