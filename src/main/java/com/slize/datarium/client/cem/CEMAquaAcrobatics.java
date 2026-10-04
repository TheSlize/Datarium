package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Loader;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public final class CEMAquaAcrobatics {
    private static final String RESIZEABLE = "com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable";

    @Nullable private static final Class<?> TYPE;
    @Nullable private static final MethodHandle IS_SWIMMING;
    @Nullable private static final MethodHandle IS_CRAWLING;

    static {
        Class<?> type = null;
        MethodHandle swimming = null;
        MethodHandle crawling = null;
        if (Loader.isModLoaded("aquaacrobatics")) {
            try {
                type = Class.forName(RESIZEABLE);
                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodType check = MethodType.methodType(boolean.class, Entity.class);
                swimming = lookup.findVirtual(type, "isSwimming", MethodType.methodType(boolean.class)).asType(check);
                crawling = lookup.findVirtual(type, "isVisuallySwimming", MethodType.methodType(boolean.class)).asType(check);
            } catch (ReflectiveOperationException | LinkageError e) {
                DatariumMain.LOGGER.warn("[CEM] Aqua Acrobatics found, but its swimming API could not be bound", e);
                type = null;
                swimming = null;
            }
        }
        TYPE = type;
        IS_SWIMMING = swimming;
        IS_CRAWLING = crawling;
    }

    private CEMAquaAcrobatics() {}

    public static boolean isSwimming(@Nullable Entity entity) {
        return test(IS_SWIMMING, entity);
    }

    public static boolean isCrawling(@Nullable Entity entity) {
        return test(IS_CRAWLING, entity);
    }

    private static boolean test(@Nullable MethodHandle handle, @Nullable Entity entity) {
        if (handle == null || entity == null || !TYPE.isInstance(entity)) return false;
        try {
            return (boolean) handle.invokeExact(entity);
        } catch (Throwable t) {
            return false;
        }
    }
}
