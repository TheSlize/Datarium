package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.CEMRenderHooks;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class CEMFunction implements CEMExpression {

    private static final Map<String, Fn> BY_NAME = new HashMap<>();
    private static final EnumSet<Fn> IMPURE = EnumSet.of(Fn.RANDOM, Fn.RANDOMB, Fn.PRINT, Fn.PRINTB);
    private static final Set<String> REPORTED_FUNCS = ConcurrentHashMap.newKeySet();
    private static final List<String> LABELS = new CopyOnWriteArrayList<>();
    private static final Map<String, Integer> LABEL_IDS = new HashMap<>();

    public enum Fn {
        SIN, COS, TAN, ASIN, ACOS, ATAN, ATAN2, ABS, FLOOR, CEIL, ROUND, SQRT, POW, EXP, LOG,
        MIN, MAX, CLAMP, TORAD, TODEG, IF, IFB, BETWEEN, EQUALS, RANDOM, RANDOMB, LERP, FMOD, SIGNUM, IN,
        WRAPRAD, WRAPDEG, RADDIFF, DEGDIFF, FRAC, PRINT, PRINTB, CATCH,
        KEYFRAME, KEYFRAMELOOP, CATMULLROM, HERMITE, CUBICBEZIER, QUADBEZIER,
        EASEINOUTEXPO, EASEINEXPO, EASEOUTEXPO,
        EASEINOUTCIRC, EASEINCIRC, EASEOUTCIRC,
        EASEINOUTELASTIC, EASEINELASTIC, EASEOUTELASTIC,
        EASEINOUTBACK, EASEINBACK, EASEOUTBACK,
        EASEINOUTBOUNCE, EASEINBOUNCE, EASEOUTBOUNCE,
        EASEINOUTQUAD, EASEINQUAD, EASEOUTQUAD,
        EASEINOUTCUBIC, EASEINCUBIC, EASEOUTCUBIC,
        EASEINOUTQUART, EASEINQUART, EASEOUTQUART,
        EASEINOUTQUINT, EASEINQUINT, EASEOUTQUINT,
        EASEINOUTSINE, EASEINSINE, EASEOUTSINE,
        CUSTOM,
        UNKNOWN
    }

    private final String name;
    private final Fn fn;
    private final CEMExpression[] args;
    private final int customSlot;
    private final double[] scratch;

    private CEMFunction(String name, List<CEMExpression> args) {
        this.name = name;
        this.args = args.toArray(new CEMExpression[0]);
        this.scratch = new double[this.args.length];
        Fn resolved = BY_NAME.getOrDefault(name, Fn.UNKNOWN);
        int slot = resolved == Fn.UNKNOWN ? CEMApiRegistry.functionSlot(name) : -1;
        this.fn = slot >= 0 ? Fn.CUSTOM : resolved;
        this.customSlot = slot;
    }

    static CEMExpression of(String name, List<CEMExpression> args) {
        CEMFunction f = new CEMFunction(name, args);
        if (f.fn == Fn.UNKNOWN) {
            reportUnknown(name, f.args.length);
            return new CEMLiteral(0);
        }
        if (!f.isPure()) return f;
        for (CEMExpression a : f.args) if (!(a instanceof CEMLiteral)) return f;
        return new CEMLiteral(f.evaluate(null));
    }

    static {
        for (Fn f : Fn.values()) BY_NAME.put(f.name().toLowerCase(Locale.ROOT), f);
        BY_NAME.remove("unknown");
        BY_NAME.remove("custom");
        BY_NAME.put("sign", Fn.SIGNUM);
        BY_NAME.put("easeinout", Fn.EASEINOUTSINE);
        BY_NAME.put("easein", Fn.EASEINSINE);
        BY_NAME.put("easeout", Fn.EASEOUTSINE);
        BY_NAME.put("cubiceaseinout", Fn.EASEINOUTCUBIC);
        BY_NAME.put("cubiceasein", Fn.EASEINCUBIC);
        BY_NAME.put("cubiceaseout", Fn.EASEOUTCUBIC);
    }

    private boolean isPure() { return fn == Fn.CUSTOM ? CEMApiRegistry.isPure(customSlot) : !IMPURE.contains(fn); }

    String name() { return name; }
    Fn fn() { return fn; }
    CEMExpression[] args() { return args; }

    static boolean isConditional(Fn fn) { return fn == Fn.IF || fn == Fn.IFB; }

    static synchronized int label(String text) {
        Integer id = LABEL_IDS.get(text);
        if (id == null) {
            id = LABELS.size();
            LABELS.add(text);
            LABEL_IDS.put(text, id);
        }
        return id;
    }

    static String labelText(double id) {
        int i = (int) id;
        return i >= 0 && i < LABELS.size() ? LABELS.get(i) : String.valueOf(id);
    }

    private static void reportUnknown(String name, int argCount) {
        if (REPORTED_FUNCS.add(name)) {
            DatariumMain.LOGGER.warn("[CEM] unsupported function '{}' ({} args, model {}) - returning 0",
                    name, argCount, CEMRenderHooks.getActiveModelName());
        }
    }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        if (isConditional(fn)) return evaluateIf(ctx);
        double[] values = fn == Fn.CUSTOM ? new double[args.length] : scratch;
        for (int i = 0; i < args.length; i++) values[i] = args[i].evaluate(ctx);
        if (fn == Fn.CUSTOM) return CEMApiRegistry.call(customSlot, values);
        return CEMFunctionOps.call(fn, values);
    }

    private double evaluateIf(CEMRenderContext ctx) {
        if (args.length < 3) return 0;
        int i = 0;
        for (; i + 1 < args.length; i += 2) {
            if (args[i].evaluate(ctx) != 0) return args[i + 1].evaluate(ctx);
        }
        return i < args.length ? args[i].evaluate(ctx) : 0;
    }
}
