package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Opt-in hit counters for variable reads, function calls and nbt queries.
 * Toggled together with the CEMDebugSystem debug key.
 * I should probably split it into separate keys later though..
 */
public final class CEMProfiler {
    public static volatile boolean enabled = false;

    private static final Map<String, LongAdder> HITS = new ConcurrentHashMap<>();

    private CEMProfiler() {}

    public static void start() {
        HITS.clear();
        enabled = true;
    }

    public static void stopAndDump() {
        enabled = false;
        if (HITS.isEmpty()) {
            DatariumMain.LOGGER.info("[CEM] profiler: no samples captured");
            return;
        }

        List<Map.Entry<String, LongAdder>> sorted = new ArrayList<>(HITS.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()));

        StringBuilder sb = new StringBuilder("[CEM] profiler results (top ").append(Math.min(40, sorted.size()))
                .append(" of ").append(sorted.size()).append(" distinct reads/calls):\n");
        int limit = Math.min(40, sorted.size());
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, LongAdder> e = sorted.get(i);
            sb.append(String.format("  %8d  %s%n", e.getValue().sum(), e.getKey()));
        }
        DatariumMain.LOGGER.info(sb.toString());
        HITS.clear();
    }

    static void hitVar(CEMVarRef ref) {
        String key = switch (ref.kind) {
            case API -> "api:" + ref.name;
            case UNKNOWN -> "unknown:" + ref.name;
            default -> ref.name;
        };
        HITS.computeIfAbsent(key, _ -> new LongAdder()).increment();
    }

    static void hitFn(CEMFunction.Fn fn) {
        HITS.computeIfAbsent("fn:" + fn.name(), _ -> new LongAdder()).increment();
    }

    static void hitNbt(String path) {
        HITS.computeIfAbsent("nbt:" + path, _ -> new LongAdder()).increment();
    }
}