package com.slize.datarium.client.cem;

import com.slize.datarium.client.cem.expr.CEMRenderContext;
import com.slize.datarium.client.cem.expr.CEMRenderVar;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityHanging;
import net.minecraft.entity.EntityLiving;

import javax.annotation.Nullable;

public final class CEMRenderEffects {
    private CEMRenderEffects() {}

    @Nullable
    public static CEMRenderContext contextFor(Entity entity) {
        CEMRenderState state = CEMManager.peekEntityState(entity);
        if (state == null || state.lastRenderFrame < CEMManager.getFrameCounter() - 1) return null;
        return state.context;
    }

    public static double value(@Nullable CEMRenderContext ctx, CEMRenderVar var) {
        return ctx != null ? ctx.getRenderValue(var) : Double.NaN;
    }

    @Nullable
    public static double[] leashOffset(Entity entity, boolean holder) {
        CEMRenderContext ctx = contextFor(entity);
        if (ctx == null) return null;
        double x = orZero(value(ctx, holder ? CEMRenderVar.LEASH_HOLDER_OFFSET_X : CEMRenderVar.LEASH_OFFSET_X));
        double y = orZero(value(ctx, holder ? CEMRenderVar.LEASH_HOLDER_OFFSET_Y : CEMRenderVar.LEASH_OFFSET_Y));
        double z = orZero(value(ctx, holder ? CEMRenderVar.LEASH_HOLDER_OFFSET_Z : CEMRenderVar.LEASH_OFFSET_Z));
        return x == 0 && y == 0 && z == 0 ? null : new double[]{x, y, z};
    }

    private static double orZero(double v) {
        return Double.isNaN(v) ? 0 : v;
    }

    private static double lerp(double start, double end, double pct) {
        return start + (end - start) * pct;
    }

    public static void renderLeash(EntityLiving entity, Entity holder, double x, double y, double z, float partialTicks,
                                   @Nullable double[] own, @Nullable double[] held) {
        double ox = own != null ? own[0] : 0, oy = own != null ? own[1] : 0, oz = own != null ? own[2] : 0;
        double hx = held != null ? held[0] : 0, hy = held != null ? held[1] : 0, hz = held != null ? held[2] : 0;

        y -= (1.6 - entity.height) * 0.5;
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        double yaw = lerp(holder.prevRotationYaw, holder.rotationYaw, partialTicks * 0.5F) * (float) (Math.PI / 180.0);
        double pitch = lerp(holder.prevRotationPitch, holder.rotationPitch, partialTicks * 0.5F) * (float) (Math.PI / 180.0);
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double sinPitch = Math.sin(pitch);
        if (holder instanceof EntityHanging) {
            cosYaw = 0.0;
            sinYaw = 0.0;
            sinPitch = -1.0;
        }
        double cosPitch = Math.cos(pitch);
        double holderX = lerp(holder.prevPosX, holder.posX, partialTicks) - cosYaw * 0.7 - sinYaw * 0.5 * cosPitch + hx;
        double holderY = lerp(holder.prevPosY + holder.getEyeHeight() * 0.7, holder.posY + holder.getEyeHeight() * 0.7, partialTicks)
                - sinPitch * 0.5 - 0.25 + hy;
        double holderZ = lerp(holder.prevPosZ, holder.posZ, partialTicks) - sinYaw * 0.7 + cosYaw * 0.5 * cosPitch + hz;
        double bodyYaw = lerp(entity.prevRenderYawOffset, entity.renderYawOffset, partialTicks) * (float) (Math.PI / 180.0) + (Math.PI / 2);
        double sideX = Math.cos(bodyYaw) * entity.width * 0.4 + ox;
        double sideZ = Math.sin(bodyYaw) * entity.width * 0.4 + oz;
        double entityX = lerp(entity.prevPosX, entity.posX, partialTicks) + sideX;
        double entityY = lerp(entity.prevPosY, entity.posY, partialTicks) + oy;
        double entityZ = lerp(entity.prevPosZ, entity.posZ, partialTicks) + sideZ;
        x += sideX;
        y += oy;
        z += sideZ;
        double dx = (float) (holderX - entityX);
        double dy = (float) (holderY - entityY);
        double dz = (float) (holderZ - entityZ);
        GlStateManager.disableTexture2D();
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        buffer.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int j = 0; j <= 24; j++) {
            float r = 0.5F, g = 0.4F, b = 0.3F;
            if (j % 2 == 0) {
                r *= 0.7F;
                g *= 0.7F;
                b *= 0.7F;
            }
            float t = j / 24.0F;
            double sag = dy * (t * t + t) * 0.5 + ((24.0F - j) / 18.0F + 0.125F);
            buffer.pos(x + dx * t, y + sag, z + dz * t).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + dx * t + 0.025, y + sag + 0.025, z + dz * t).color(r, g, b, 1.0F).endVertex();
        }
        tessellator.draw();

        buffer.begin(5, DefaultVertexFormats.POSITION_COLOR);
        for (int k = 0; k <= 24; k++) {
            float r = 0.5F, g = 0.4F, b = 0.3F;
            if (k % 2 == 0) {
                r *= 0.7F;
                g *= 0.7F;
                b *= 0.7F;
            }
            float t = k / 24.0F;
            double sag = dy * (t * t + t) * 0.5 + ((24.0F - k) / 18.0F + 0.125F);
            buffer.pos(x + dx * t, y + sag + 0.025, z + dz * t).color(r, g, b, 1.0F).endVertex();
            buffer.pos(x + dx * t + 0.025, y + sag, z + dz * t + 0.025).color(r, g, b, 1.0F).endVertex();
        }
        tessellator.draw();

        GlStateManager.enableLighting();
        GlStateManager.enableTexture2D();
        GlStateManager.enableCull();
    }
}
