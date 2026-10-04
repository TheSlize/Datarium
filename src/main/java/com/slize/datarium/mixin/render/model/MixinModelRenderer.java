package com.slize.datarium.mixin.render.model;

import com.slize.datarium.client.cem.CEMGenericRender;
import com.slize.datarium.client.cem.CEMModelRenderer;
import com.slize.datarium.client.cem.CEMRenderHooks;
import net.minecraft.client.model.ModelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelRenderer.class)
public class MixinModelRenderer {

    @Unique private boolean datarium$mirrored = false;
    @Unique private float datarium$rpx, datarium$rpy, datarium$rpz;
    @Unique private float datarium$rax, datarium$ray, datarium$raz;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void datarium$onRender(float scale, CallbackInfo ci) {
        ModelRenderer self = (ModelRenderer) (Object) this;

        CEMModelRenderer replacement = CEMRenderHooks.getReplacement(self);
        if (replacement != null && CEMGenericRender.renderPart(self, replacement, scale)) {
            ci.cancel();
            return;
        }
        if (replacement != null && !replacement.isAttached()) {
            if (!replacement.isDeferred()) replacement.renderWithVanilla(scale);
            ci.cancel();
            return;
        }
        if (replacement == null && CEMGenericRender.isHidden(self)) {
            ci.cancel();
            return;
        }

        CEMModelRenderer secondary = CEMRenderHooks.getSecondaryReplacement(self);
        if (secondary != null) {
            if (!secondary.isAttached()) {
                boolean mirrored = datarium$applyMirror(self);
                secondary.renderWithVanilla(scale);
                if (mirrored) datarium$restoreMirror(self);
                ci.cancel();
            }
            return;
        }

        if (CEMRenderHooks.isSecondaryHidden(self)) { ci.cancel(); return; }

        datarium$mirrored = datarium$applyMirror(self);
    }

    @Unique
    private boolean datarium$applyMirror(ModelRenderer self) {
        CEMModelRenderer mirror = CEMRenderHooks.getMirrorSource(self);
        if (mirror == null) return false;
        datarium$rpx = self.rotationPointX; datarium$rpy = self.rotationPointY; datarium$rpz = self.rotationPointZ;
        datarium$rax = self.rotateAngleX;   datarium$ray = self.rotateAngleY;   datarium$raz = self.rotateAngleZ;
        if (mirror.hasAnimatedTranslate()) {
            self.rotationPointX = mirror.effectivePivotX();
            self.rotationPointY = mirror.effectivePivotY();
            self.rotationPointZ = mirror.effectivePivotZ();
        }
        if (mirror.hasAnimatedRotate()) {
            self.rotateAngleX = mirror.effectiveRotateX();
            self.rotateAngleY = mirror.effectiveRotateY();
            self.rotateAngleZ = mirror.effectiveRotateZ();
        }
        return true;
    }

    @Unique
    private void datarium$restoreMirror(ModelRenderer self) {
        self.rotationPointX = datarium$rpx; self.rotationPointY = datarium$rpy; self.rotationPointZ = datarium$rpz;
        self.rotateAngleX = datarium$rax;   self.rotateAngleY = datarium$ray;   self.rotateAngleZ = datarium$raz;
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void datarium$onRenderReturn(float scale, CallbackInfo ci) {
        ModelRenderer self = (ModelRenderer) (Object) this;

        if (datarium$mirrored) {
            datarium$restoreMirror(self);
            datarium$mirrored = false;
        }

        CEMModelRenderer replacement = CEMRenderHooks.getReplacement(self);
        if (replacement != null && replacement.isAttached()) {
            replacement.renderWithVanilla(scale);
        }
    }

    @Inject(method = "postRender", at = @At("HEAD"), cancellable = true)
    private void datarium$onPostRender(float scale, CallbackInfo ci) {
        ModelRenderer self = (ModelRenderer) (Object) this;

        CEMModelRenderer replacement = CEMRenderHooks.getReplacement(self);
        if (replacement == null) replacement = CEMRenderHooks.getSecondaryReplacement(self);

        if (replacement != null && !replacement.isAttached()) {
            CEMGenericRender.beforePartRender(self, replacement);
            replacement.applyPostRender(scale);
            ci.cancel();
            return;
        }

        CEMModelRenderer mirror = CEMRenderHooks.getMirrorSource(self);
        if (mirror != null) {
            mirror.applyPostRender(scale);
            ci.cancel();
        }
    }

    @Inject(method = "renderWithRotation", at = @At("HEAD"), cancellable = true)
    private void datarium$onRenderWithRotation(float scale, CallbackInfo ci) {
        ModelRenderer self = (ModelRenderer) (Object) this;

        CEMModelRenderer replacement = CEMRenderHooks.getReplacement(self);
        if (replacement != null && CEMGenericRender.renderPart(self, replacement, scale)) {
            ci.cancel();
            return;
        }
        if (replacement == null) replacement = CEMRenderHooks.getSecondaryReplacement(self);

        if (replacement != null && !replacement.isAttached()) {
            if (!replacement.isDeferred()) replacement.renderWithVanilla(scale);
            ci.cancel();
        } else if (replacement == null && CEMGenericRender.isHidden(self)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderWithRotation", at = @At("RETURN"))
    private void datarium$onRenderWithRotationReturn(float scale, CallbackInfo ci) {
        ModelRenderer self = (ModelRenderer) (Object) this;

        CEMModelRenderer replacement = CEMRenderHooks.getReplacement(self);
        if (replacement != null && replacement.isAttached()) replacement.renderWithVanilla(scale);
    }
}