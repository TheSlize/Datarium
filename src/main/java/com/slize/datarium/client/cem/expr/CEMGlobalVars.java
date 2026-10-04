package com.slize.datarium.client.cem.expr;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public final class CEMGlobalVars {
    private static final Map<String, Integer> SLOTS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT = new AtomicInteger();

    private static double[] values = new double[0];
    private static boolean[] boolSet = new boolean[0];
    private static boolean[] bools = new boolean[0];

    private CEMGlobalVars() {}

    public static int slotFor(String name) {
        return SLOTS.computeIfAbsent(name, _ -> NEXT.getAndIncrement());
    }

    static double get(int slot) {
        return slot < values.length ? values[slot] : 0;
    }

    static double getBool(int slot) {
        if (slot < boolSet.length && boolSet[slot]) return bools[slot] ? 1 : 0;
        return get(slot);
    }

    public static void set(int slot, double value) {
        if (slot >= values.length) values = Arrays.copyOf(values, slot + 1);
        values[slot] = value;
    }

    public static void setBool(int slot, boolean value) {
        if (slot >= boolSet.length) {
            boolSet = Arrays.copyOf(boolSet, slot + 1);
            bools = Arrays.copyOf(bools, slot + 1);
        }
        boolSet[slot] = true;
        bools[slot] = value;
    }

    public static void clear() {
        Arrays.fill(values, 0);
        Arrays.fill(boolSet, false);
    }

    static Map<String, Integer> all() {
        return SLOTS;
    }
}
