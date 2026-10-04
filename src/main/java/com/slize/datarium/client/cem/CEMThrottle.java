package com.slize.datarium.client.cem;

import net.minecraft.client.Minecraft;

/**
 * Distance-based LOD for CEM animation updates: far entities hold their last pose longer.
 * Distance is apparent (scaled by the current FOV, see MixinEntityRendererFOV),
 * tiers are wall-clock intervals rather than frame counts.
 */
public final class CEMThrottle {
    private static final double TIER1_DIST = 24.0;
    private static final double TIER2_DIST = 48.0;
    private static final double TIER3_DIST = 80.0;

    private static final double TIER1_INTERVAL = 1.0 / 60.0;
    private static final double TIER2_INTERVAL = 1.0 / 30.0;
    private static final double TIER3_INTERVAL = 1.0 / 20.0;
    private static final double TIER4_INTERVAL = 1.0 / 15.0;

    private static volatile float currentFov = 70.0F;

    private CEMThrottle() {}

    public static void setCurrentFov(float fov) {
        currentFov = fov;
    }

    /**
     * @param distSq             camera-relative squared distance, 0 for first-person
     * @param lastUpdateNanoTime {@link CEMRenderState#lastUpdateTime}, 0 if never updated
     */
    public static boolean shouldUpdate(double distSq, long lastUpdateNanoTime, long nowNanoTime) {
        if (lastUpdateNanoTime == 0L) return true;
        double elapsed = (nowNanoTime - lastUpdateNanoTime) / 1.0E9D;
        return elapsed >= minIntervalFor(distSq);
    }

    /** For first-person hands. */
    public static boolean shouldUpdateClose(long lastUpdateNanoTime, long nowNanoTime) {
        return shouldUpdate(0.0, lastUpdateNanoTime, nowNanoTime);
    }

    private static double minIntervalFor(double distSq) {
        float baseFov = Minecraft.getMinecraft().gameSettings.fovSetting;
        if (baseFov <= 0.0F) baseFov = 70.0F;

        double zoomRatio = currentFov / baseFov;
        if (zoomRatio < 0.05) zoomRatio = 0.05;
        else if (zoomRatio > 2.0) zoomRatio = 2.0;

        double apparentDistSq = distSq * zoomRatio * zoomRatio;

        if (apparentDistSq < TIER1_DIST * TIER1_DIST) return TIER1_INTERVAL;
        if (apparentDistSq < TIER2_DIST * TIER2_DIST) return TIER2_INTERVAL;
        if (apparentDistSq < TIER3_DIST * TIER3_DIST) return TIER3_INTERVAL;
        return TIER4_INTERVAL;
    }
}