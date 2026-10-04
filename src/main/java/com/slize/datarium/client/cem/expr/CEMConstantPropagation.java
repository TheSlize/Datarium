package com.slize.datarium.client.cem.expr;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Whole-model constant folding for "var."/"varb." targets, e.g. Fresh Animations' version flags
 * ("varb.21_2_plus": "false") and everything gated behind them.
 * Only names with exactly one writer that reduces to a literal are folded.
 */
public final class CEMConstantPropagation {
    private CEMConstantPropagation() {}

    /** Mutates {@code asts} in place. */
    public static void apply(List<String> keys, List<CEMExpression> asts) {
        Map<String, Integer> soleWriterIndex = new HashMap<>();
        Map<String, Boolean> ambiguous = new HashMap<>();
        for (int i = 0; i < keys.size(); i++) {
            String name = qualifiedVarName(keys.get(i));
            if (name == null) continue;
            if (Boolean.TRUE.equals(ambiguous.get(name))) continue;
            if (soleWriterIndex.containsKey(name)) {
                soleWriterIndex.remove(name);
                ambiguous.put(name, true);
            } else {
                soleWriterIndex.put(name, i);
            }
        }
        if (soleWriterIndex.isEmpty()) return;

        Map<String, Double> constants = new HashMap<>();

        // Fixed point for aliasing chains, bounded as a safety net.
        for (int pass = 0; pass < 8; pass++) {
            boolean changed = false;
            for (Map.Entry<String, Integer> e : soleWriterIndex.entrySet()) {
                String name = e.getKey();
                if (constants.containsKey(name)) continue;
                int idx = e.getValue();
                CEMExpression substituted = substitute(asts.get(idx), constants);
                if (substituted != asts.get(idx)) asts.set(idx, substituted);
                if (asts.get(idx) instanceof CEMLiteral(double value)) {
                    constants.put(name, value);
                    changed = true;
                }
            }
            if (!changed) break;
        }

        if (constants.isEmpty()) return;

        for (int i = 0; i < asts.size(); i++) {
            CEMExpression substituted = substitute(asts.get(i), constants);
            if (substituted != asts.get(i)) asts.set(i, substituted);
        }
    }

    /** Part-property targets are never candidates: their fallback is the live part value. */
    private static String qualifiedVarName(String key) {
        if (key.startsWith("var.") || key.startsWith("varb.")) return key;
        return null;
    }

    private static CEMExpression substitute(CEMExpression e, Map<String, Double> constants) {
        if (e instanceof CEMVariable v) {
            Double val = constants.get(v.name());
            return val != null ? new CEMLiteral(val) : e;
        }
        if (e instanceof CEMBinaryOp(CEMBinaryOp.Op op, CEMExpression left, CEMExpression right)) {
            CEMExpression l = substitute(left, constants);
            CEMExpression r = substitute(right, constants);
            if (l == left && r == right) return e;
            return CEMBinaryOp.of(op, l, r);
        }
        if (e instanceof CEMUnaryOp(CEMUnaryOp.Op op, CEMExpression operand1)) {
            CEMExpression operand = substitute(operand1, constants);
            return operand == operand1 ? e : CEMUnaryOp.of(op, operand);
        }
        if (e instanceof CEMFunction f) {
            CEMExpression[] args = f.args();
            CEMExpression[] newArgs = null;
            for (int i = 0; i < args.length; i++) {
                CEMExpression sub = substitute(args[i], constants);
                if (sub != args[i]) {
                    if (newArgs == null) newArgs = args.clone();
                    newArgs[i] = sub;
                }
            }
            return newArgs == null ? e : CEMFunction.of(f.name(), Arrays.asList(newArgs));
        }
        return e;
    }
}