package com.slize.datarium.mixin.misc;

import com.google.gson.JsonObject;
import com.slize.datarium.client.gui.GuiRespackOpts;
import com.slize.datarium.util.RespackOptsManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ResourcePackListEntry.class)
public abstract class MixinResourcePackListEntry {

    @Shadow @Final protected Minecraft mc;

    @Unique
    private static final ResourceLocation CONFIG_BUTTON_TEXTURE = new ResourceLocation("datarium", "textures/gui/configure_button.png");

    @Unique
    private boolean datarium$hasRespackOpts = false;
    @Unique
    private boolean datarium$checkedRespackOpts = false;
    @Unique
    private JsonObject datarium$cachedConfig = null;

    @Unique
    private IResourcePack datarium$getPack() {
        if ((Object) this instanceof ResourcePackListEntryFound) {
            return ((ResourcePackListEntryFound)(Object)this).getResourcePackEntry().getResourcePack();
        }
        return null;
    }

    @Inject(method = "drawEntry", at = @At("RETURN"))
    public void onDrawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected, float partialTicks, CallbackInfo ci) {
        if (!datarium$checkedRespackOpts) {
            IResourcePack pack = datarium$getPack();
            if (pack != null) {
                this.datarium$cachedConfig = RespackOptsManager.getPackConfiguration(pack);
                this.datarium$hasRespackOpts = (this.datarium$cachedConfig != null);
            }
            datarium$checkedRespackOpts = true;
        }

        if (this.datarium$hasRespackOpts) {
            this.mc.getTextureManager().bindTexture(CONFIG_BUTTON_TEXTURE);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            GlStateManager.enableBlend();

            int btnSize = 20;
            int btnX = x + listWidth - btnSize - 8;
            int btnY = y + (slotHeight - btnSize) / 2; // Vertically centered

            int vOffset = 0; // Normal (0)
            if (mouseX >= btnX && mouseX <= btnX + btnSize && mouseY >= btnY && mouseY <= btnY + btnSize) {
                vOffset = 20; // Hover (20)
            }

            Gui.drawModalRectWithCustomSizedTexture(btnX, btnY, 0, vOffset, btnSize, btnSize, 32, 64);

            GlStateManager.disableBlend();
        }
    }

    @Inject(method = "mousePressed", at = @At("HEAD"), cancellable = true)
    public void onMousePressed(int slotIndex, int mouseX, int mouseY, int mouseEvent, int relativeX, int relativeY, CallbackInfoReturnable<Boolean> cir) {
        if (this.datarium$hasRespackOpts && this.datarium$cachedConfig != null) {
            if (relativeX > 160 && relativeY >= 0 && relativeY <= 32) {
                this.mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.getMasterRecord(net.minecraft.init.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                this.mc.displayGuiScreen(new GuiRespackOpts(this.mc.currentScreen, datarium$getPack(), this.datarium$cachedConfig));
                cir.setReturnValue(true);
            }
        }
    }
}