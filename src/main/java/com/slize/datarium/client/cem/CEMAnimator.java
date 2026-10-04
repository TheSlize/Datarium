package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.expr.CEMCompiled;
import com.slize.datarium.client.cem.expr.CEMConstantPropagation;
import com.slize.datarium.client.cem.expr.CEMExpression;
import com.slize.datarium.client.cem.expr.CEMExpressionParser;
import com.slize.datarium.client.cem.expr.CEMGlobalVars;
import com.slize.datarium.client.cem.expr.CEMProfiler;
import com.slize.datarium.client.cem.expr.CEMRenderVar;
import com.slize.datarium.client.cem.expr.CEMRenderContext;
import com.slize.datarium.client.cem.expr.CEMVarSlots;

import java.util.*;

public class CEMAnimator {
    private final CEMModel model;
    private final String name;
    private final List<CompiledAnim> entries;

    public CEMAnimator(CEMModel model, String name) {
        this.model = model;
        this.name = name;
        this.entries = new ArrayList<>();
        compileAnimations();
    }

    private void compileAnimations() {
        List<String> keys = new ArrayList<>();
        List<CEMExpression> asts = new ArrayList<>();
        for (CEMAnimation anim : model.animations) {
            for (Map.Entry<String, String> entry : anim.expressions.entrySet()) {
                String key = entry.getKey();
                try {
                    asts.add(CEMExpressionParser.parse(entry.getValue()));
                    keys.add(key);
                } catch (Exception e) {
                    DatariumMain.LOGGER.warn("[CEM] Failed to compile expression: {} = {}", key, entry.getValue(), e);
                }
            }
        }

        CEMConstantPropagation.apply(keys, asts);

        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            CEMExpression ast = asts.get(i);
            try {
                CompiledAnim target = CompiledAnim.forKey(key, ast);
                if (target != null) entries.add(target);
            } catch (Exception e) {
                DatariumMain.LOGGER.warn("[CEM] Failed to compile expression: {}", key, e);
            }
        }
    }

    public void evaluate(CEMRenderContext ctx, Map<String, CEMPartTransform> transforms) {
        if (!CEMApiState.hasHooks()) {
            evaluateEntries(ctx, transforms);
            return;
        }
        Object subject = ctx.getRenderedObject();
        boolean allowed = CEMApiState.startAnimation(subject, name);
        try {
            if (allowed) evaluateEntries(ctx, transforms);
        } finally {
            CEMApiState.endAnimation(subject, name, !allowed);
        }
    }

    private void evaluateEntries(CEMRenderContext ctx, Map<String, CEMPartTransform> transforms) {
        boolean profiling = CEMProfiler.enabled;
        for (int i = 0, n = entries.size(); i < n; i++) {
            CompiledAnim e = entries.get(i);

            try {
                double value = profiling ? e.expr.evaluate(ctx) : e.fast.eval(ctx);

                switch (e.kind) {
                    case VAR -> ctx.setVariable(e.slot, value);
                    case VARB -> ctx.setBoolVariable(e.slot, value != 0);
                    case GLOBAL_VAR -> CEMGlobalVars.set(e.slot, value);
                    case GLOBAL_VARB -> CEMGlobalVars.setBool(e.slot, value != 0);
                    case RENDER -> ctx.setRenderValue(e.render, value);
                    case PART -> {
                        ctx.setPartValueByFullKey(e.fullKey, value);

                        CEMPartTransform transform = transforms.computeIfAbsent(e.partId, _ -> new CEMPartTransform());
                        applyPropertyToTransform(transform, e.prop, value);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void applyPropertyToTransform(CEMPartTransform transform, PropKind property, double value) {
        switch (property) {
            case RX -> { transform.rotateX = (float) value; transform.hasRotateX = true; }
            case RY -> { transform.rotateY = (float) value; transform.hasRotateY = true; }
            case RZ -> { transform.rotateZ = (float) value; transform.hasRotateZ = true; }
            case TX -> { transform.translateX = (float) value; transform.hasTranslateX = true; }
            case TY -> { transform.translateY = (float) value; transform.hasTranslateY = true; }
            case TZ -> { transform.translateZ = (float) value; transform.hasTranslateZ = true; }
            case SX -> { transform.scaleX = (float) value; transform.hasScaleX = true; }
            case SY -> { transform.scaleY = (float) value; transform.hasScaleY = true; }
            case SZ -> { transform.scaleZ = (float) value; transform.hasScaleZ = true; }
            case VISIBLE -> { transform.visible = value != 0; transform.hasVisible = true; }
            case VISIBLE_BOXES -> { transform.visibleBoxes = value != 0; transform.hasVisibleBoxes = true; }
            case UNKNOWN -> {}
        }
    }

    private enum TargetKind { VAR, VARB, GLOBAL_VAR, GLOBAL_VARB, RENDER, PART }

    private enum PropKind { RX, RY, RZ, TX, TY, TZ, SX, SY, SZ, VISIBLE, VISIBLE_BOXES, UNKNOWN }

    /**
     * An animation key ("var.run", "head.rx", ...) classified once at load.
     */
    private record CompiledAnim(CEMExpression expr, CEMCompiled fast, TargetKind kind, int slot, String partId, String fullKey,
                                PropKind prop, CEMRenderVar render) {

        private static CompiledAnim slotted(CEMExpression expr, TargetKind kind, int slot) {
            return new CompiledAnim(expr, CEMCompiled.of(expr), kind, slot, null, null, null, null);
        }

        /**
         * @return null if the key matches none of the recognised forms.
         */
        static CompiledAnim forKey(String key, CEMExpression expr) {
            if (key.startsWith("var.")) return slotted(expr, TargetKind.VAR, CEMVarSlots.slotFor(key.substring(4)));
            if (key.startsWith("varb.")) return slotted(expr, TargetKind.VARB, CEMVarSlots.slotFor(key.substring(5)));
            if (key.startsWith("global_var."))
                return slotted(expr, TargetKind.GLOBAL_VAR, CEMGlobalVars.slotFor(key.substring(11)));
            if (key.startsWith("global_varb."))
                return slotted(expr, TargetKind.GLOBAL_VARB, CEMGlobalVars.slotFor(key.substring(12)));
            if (key.startsWith("render.")) {
                CEMRenderVar render = CEMRenderVar.byName(key.substring(7));
                return render != null ? new CompiledAnim(expr, CEMCompiled.of(expr), TargetKind.RENDER, -1, null, null, null, render) : null;
            }
            int dotIdx = key.lastIndexOf('.');
            if (dotIdx >= 0) {
                return new CompiledAnim(expr, CEMCompiled.of(expr), TargetKind.PART, -1, key.substring(0, dotIdx), key, parseProp(key.substring(dotIdx + 1)), null);
            }
            return null;
        }

        private static PropKind parseProp(String property) {
            return switch (property) {
                case "rx" -> PropKind.RX;
                case "ry" -> PropKind.RY;
                case "rz" -> PropKind.RZ;
                case "tx" -> PropKind.TX;
                case "ty" -> PropKind.TY;
                case "tz" -> PropKind.TZ;
                case "sx" -> PropKind.SX;
                case "sy" -> PropKind.SY;
                case "sz" -> PropKind.SZ;
                case "visible" -> PropKind.VISIBLE;
                case "visible_boxes" -> PropKind.VISIBLE_BOXES;
                default -> PropKind.UNKNOWN;
            };
        }
    }
}