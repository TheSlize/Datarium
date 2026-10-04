package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.api.CEMAnimationApi.AnimationFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

public final class CEMApiRegistry {
    private CEMApiRegistry() {}

    private static final Map<String, Integer> VARIABLE_SLOTS = new ConcurrentHashMap<>();
    private static final List<DoubleSupplier> VARIABLES = new ArrayList<>();

    private static final Map<String, Integer> FUNCTION_SLOTS = new ConcurrentHashMap<>();
    private static final List<AnimationFunction> FUNCTIONS = new ArrayList<>();
    private static final List<Boolean> FUNCTION_PURE = new ArrayList<>();

    public static synchronized void registerVariable(String name, DoubleSupplier supplier) {
        Integer slot = VARIABLE_SLOTS.get(name);
        if (slot != null) {
            VARIABLES.set(slot, supplier);
        } else {
            VARIABLE_SLOTS.put(name, VARIABLES.size());
            VARIABLES.add(supplier);
        }
    }

    public static synchronized void registerFunction(String name, AnimationFunction function, boolean pure) {
        Integer slot = FUNCTION_SLOTS.get(name);
        if (slot != null) {
            FUNCTIONS.set(slot, function);
            FUNCTION_PURE.set(slot, pure);
        } else {
            FUNCTION_SLOTS.put(name, FUNCTIONS.size());
            FUNCTIONS.add(function);
            FUNCTION_PURE.add(pure);
        }
    }

    static int variableSlot(String name) {
        return VARIABLE_SLOTS.getOrDefault(name, -1);
    }

    static int functionSlot(String name) {
        return FUNCTION_SLOTS.getOrDefault(name, -1);
    }

    static boolean isPure(int slot) {
        return FUNCTION_PURE.get(slot);
    }

    static double variable(int slot) {
        try {
            return VARIABLES.get(slot).getAsDouble();
        } catch (RuntimeException e) {
            DatariumMain.LOGGER.debug("[CEM] API variable failed", e);
            return 0;
        }
    }

    static double call(int slot, double[] args) {
        try {
            return FUNCTIONS.get(slot).apply(args);
        } catch (RuntimeException e) {
            DatariumMain.LOGGER.debug("[CEM] API function failed", e);
            return Double.NaN;
        }
    }
}
