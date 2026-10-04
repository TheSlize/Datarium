package com.slize.datarium.mixin.accessors;

import net.minecraft.entity.passive.EntityVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityVillager.class)
public interface AccessorEntityVillager {
    @Accessor("careerId")
    int datarium$getCareerId();

    @Accessor("careerLevel")
    int datarium$getCareerLevel();
}
