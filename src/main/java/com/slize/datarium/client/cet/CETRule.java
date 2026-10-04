package com.slize.datarium.client.cet;

import com.slize.datarium.client.cet.property.CETProperty;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CETRule {
    public final int ruleNumber;
    public final String propertyFile;
    private final Integer[] suffixNumbers;
    @Nullable private final int[] weights;
    private final int weightTotal;
    private final int seedOffset;
    private final boolean seedFromVehicle;
    private final CETProperty[] properties;
    private final boolean alwaysMet;
    private final boolean updates;

    static final CETRule DEFAULT_RETURN = new CETRule();

    private CETRule() {
        ruleNumber = 0;
        propertyFile = "default setter";
        suffixNumbers = new Integer[]{1};
        weights = null;
        weightTotal = 0;
        seedOffset = 0;
        seedFromVehicle = false;
        properties = new CETProperty[0];
        alwaysMet = true;
        updates = false;
    }

    public CETRule(String propertyFile, int ruleNumber, Integer[] suffixes, @Nullable Integer[] weights,
                   int seedOffset, @Nullable String seedSource, CETProperty... properties) {
        this.propertyFile = propertyFile;
        this.ruleNumber = ruleNumber;
        this.properties = properties;
        this.alwaysMet = properties.length == 0;
        this.seedOffset = seedOffset;
        boolean vehicle = false;
        if (seedSource != null && !seedSource.trim().isEmpty()) {
            if ("vehicle".equals(seedSource.trim())) vehicle = true;
            else if (!"entity".equals(seedSource.trim())) {
                CETUtils.warn("Random Property file [" + propertyFile + "] rule # [" + ruleNumber + "] has invalid seed source [" + seedSource + "], ignoring");
            }
        }
        this.seedFromVehicle = vehicle;
        this.suffixNumbers = suffixes;

        if (weights == null || weights.length == 0) {
            this.weights = null;
            this.weightTotal = 0;
        } else {
            Integer[] adjusted = weights;
            if (weights.length != suffixes.length) {
                adjusted = new Integer[suffixes.length];
                System.arraycopy(weights, 0, adjusted, 0, Math.min(weights.length, suffixes.length));
                if (weights.length >= suffixes.length) {
                    CETUtils.warn("Random Property file [" + propertyFile + "] rule # [" + ruleNumber + "] has more weights than suffixes, trimming to match");
                } else {
                    CETUtils.warn("Random Property file [" + propertyFile + "] rule # [" + ruleNumber + "] has more suffixes than weights, expanding to match");
                    int average = Arrays.stream(weights).mapToInt(Integer::intValue).sum() / weights.length;
                    for (int i = weights.length; i < adjusted.length; i++) adjusted[i] = average;
                }
            }
            int total = 0;
            int[] cumulative = new int[adjusted.length];
            for (int i = 0; i < adjusted.length; i++) {
                int weight = adjusted[i];
                if (weight < 0) {
                    total = 0;
                    break;
                }
                total += weight;
                cumulative[i] = total;
            }
            this.weights = cumulative;
            this.weightTotal = total;
        }
        boolean anyUpdates = false;
        for (CETProperty property : properties) {
            if (property.canPropertyUpdate()) {
                anyUpdates = true;
                break;
            }
        }
        this.updates = anyUpdates;
    }

    public boolean isAlwaysMet() {
        return alwaysMet;
    }

    public Set<Integer> getSuffixSet() {
        return new HashSet<>(Arrays.asList(suffixNumbers));
    }

    public boolean matches(@Nullable CETSubject subject, boolean isUpdate, @Nullable CETLru.Booleans canUpdate) {
        if (alwaysMet) return true;
        if (subject == null) return false;
        if (updates && canUpdate != null) canUpdate.put(subject.uuid(), true);
        try {
            for (CETProperty property : properties) {
                if (!property.test(subject, isUpdate)) return false;
            }
            return true;
        } catch (Exception e) {
            CETUtils.warn("Random Property file [" + propertyFile + "] rule # [" + ruleNumber + "] failed with Exception:\n" + e.getMessage());
            return false;
        }
    }

    public int getVariantSuffix(CETSubject subject) {
        if (this == DEFAULT_RETURN) return 1;
        int seed = seedFromVehicle ? subject.optifineVehicleId() : subject.optifineId();
        if (seedOffset != 0) seed ^= CETUtils.optifineHashing(seedOffset);
        if (weightTotal == 0 || weights == null) {
            return suffixNumbers[Math.floorMod(Math.abs(seed), suffixNumbers.length)];
        }
        int value = Math.floorMod(Math.abs(seed), weightTotal);
        for (int i = 0; i < weights.length; i++) {
            if (value < weights[i]) return suffixNumbers[i];
        }
        return 0;
    }

    public void cacheInitialResults(CETSubject subject) {
        for (CETProperty property : properties) {
            if (!property.canPropertyUpdate()) property.cacheInitialResult(subject);
        }
    }
}
