package com.slize.datarium.mixin.render.cet;

import com.slize.datarium.client.cet.CETRender;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntityCET {

    @Inject(method = "getBrightnessForRender", at = @At("RETURN"), cancellable = true)
    private void datarium$cetLightOverride(CallbackInfoReturnable<Integer> cir) {
        int original = cir.getReturnValue();
        int modified = CETRender.entityLight((Entity) (Object) this, original);
        if (modified != original) cir.setReturnValue(modified);
    }
}
