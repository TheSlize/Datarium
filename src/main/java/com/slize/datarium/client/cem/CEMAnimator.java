package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.expr.CEMCompiled;
import com.slize.datarium.client.cem.expr.CEMConstantPropagation;
import com.slize.datarium.client.cem.expr.CEMExpression;
import com.slize.datarium.client.cem.expr.CEMExpressionParser;
import com.slize.datarium.client.cem.expr.CEMGlobalVars;
import com.slize.datarium.client.cem.expr.CEMPartRefs;
import com.slize.datarium.client.cem.expr.CEMProfiler;
import com.slize.datarium.client.cem.expr.CEMRenderVar;
import com.slize.datarium.client.cem.expr.CEMRenderContext;
import com.slize.datarium.client.cem.expr.CEMVarSlots;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

public class CEMAnimator {
    private final CEMModel model;
    private final String name;
    private final List<CompiledAnim> entries;
    private final List<Set<String>> entryRefs = new ArrayList<>();
    private final Map<String, List<Integer>> writers = new HashMap<>();
    private final Set<String> partRefs = new HashSet<>();
    private boolean prunable = true;

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
                if (target == null) continue;
                Set<String> refs = new HashSet<>();
                if (!CEMPartRefs.collect(ast, refs)) prunable = false;
                if (target.kind == TargetKind.PART) writers.computeIfAbsent(target.fullKey, _ -> new ArrayList<>()).add(entries.size());
                entries.add(target);
                entryRefs.add(refs);
                partRefs.addAll(refs);
            } catch (Exception e) {
                DatariumMain.LOGGER.warn("[CEM] Failed to compile expression: {}", key, e);
            }
        }
    }

    /** Every "part.property" this model's expressions read. */
    public Set<String> partRefs() {
        return partRefs;
    }

    /**
     * Entries that still have to run when the parts matched by {@code partUnused} draw nothing:
     * everything that is not a part target, the remaining parts, and whatever those read.
     *
     * @return null when nothing can be skipped.
     */
    @Nullable
    public int[] prune(Predicate<String> partUnused, Collection<String> externalRefs) {
        if (!prunable) return null;
        int n = entries.size();
        boolean[] keep = new boolean[n];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        Map<String, Boolean> unused = new HashMap<>();

        for (int i = 0; i < n; i++) {
            CompiledAnim e = entries.get(i);
            if (e.kind == TargetKind.PART && unused.computeIfAbsent(e.partId, partUnused::test)) continue;
            keep[i] = true;
            queue.add(i);
        }
        for (String ref : externalRefs) keepWriters(ref, keep, queue);
        while (!queue.isEmpty()) {
            for (String ref : entryRefs.get(queue.poll())) keepWriters(ref, keep, queue);
        }

        int count = 0;
        for (boolean k : keep) if (k) count++;
        if (count == n) return null;
        int[] out = new int[count];
        for (int i = 0, o = 0; i < n; i++) if (keep[i]) out[o++] = i;
        return out;
    }

    /** Parts written by the entries of a {@link #prune} result. */
    public Set<String> partIds(int[] subset) {
        Set<String> parts = new LinkedHashSet<>();
        for (int index : subset) {
            CompiledAnim e = entries.get(index);
            if (e.kind == TargetKind.PART) parts.add(e.partId);
        }
        return parts;
    }

    private void keepWriters(String ref, boolean[] keep, ArrayDeque<Integer> queue) {
        List<Integer> indices = writers.get(ref);
        if (indices == null) return;
        for (int index : indices) {
            if (keep[index]) continue;
            keep[index] = true;
            queue.add(index);
        }
    }

    public void evaluate(CEMRenderContext ctx, Map<String, CEMPartTransform> transforms) {
        evaluate(ctx, transforms, null);
    }

    /** @param subset entry indices from {@link #prune}, null for all of them. */
    public void evaluate(CEMRenderContext ctx, Map<String, CEMPartTransform> transforms, @Nullable int[] subset) {
        if (!CEMApiState.hasHooks()) {
            evaluateEntries(ctx, transforms, subset);
            return;
        }
        Object subject = ctx.getRenderedObject();
        boolean allowed = CEMApiState.startAnimation(subject, name);
        try {
            if (allowed) evaluateEntries(ctx, transforms, null);
        } finally {
            CEMApiState.endAnimation(subject, name, !allowed);
        }
    }

    private void evaluateEntries(CEMRenderContext ctx, Map<String, CEMPartTransform> transforms, @Nullable int[] subset) {
        boolean profiling = CEMProfiler.enabled;
        if (profiling) subset = null;
        for (int i = 0, n = subset != null ? subset.length : entries.size(); i < n; i++) {
            CompiledAnim e = entries.get(subset != null ? subset[i] : i);

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