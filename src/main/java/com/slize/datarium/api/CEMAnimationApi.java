package com.slize.datarium.api;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.CEMApiState;
import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.client.cem.CEMRenderHooks;
import com.slize.datarium.client.cem.expr.CEMApiRegistry;
import com.slize.datarium.client.cem.expr.CEMRenderContext;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

// Verbatim ported API endpoints from Entity Model Features
// Tbh I'm not sure if people even use it but I'd rather port a feature if I can.
@SuppressWarnings({"unused", "BooleanMethodIsAlwaysInverted"})
public final class CEMAnimationApi {
    private CEMAnimationApi() {}

    /**
     * A custom function for use in animation math expressions.
     * Arguments are passed already evaluated, in the order they were written in the animation.
     */
    @FunctionalInterface
    public interface AnimationFunction {
        /**
         * @param args the evaluated arguments of the function call, may be empty.
         * @return the result of the function.
         */
        double apply(double[] args);
    }

    /**
     * Animation hook class for receiving callbacks at the start and end of animations.
     */
    public abstract static class AnimationHook {
        /**
         * @param entityOrTile      the {@link Entity} or {@link TileEntity} being animated, or null.
         * @param modelName         the name of the CEM model being animated.
         * @param isCancelledByHook true if a previously run hook has already cancelled this animation.
         * @return true if you want to allow this animation, all hooks will still run as normal either way,
         * but if any hook returns false, the animation will be canceled and not run.
         */
        public boolean onAnimationStart(@Nullable Object entityOrTile, String modelName, boolean isCancelledByHook) {
            return true;
        }

        /**
         * @param entityOrTile       the {@link Entity} or {@link TileEntity} that was animated, or null.
         * @param modelName          the name of the CEM model that was animated.
         * @param wasCancelledByHook true if any hook cancelled this animation.
         */
        public void onAnimationEnd(@Nullable Object entityOrTile, String modelName, boolean wasCancelledByHook) {
        }
    }

    /**
     * Gets the current version of the CEM API.
     * Future versions of the api will endeavor to maintain backwards compatibility,
     * though may depreciate old methods by having them return null or do nothing.
     *
     * @return The current version of the CEM API.
     */
    public static int getApiVersion() {
        return 1;
    }

    /**
     * Gets current rendered entity.
     * This may be either a {@link Entity} or {@link TileEntity} or null.
     *
     * @return the currently rendered entity
     */
    @Nullable
    public static Object getCurrentEntity() {
        CEMRenderContext ctx = CEMRenderHooks.getActiveContext();
        return ctx != null ? ctx.getRenderedObject() : null;
    }

    /**
     * Registers a singleton float variable for use in animation math expressions.
     * Registering a variable with an already registered name replaces the previous one.
     *
     * @param sourceModId  The mod id of the mod registering the variable.
     * @param variableName The name of the variable.
     * @param supplier     A supplier for the value of the variable.
     */
    public static void registerSingletonAnimationVariable(String sourceModId, String variableName, DoubleSupplier supplier) {
        require(sourceModId, variableName, supplier, "variable");
        CEMApiRegistry.registerVariable(variableName, supplier);
        DatariumMain.LOGGER.info("[CEM] Registered animation variable {} from mod {}", variableName, sourceModId);
    }

    /**
     * Registers a singleton boolean variable for use in animation math expressions.
     * Registering a variable with an already registered name replaces the previous one.
     *
     * @param sourceModId  The mod id of the mod registering the variable.
     * @param variableName The name of the variable.
     * @param supplier     A supplier for the value of the variable.
     */
    public static void registerSingletonAnimationVariable(String sourceModId, String variableName, BooleanSupplier supplier) {
        require(sourceModId, variableName, supplier, "variable");
        CEMApiRegistry.registerVariable(variableName, () -> supplier.getAsBoolean() ? 1 : 0);
        DatariumMain.LOGGER.info("[CEM] Registered animation variable {} from mod {}", variableName, sourceModId);
    }

