package com.slize.datarium.mixin.render.cet;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.slize.datarium.client.cet.CETNbt;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.RecipeBookServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMPCET {

    @WrapOperation(method = "writeEntityToNBT",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/stats/RecipeBookServer;write()Lnet/minecraft/nbt/NBTTagCompound;"))
    private NBTTagCompound datarium$skipRecipeBook(RecipeBookServer book, Operation<NBTTagCompound> original) {
        return CETNbt.isComputing() ? new NBTTagCompound() : original.call(book);
    }
}
