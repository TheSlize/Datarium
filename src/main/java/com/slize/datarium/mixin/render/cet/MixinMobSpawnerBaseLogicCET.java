package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETSubject;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.MobSpawnerBaseLogic;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(MobSpawnerBaseLogic.class)
public abstract class MixinMobSpawnerBaseLogicCET {

    @Shadow
    public abstract BlockPos getSpawnerPosition();

    @Inject(method = "getCachedEntity", at = @At("RETURN"))
    private void datarium$cetStabiliseUuid(CallbackInfoReturnable<Entity> cir) {
        Entity entity = cir.getReturnValue();
        if (entity != null && entity.getUniqueID().getLeastSignificantBits() != CETSubject.SPAWNER_MARKER) {
            entity.setUniqueId(new UUID(getSpawnerPosition().toLong(), CETSubject.SPAWNER_MARKER));
        }
    }
}
