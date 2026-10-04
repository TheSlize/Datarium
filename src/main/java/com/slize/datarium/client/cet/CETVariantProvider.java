package com.slize.datarium.client.cet;

import com.slize.datarium.client.cet.property.CETProperties;
import com.slize.datarium.client.cet.property.GenericProperties;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public interface CETVariantProvider {

    boolean entityCanUpdate(UUID uuid);

    Set<Integer> getAllSuffixes();

    int size();

    int getSuffix(@Nullable CETSubject subject);

    @Nullable
    static CETVariantProvider of(ResourceLocation propertiesId, ResourceLocation vanillaId, String... suffixKeys) {
        PropertiesProvider properties = PropertiesProvider.of(propertiesId, vanillaId, suffixKeys);
        TrueRandomProvider random = TrueRandomProvider.of(vanillaId);

        if (properties == null && vanillaId.getPath().endsWith(".png") && "minecraft".equals(vanillaId.getNamespace())
                && vanillaId.getPath().contains("_")) {
            String fallback = vanillaId.getPath().replaceAll("_(tame|angry|nectar|shooting|cold)", "");
            properties = PropertiesProvider.of(new ResourceLocation(fallback.replace(".png", ".properties")),
                    new ResourceLocation(fallback), suffixKeys);
        }

        if (random == null && properties == null) return null;
        if (properties == null) return random;
        if (random == null) return properties;
        return properties.isHigherPackThan(random.packName) ? properties : random;
    }

    final class PropertiesProvider implements CETVariantProvider {
        private final List<CETRule> rules;
        private final CETLru.Booleans canUpdate = new CETLru.Booleans();
        private final String packName;
        private BiConsumer<CETSubject, CETRule> onMeetsRule = (subject, rule) -> {};

        private PropertiesProvider(ResourceLocation propertiesId, List<CETRule> rules) {
            this.rules = rules;
            String pack = CETUtils.packOf(propertiesId);
            this.packName = pack == null ? "vanilla" : pack;
        }

        @Nullable
        public static PropertiesProvider of(ResourceLocation initialPropertiesId, ResourceLocation vanillaId, String... suffixKeys) {
            ResourceLocation propertiesId = CETDirectory.getDirectoryVersionOf(initialPropertiesId);
            if (propertiesId == null) return null;
            try {
                Properties properties = CETUtils.readProperties(propertiesId);
                if (properties == null) {
                    CETUtils.log("Ignoring properties file that was null @ " + propertiesId);
                    return null;
                }
                if (vanillaId.getPath().endsWith(".png")) CETManager.grabSpecialProperties(properties, CETState.subject());

                List<CETRule> rules = readRules(properties, propertiesId, suffixKeys);
                if (rules.isEmpty()) {
                    CETUtils.log("Ignoring properties file that failed to load any cases @ " + propertiesId);
                    return null;
                }
                if (!rules.get(rules.size() - 1).isAlwaysMet()) rules.add(CETRule.DEFAULT_RETURN);

                String propertiesPack = CETUtils.packOf(propertiesId);
                String vanillaPack = CETUtils.packOf(vanillaId);
                if (propertiesPack != null && propertiesPack.equals(CETUtils.highestPackOfTwo(propertiesPack, vanillaPack))) {
                    return new PropertiesProvider(propertiesId, rules);
                }
            } catch (IllegalStateException e) {
                if (!propertiesId.toString().contains("optifine/cit/")) {
                    CETUtils.warn("Ignoring properties file with problem: " + propertiesId + "\n" + e.getMessage());
                }
            } catch (Exception e) {
                CETUtils.warn("Ignoring properties file that caused unexpected Exception: " + propertiesId + "\n" + e);
            }
            return null;
        }

        public static List<CETRule> readRules(Properties properties, ResourceLocation propertiesId, String... suffixKeys) {
            List<Integer> numbers = ruleNumbers(properties);
            if (numbers.isEmpty()) {
                throw new IllegalStateException("Properties file [" + propertiesId + "] contains no rules, this is invalid.");
            }
            if (numbers.get(0) < 1) {
                throw new IllegalStateException("Properties file [" + propertiesId + "] contains rule numbers less than 1, this is invalid.");
            }
            List<CETRule> rules = new ArrayList<>();
            for (int number : numbers) {
                Integer[] suffixes = GenericProperties.readIntegers(properties, number, suffixKeys);
                if (suffixes != null) {
                    for (Integer suffix : suffixes) {
                        if (suffix < 1) throw new IllegalStateException("Invalid suffix: [" + suffix + "] in " + Arrays.toString(suffixes));
                    }
                }
                if (suffixes != null && suffixes.length != 0) {
                    rules.add(new CETRule(propertiesId.toString(), number, suffixes,
                            GenericProperties.readIntegers(properties, number, "weights"),
                            seedOffset(properties, number),
                            properties.getProperty("seedSource." + number),
                            CETProperties.getAllOfRule(properties, number)));
                } else {
                    CETUtils.warn("property number \"" + number + ". in file \"" + propertiesId + ". failed to read.");
                }
            }
            return rules;
        }

        private static int seedOffset(Properties properties, int number) {
            String value = properties.getProperty("seedOffset." + number);
            if (value != null) {
                try {
                    return Integer.parseInt(value.trim());
                } catch (NumberFormatException ignored) {
                }
            }
            return 0;
        }

        private static List<Integer> ruleNumbers(Properties properties) {
            Set<Integer> found = new TreeSet<>();
            for (String key : properties.stringPropertyNames()) {
                String[] split = key.split("\\.");
                if (split.length >= 2 && !split[1].trim().isEmpty()) {
                    String digits = split[1].replaceAll("\\D", "");
                    if (!digits.isEmpty()) {
                        try {
                            found.add(Integer.parseInt(digits));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
            return new ArrayList<>(found);
        }

        public void setOnMeetsRuleHook(@Nullable BiConsumer<CETSubject, CETRule> hook) {
            if (hook != null) onMeetsRule = hook;
        }

        public String getPackName() {
            return packName;
        }

        public boolean isHigherPackThan(@Nullable String other) {
            return packName.equals(CETUtils.highestPackOfTwo(packName, other));
        }

        @Override
        public boolean entityCanUpdate(UUID uuid) {
            return canUpdate.getBoolean(uuid);
        }

        @Override
        public Set<Integer> getAllSuffixes() {
            Set<Integer> all = new HashSet<>();
            for (CETRule rule : rules) all.addAll(rule.getSuffixSet());
            return all;
        }

        @Override
        public int size() {
            return rules.size();
        }

        @Override
        public int getSuffix(@Nullable CETSubject subject) {
            if (subject == null) return 0;
            UUID id = subject.uuid();
            boolean testedBefore = canUpdate.containsKey(id);
            int result = 0;
            for (CETRule rule : rules) {
                if (rule.matches(subject, testedBefore, canUpdate)) {
                    onMeetsRule.accept(subject, rule);
                    result = rule.getVariantSuffix(subject);
                    break;
                }
            }
            if (!testedBefore && canUpdate.getBoolean(id)) {
                for (CETRule rule : rules) rule.cacheInitialResults(subject);
            }
            if (result > 0) return result;
            onMeetsRule.accept(subject, null);
            return 0;
        }
    }

    final class TrueRandomProvider implements CETVariantProvider {
        private final int[] suffixes;
        private final String packName;

        private TrueRandomProvider(String packName, int[] suffixes) {
            this.suffixes = suffixes;
            this.packName = packName;
        }

        @Nullable
        public static TrueRandomProvider of(ResourceLocation vanillaId) {
            ResourceLocation variant2 = CETUtils.addVariantNumberSuffix(vanillaId, 2);
            ResourceLocation second = variant2 == null ? null : CETDirectory.getDirectoryVersionOf(variant2);
            if (second == null) return null;

            String secondPack = CETUtils.packOf(second);
            String vanillaPack = CETUtils.packOf(vanillaId);
            if (secondPack == null || !secondPack.equals(CETUtils.highestPackOfTwo(secondPack, vanillaPack))) return null;

            List<Integer> found = new ArrayList<>();
            found.add(1);
            found.add(2);
            boolean noSkip = !CETConfig.optifineAllowWeirdSkipsInTrueRandom;
            for (int i = 3; i < found.size() + 10; i++) {
                ResourceLocation candidate = CETUtils.addVariantNumberSuffix(vanillaId, i);
                if (candidate != null && CETDirectory.getDirectoryVersionOf(candidate) != null) {
                    found.add(i);
                } else if (noSkip) {
                    break;
                }
            }
            if (found.get(found.size() - 1) != found.size()) {
                CETUtils.warn("Random suffixes [" + found + "] are not sequential for " + vanillaId + " in pack " + secondPack
                        + " this is not recommended but has been enabled in the optifine compat settings.");
            }
            return new TrueRandomProvider(secondPack, found.stream().mapToInt(Integer::intValue).toArray());
        }

        @Override
        public boolean entityCanUpdate(UUID uuid) {
            return false;
        }

        @Override
        public Set<Integer> getAllSuffixes() {
            return Arrays.stream(suffixes).boxed().collect(Collectors.toSet());
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public int getSuffix(@Nullable CETSubject subject) {
            if (subject == null) return 0;
            return suffixes[Math.floorMod(Math.abs(subject.optifineId()), suffixes.length)];
        }
    }
}
