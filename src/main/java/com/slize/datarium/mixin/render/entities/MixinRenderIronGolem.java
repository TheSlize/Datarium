package com.slize.datarium.mixin.render.entities;

import com.slize.datarium.client.cem.textures.LayerIronGolemCracks;
import com.slize.datarium.mixin.accessors.AccessorRenderLivingBase;
import net.minecraft.client.renderer.entity.RenderIronGolem;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.monster.EntityIronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderIronGolem.class)
public class MixinRenderIronGolem {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void datarium$addCrackLayer(RenderManager manager, CallbackInfo ci) {
        RenderLivingBase<EntityIronGolem> self = (RenderLivingBase<EntityIronGolem>) (Object) this;
        ((AccessorRenderLivingBase) self).datarium$addLayer(new LayerIronGolemCracks(self));
    }
}
