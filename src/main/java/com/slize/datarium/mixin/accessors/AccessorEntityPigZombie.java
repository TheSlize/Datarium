package com.slize.datarium.mixin.accessors;

import net.minecraft.entity.monster.EntityPigZombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityPigZombie.class)
public interface AccessorEntityPigZombie {
    @Accessor("angerLevel")
    int datarium$getAngerLevel();
}
