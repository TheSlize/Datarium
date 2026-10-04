package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETManager;
import com.slize.datarium.client.cet.CETSubject;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntityBlaze.class)
public abstract class MixinEntityBlazeCET {

    @Redirect(method = "onLivingUpdate", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V"))
    private void datarium$cetSuppressParticles(World world, EnumParticleTypes type, double x, double y, double z,
                                               double dx, double dy, double dz, int[] parameters) {
        if (world.isRemote && CETManager.suppressesParticles(CETSubject.keyOf((EntityBlaze) (Object) this))) return;
        world.spawnParticle(type, x, y, z, dx, dy, dz, parameters);
    }
}
