package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.api.CEMAnimationApi.AnimationHook;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

public final class CEMApiState {
    private CEMApiState() {}

    private static final List<Predicate<Object>> PAUSE_CONDITIONS = new CopyOnWriteArrayList<>();
    private static final Set<Object> PAUSED = ConcurrentHashMap.newKeySet();
    private static final List<Predicate<Object>> VANILLA_CONDITIONS = new CopyOnWriteArrayList<>();
    private static final Set<Object> FORCED_VANILLA = ConcurrentHashMap.newKeySet();
    private static final List<AnimationHook> HOOKS = new CopyOnWriteArrayList<>();

    @Nullable
    private static Object keyOf(@Nullable Object subject) {
        if (subject instanceof Entity entity) return entity.getUniqueID();
        if (subject instanceof TileEntity tile) return tile.getPos();
        return null;
    }

    public static void addPauseCondition(Predicate<Object> condition) {
        PAUSE_CONDITIONS.add(condition);
    }

    public static void addVanillaCondition(Predicate<Object> condition) {
        VANILLA_CONDITIONS.add(condition);
    }

    public static void addHook(AnimationHook hook) {
        HOOKS.add(hook);
    }

    public static boolean pause(Object subject) {
        Object key = keyOf(subject);
        if (key == null) return false;
        PAUSED.add(key);
        return true;
    }

    public static boolean resume(Object subject) {
        Object key = keyOf(subject);
        if (key == null) return false;
        PAUSED.remove(key);
        return true;
    }

    public static boolean lockVanilla(Object subject) {
        Object key = keyOf(subject);
        if (key == null) return false;
        FORCED_VANILLA.add(key);
        return true;
    }

    public static boolean unlockVanilla(Object subject) {
        Object key = keyOf(subject);
        if (key == null) return false;
        FORCED_VANILLA.remove(key);
        return true;
    }

    public static boolean isPaused(@Nullable Object subject) {
        return matches(subject, PAUSED, PAUSE_CONDITIONS);
    }

    public static boolean forcesVanilla(@Nullable Object subject) {
        return matches(subject, FORCED_VANILLA, VANILLA_CONDITIONS);
    }

    private static boolean matches(@Nullable Object subject, Set<Object> keys, List<Predicate<Object>> conditions) {
        if (subject == null) return false;
        if (!keys.isEmpty()) {
            Object key = keyOf(subject);
            if (key != null && keys.contains(key)) return true;
        }
        for (Predicate<Object> condition : conditions) {
            try {
                if (condition.test(subject)) return true;
            } catch (RuntimeException e) {
                DatariumMain.LOGGER.debug("[CEM] API condition failed", e);
            }
        }
        return false;
    }

    public static boolean hasHooks() {
        return !HOOKS.isEmpty();
    }

    public static boolean startAnimation(@Nullable Object subject, String modelName) {
        boolean cancelled = false;
        for (AnimationHook hook : HOOKS) {
            try {
                if (!hook.onAnimationStart(subject, modelName, cancelled)) cancelled = true;
            } catch (RuntimeException e) {
                DatariumMain.LOGGER.debug("[CEM] API animation hook failed", e);
            }
        }
        return !cancelled;
    }

    public static void endAnimation(@Nullable Object subject, String modelName, boolean cancelled) {
        for (AnimationHook hook : HOOKS) {
            try {
                hook.onAnimationEnd(subject, modelName, cancelled);
            } catch (RuntimeException e) {
                DatariumMain.LOGGER.debug("[CEM] API animation hook failed", e);
            }
        }
    }
}