    /**
     * Registers a custom {@link AnimationFunction} for use in animation math expressions.
     * Registering a function with an already registered name replaces the previous one.
     *
     * @param sourceModId  The mod id of the mod registering the function.
     * @param functionName The name of the function.
     * @param function     The function to be registered.
     * @param pure         true if the function always returns the same result for the same arguments and has no side effects.
     *                     Pure functions called with constant arguments will be pre-computed once when the animation is loaded.
     */
    public static void registerCustomFunction(String sourceModId, String functionName, AnimationFunction function, boolean pure) {
        require(sourceModId, functionName, function, "function");
        CEMApiRegistry.registerFunction(functionName, function, pure);
        DatariumMain.LOGGER.info("[CEM] Registered animation function {} from mod {}", functionName, sourceModId);
    }

    /**
     * Registers a custom, non-pure {@link AnimationFunction} for use in animation math expressions.
     *
     * @param sourceModId  The mod id of the mod registering the function.
     * @param functionName The name of the function.
     * @param function     The function to be registered.
     */
    public static void registerCustomFunction(String sourceModId, String functionName, AnimationFunction function) {
        registerCustomFunction(sourceModId, functionName, function, false);
    }

    /**
     * Registers a custom function from a static method for use in animation math expressions.
     * <p>
     * The supplied method must be public and static
     * The supplied method must return a primitive type of either [boolean], [float], [double] or [int].
     * The supplied method can accept any number of parameters of types [boolean], [float], [double] or [int].
     * The function is registered as pure, missing arguments default to [0] and any thrown exception results in [NaN].
     *
     * @param sourceModId  The mod id of the mod registering the function.
     * @param functionName The name of the function.
     * @param staticMethod The method to be registered.
     */
    public static void registerCustomFunctionFromStaticMethod(String sourceModId, String functionName, Method staticMethod) {
        require(sourceModId, functionName, staticMethod, "function");
        if (!Modifier.isStatic(staticMethod.getModifiers()) || !Modifier.isPublic(staticMethod.getModifiers())) {
            throw fail("function " + functionName + " from mod " + sourceModId + " must be public and static");
        }
        Class<?> returnType = staticMethod.getReturnType();
        if (!isSupported(returnType)) throw fail("function " + functionName + " has unsupported return type " + returnType);
        Class<?>[] params = staticMethod.getParameterTypes();
        for (Class<?> param : params) {
            if (!isSupported(param)) throw fail("function " + functionName + " has unsupported parameter type " + param);
        }
        MethodHandle handle;
        try {
            handle = MethodHandles.publicLookup().unreflect(staticMethod);
        } catch (IllegalAccessException e) {
            throw fail("function " + functionName + " is not accessible: " + e.getMessage());
        }
        registerCustomFunction(sourceModId, functionName, args -> {
            Object[] converted = new Object[params.length];
            for (int i = 0; i < params.length; i++) converted[i] = toParam(params[i], i < args.length ? args[i] : 0);
            try {
                return fromResult(handle.invokeWithArguments(converted));
            } catch (Throwable t) {
                return Double.NaN;
            }
        }, true);
    }

    /**
     * @param hook the animation hook object to register, see {@link AnimationHook}.
     */
    public static void registerAnimationHook(AnimationHook hook) {
        if (hook == null) throw fail("null animation hook");
        CEMApiState.addHook(hook);
    }

    /**
     * @param shouldPause The function to consider if a given entity should be paused rather that triggering it via uuid.
     *                    Note: that if this returns false, another mod or even CEM itself might yet return true and pause the entity for other reasons.
     *                    Returning true from this function will ALWAYS lead to a pause.
     */
    public static void registerPauseCondition(Predicate<Object> shouldPause) {
        if (shouldPause == null) throw fail("null pause condition");
        CEMApiState.addPauseCondition(shouldPause);
    }

