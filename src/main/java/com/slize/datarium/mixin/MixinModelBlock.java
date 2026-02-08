package com.slize.datarium.mixin;

import com.slize.datarium.client.model.nodes.ModernModelNode;
import com.slize.datarium.util.DatariumContext;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.block.model.ModelBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;

@Mixin(ModelBlock.class)
public class MixinModelBlock {

    @Unique
    private ModernModelNode modernLogic;

    @Inject(method = "createOverrides", at = @At("HEAD"))
    public void onCreateOverridesHead(CallbackInfoReturnable<ItemOverrideList> cir) {
        if (this.modernLogic != null) {
            DatariumContext.CURRENT_LOGIC.set(this.modernLogic);
        }
    }

    @Inject(method = "createOverrides", at = @At("RETURN"), cancellable = true)
    public void onCreateOverrides(CallbackInfoReturnable<ItemOverrideList> cir) {
        try {
            if (this.modernLogic != null) {
                ItemOverrideList list = cir.getReturnValue();

                // If vanilla returned NONE, then I must create a new instance to hold item datapack logic.
                // The constructor of this new instance will read the ThreadLocal I've set in HEAD.
                if (list == ItemOverrideList.NONE) {
                    list = new ItemOverrideList(new ArrayList<>());
                    cir.setReturnValue(list);
                    // DatariumMain.LOGGER.info("Datarium: Replaced ItemOverrideList.NONE with new instance for logic injection.");
                }
            }
        } finally {
            DatariumContext.CURRENT_LOGIC.remove();
        }
    }
}
