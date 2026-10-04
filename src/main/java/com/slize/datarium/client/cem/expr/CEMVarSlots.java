package com.slize.datarium.client.cem.expr;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Global "var."/"varb." short-name -> slot table.
 * Global because a {@link CEMRenderContext} is shared by a model and its layer models (sheep -> sheep_wool).
 */
public final class CEMVarSlots {
    private static final Map<String, Integer> SLOTS = new ConcurrentHashMap<>();
    private static final AtomicInteger NEXT = new AtomicInteger();

    private CEMVarSlots() {}

    public static int slotFor(String shortName) {
        return SLOTS.computeIfAbsent(shortName, _ -> NEXT.getAndIncrement());
    }

    static Map<String, Integer> all() {
        return SLOTS;
    }
}