    /**
     * @param entityOrTile The entity or tile entity to pause animations for.
     * @return true if valid inputs were supplied and the entity's animations were set to pause.
     */
    public static boolean pauseAllCustomAnimationsForEntity(Object entityOrTile) {
        return CEMApiState.pause(entityOrTile);
    }

    /**
     * @param entityOrTile The entity or tile entity to resume animations for.
     * @return true if valid inputs were supplied and the entity's animations were set to resume.
     */
    public static boolean resumeAllCustomAnimationsForEntity(Object entityOrTile) {
        return CEMApiState.resume(entityOrTile);
    }

    /**
     * @param shouldUseVanillaModel The function to consider if a given entity should use the vanilla model rather that triggering it via uuid.
     *                    Note: that if this returns false, another mod or even CEM itself might yet return true and do it for other reasons.
     *                    Returning true from this function will ALWAYS lead to using the vanilla model variant.
     */
    public static void registerVanillaModelCondition(Predicate<Object> shouldUseVanillaModel) {
        if (shouldUseVanillaModel == null) throw fail("null vanilla model condition");
        CEMApiState.addVanillaCondition(shouldUseVanillaModel);
    }

    /**
     * @param entityOrTile The entity or tile entity to be forced into their vanilla model.
     * @return true if valid inputs were supplied and the entity was marked to use the vanilla model.
     */
    public static boolean lockEntityToVanillaModel(Object entityOrTile) {
        return CEMApiState.lockVanilla(entityOrTile);
    }

    /**
     * @param entityOrTile The entity or tile entity to be re-allowed to variate.
     * @return true if valid inputs were supplied and the entity was marked to use their variants again.
     */
    public static boolean unlockEntityToVanillaModel(Object entityOrTile) {
        return CEMApiState.unlockVanilla(entityOrTile);
    }

    /**
     * Get the name of the CEM model used by the entity.
     * Returns null if the entity has no CEM model or is null.
     *
     * @param entity the entity
     * @return the model name
     */
    @Nullable
    public static String getModelName(Entity entity) {
        return entity != null ? CEMManager.getModelNameForEntity(entity) : null;
    }

    /**
     * Get the name of the CEM model used by the tile entity.
     * Returns null if the tile entity has no CEM model or is null.
     *
     * @param tile the tile entity
     * @return the model name
     */
    @Nullable
    public static String getModelName(TileEntity tile) {
        return tile != null ? CEMManager.getModelNameForTile(tile) : null;
    }

    /**
     * Is this entity rendered with a custom CEM model.
     * Returns false if the entity is null.
     *
     * @param entity the entity
     * @return the boolean
     */
    public static boolean isEntityCustomized(Entity entity) {
        return getModelName(entity) != null;
    }

    /**
     * Checks if the entity's model has custom CEM animations.
     * Returns false if the entity has no CEM model or is null.
     *
     * @param entity the entity
     * @return the boolean
     */
    public static boolean isEntityAnimated(Entity entity) {
        String name = getModelName(entity);
        return name != null && CEMManager.getAnimator(name) != null;
    }

    private static boolean isSupported(Class<?> type) {
        return type == float.class || type == double.class || type == int.class || type == boolean.class;
    }

    private static Object toParam(Class<?> type, double value) {
        if (type == float.class) return (float) value;
        if (type == int.class) return (int) value;
        if (type == boolean.class) return value != 0;
        return value;
    }

    private static double fromResult(Object result) {
        if (result instanceof Boolean b) return b ? 1 : 0;
        if (result instanceof Number n) return n.doubleValue();
        return Double.NaN;
    }

    private static void require(String sourceModId, String name, Object value, String what) {
        if (sourceModId == null || name == null || name.isEmpty() || value == null) {
            throw fail("invalid registration of " + what + " " + name + " from mod " + sourceModId);
        }
    }

    private static IllegalArgumentException fail(String message) {
        DatariumMain.LOGGER.error("[CEM] Animation API: {}", message);
        return new IllegalArgumentException("[CEM] Animation API: " + message);
    }
}
