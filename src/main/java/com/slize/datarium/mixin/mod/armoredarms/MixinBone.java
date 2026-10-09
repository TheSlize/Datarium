package com.slize.datarium.mixin.mod.armoredarms;

import com.slize.datarium.client.cem.CEMFirstPerson;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.artur114.armoredarms.core.util.Bone", remap = false)
public class MixinBone {

    @Inject(method = "injectTo", at = @At("RETURN"), remap = false)
    private void datarium$onInjectTo(Object target, CallbackInfo ci) {
        CEMFirstPerson.bindArm(target);
    }
}
