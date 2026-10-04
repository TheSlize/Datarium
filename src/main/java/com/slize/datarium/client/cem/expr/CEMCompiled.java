package com.slize.datarium.client.cem.expr;

import com.slize.datarium.client.cem.CEMAnimator;

/**
 * A fast form of an animation expression that {@link CEMAnimator} uses evaluating eavh frame.
 * Each node does exactly one thing instead of going over a switch function, so it saves some CPU power for actually rendering entities.
 */
public abstract class CEMCompiled {

    public abstract double eval(CEMRenderContext ctx);

    public static CEMCompiled of(CEMExpression e) {
        if (e instanceof CEMLiteral(double value)) return new Const(value);
        if (e instanceof CEMBinaryOp(CEMBinaryOp.Op op, CEMExpression left, CEMExpression right)) {
            return binary(op, of(left), of(right));
        }
        if (e instanceof CEMUnaryOp(CEMUnaryOp.Op op, CEMExpression operand)) {
            CEMCompiled a = of(operand);
            return op == CEMUnaryOp.Op.NEG ? new Neg(a) : new Not(a);
        }
        if (e instanceof CEMVariable v) return variable(v.ref());
        if (e instanceof CEMFunction f) return function(f);
        return new Wrapped(e);
    }

    private static CEMCompiled variable(CEMVarRef ref) {
        return switch (ref.kind) {
            case CUSTOM_VAR -> new Var(ref.slot);
            case CUSTOM_VARB -> new Varb(ref.slot);
            case BUILTIN -> new Builtin(ref.builtin);
            default -> new Ref(ref);
        };
    }

    private static CEMCompiled binary(CEMBinaryOp.Op op, CEMCompiled l, CEMCompiled r) {
        if (r instanceof Const c) {
            double k = c.value;
            switch (op) {
                case ADD: return new AddK(l, k);
                case SUB: return new SubK(l, k);
                case MUL: return new MulK(l, k);
                case DIV: if (k != 0) return new DivK(l, k); break;
                case EQ: return new EqK(l, k);
                case NEQ: return new NeqK(l, k);
                case LT: return new LtK(l, k);
                case GT: return new GtK(l, k);
                case LTE: return new LteK(l, k);
                case GTE: return new GteK(l, k);
                default: break;
            }
        } else if (l instanceof Const c) {
            double k = c.value;
            switch (op) {
                case ADD: return new AddK(r, k);
                case SUB: return new KSub(k, r);
                case MUL: return new MulK(r, k);
                case DIV: return new KDiv(k, r);
                default: break;
            }
        }
        return switch (op) {
            case ADD -> new Add(l, r);
            case SUB -> new Sub(l, r);
            case MUL -> new Mul(l, r);
            case DIV -> new Div(l, r);
            case MOD -> new Mod(l, r);
            case EQ -> new Eq(l, r);
            case NEQ -> new Neq(l, r);
            case LT -> new Lt(l, r);
            case GT -> new Gt(l, r);
            case LTE -> new Lte(l, r);
            case GTE -> new Gte(l, r);
            case AND -> new And(l, r);
            case OR -> new Or(l, r);
        };
    }

    private static CEMCompiled function(CEMFunction f) {
        CEMFunction.Fn fn = f.fn();
        if (fn == CEMFunction.Fn.CUSTOM || fn == CEMFunction.Fn.UNKNOWN) return new Wrapped(f);

        CEMExpression[] source = f.args();
        int n = source.length;
        CEMCompiled[] a = new CEMCompiled[n];
        for (int i = 0; i < n; i++) a[i] = of(source[i]);

        if (CEMFunction.isConditional(fn)) {
            if (n < 3) return new Const(0);
            return n == 3 ? new If(a[0], a[1], a[2]) : new IfChain(a);
        }
        if (n == 1) {
            switch (fn) {
                case SIN: return new Sin(a[0]);
                case COS: return new Cos(a[0]);
                case ABS: return new Abs(a[0]);
                case FLOOR: return new Floor(a[0]);
                case CEIL: return new Ceil(a[0]);
                case SQRT: return new Sqrt(a[0]);
                case TORAD: return new ToRad(a[0]);
                case TODEG: return new ToDeg(a[0]);
                case SIGNUM: return new Signum(a[0]);
                case FRAC: return new Frac(a[0]);
                default: break;
            }
        } else if (n == 2) {
            switch (fn) {
                case MIN: return new Min(a[0], a[1]);
                case MAX: return new Max(a[0], a[1]);
                case POW: return new Pow(a[0], a[1]);
                default: break;
            }
        } else if (n == 3) {
            switch (fn) {
                case CLAMP: return new Clamp(a[0], a[1], a[2]);
                case LERP: return new Lerp(a[0], a[1], a[2]);
                case BETWEEN: return new Between(a[0], a[1], a[2]);
                default: break;
            }
        }
        return new Call(fn, a);
    }

    private static final class Const extends CEMCompiled {
        final double value;
        Const(double value) { this.value = value; }
        @Override public double eval(CEMRenderContext ctx) { return value; }
    }

    private static final class Wrapped extends CEMCompiled {
        final CEMExpression e;
        Wrapped(CEMExpression e) { this.e = e; }
        @Override public double eval(CEMRenderContext ctx) { return e.evaluate(ctx); }
    }

    private static final class Var extends CEMCompiled {
        final int slot;
        Var(int slot) { this.slot = slot; }
        @Override public double eval(CEMRenderContext ctx) { return ctx.variable(slot); }
    }

    private static final class Varb extends CEMCompiled {
        final int slot;
        Varb(int slot) { this.slot = slot; }
        @Override public double eval(CEMRenderContext ctx) { return ctx.boolVariable(slot); }
    }

    private static final class Builtin extends CEMCompiled {
        final CEMBuiltinVar builtin;
        Builtin(CEMBuiltinVar builtin) { this.builtin = builtin; }
        @Override public double eval(CEMRenderContext ctx) { return ctx.resolveBuiltin(builtin); }
    }

    private static final class Ref extends CEMCompiled {
        final CEMVarRef ref;
        Ref(CEMVarRef ref) { this.ref = ref; }
        @Override public double eval(CEMRenderContext ctx) { return ctx.resolve(ref); }
    }

    private static final class Neg extends CEMCompiled {
        final CEMCompiled a;
        Neg(CEMCompiled a) { this.a = a; }
        @Override public double eval(CEMRenderContext ctx) { return -a.eval(ctx); }
    }

    private static final class Not extends CEMCompiled {
        final CEMCompiled a;
        Not(CEMCompiled a) { this.a = a; }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) == 0 ? 1 : 0; }
    }

    private abstract static class Binary extends CEMCompiled {
        final CEMCompiled l;
        final CEMCompiled r;
        Binary(CEMCompiled l, CEMCompiled r) { this.l = l; this.r = r; }
    }

    private static final class Add extends Binary {
        Add(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) + r.eval(ctx); }
    }

    private static final class Sub extends Binary {
        Sub(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) - r.eval(ctx); }
    }

    private static final class Mul extends Binary {
        Mul(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) * r.eval(ctx); }
    }

    private static final class Div extends Binary {
        Div(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) {
            double a = l.eval(ctx);
            double b = r.eval(ctx);
            return b != 0 ? a / b : 0;
        }
    }

    private static final class Mod extends Binary {
        Mod(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) {
            double a = l.eval(ctx);
            double b = r.eval(ctx);
            return b != 0 ? a % b : 0;
        }
    }

    private static final class Eq extends Binary {
        Eq(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return Math.abs(l.eval(ctx) - r.eval(ctx)) < 0.0001 ? 1 : 0; }
    }

    private static final class Neq extends Binary {
        Neq(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return Math.abs(l.eval(ctx) - r.eval(ctx)) >= 0.0001 ? 1 : 0; }
    }

    private static final class Lt extends Binary {
        Lt(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) < r.eval(ctx) ? 1 : 0; }
    }

    private static final class Gt extends Binary {
        Gt(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) > r.eval(ctx) ? 1 : 0; }
    }

    private static final class Lte extends Binary {
        Lte(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) <= r.eval(ctx) ? 1 : 0; }
    }

    private static final class Gte extends Binary {
        Gte(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) >= r.eval(ctx) ? 1 : 0; }
    }

    private static final class And extends Binary {
        And(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) != 0 && r.eval(ctx) != 0 ? 1 : 0; }
    }

    private static final class Or extends Binary {
        Or(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return l.eval(ctx) != 0 || r.eval(ctx) != 0 ? 1 : 0; }
    }

    private abstract static class WithConst extends CEMCompiled {
        final CEMCompiled a;
        final double k;
        WithConst(CEMCompiled a, double k) { this.a = a; this.k = k; }
    }

    private static final class AddK extends WithConst {
        AddK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) + k; }
    }

    private static final class SubK extends WithConst {
        SubK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) - k; }
    }

    private static final class KSub extends WithConst {
        KSub(double k, CEMCompiled a) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return k - a.eval(ctx); }
    }

    private static final class MulK extends WithConst {
        MulK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) * k; }
    }

    private static final class DivK extends WithConst {
        DivK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) / k; }
    }

    private static final class KDiv extends WithConst {
        KDiv(double k, CEMCompiled a) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) {
            double b = a.eval(ctx);
            return b != 0 ? k / b : 0;
        }
    }

    private static final class EqK extends WithConst {
        EqK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return Math.abs(a.eval(ctx) - k) < 0.0001 ? 1 : 0; }
    }

    private static final class NeqK extends WithConst {
        NeqK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return Math.abs(a.eval(ctx) - k) >= 0.0001 ? 1 : 0; }
    }

    private static final class LtK extends WithConst {
        LtK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) < k ? 1 : 0; }
    }

    private static final class GtK extends WithConst {
        GtK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) > k ? 1 : 0; }
    }

    private static final class LteK extends WithConst {
        LteK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) <= k ? 1 : 0; }
    }

    private static final class GteK extends WithConst {
        GteK(CEMCompiled a, double k) { super(a, k); }
        @Override public double eval(CEMRenderContext ctx) { return a.eval(ctx) >= k ? 1 : 0; }
    }

    private static final class If extends CEMCompiled {
        final CEMCompiled condition;
        final CEMCompiled then;
        final CEMCompiled otherwise;
        If(CEMCompiled condition, CEMCompiled then, CEMCompiled otherwise) {
            this.condition = condition;
            this.then = then;
            this.otherwise = otherwise;
        }
        @Override public double eval(CEMRenderContext ctx) {
            return condition.eval(ctx) != 0 ? then.eval(ctx) : otherwise.eval(ctx);
        }
    }

    private static final class IfChain extends CEMCompiled {
        final CEMCompiled[] args;
        IfChain(CEMCompiled[] args) { this.args = args; }
        @Override public double eval(CEMRenderContext ctx) {
            int i = 0;
            for (; i + 1 < args.length; i += 2) {
                if (args[i].eval(ctx) != 0) return args[i + 1].eval(ctx);
            }
            return i < args.length ? args[i].eval(ctx) : 0;
        }
    }

    private static final class Call extends CEMCompiled {
        final CEMFunction.Fn fn;
        final CEMCompiled[] args;
        final double[] values;
        Call(CEMFunction.Fn fn, CEMCompiled[] args) {
            this.fn = fn;
            this.args = args;
            this.values = new double[args.length];
        }
        @Override public double eval(CEMRenderContext ctx) {
            for (int i = 0; i < args.length; i++) values[i] = args[i].eval(ctx);
            return CEMFunctionOps.call(fn, values);
        }
    }

    private abstract static class Unary extends CEMCompiled {
        final CEMCompiled a;
        Unary(CEMCompiled a) { this.a = a; }
    }

    private static final class Sin extends Unary {
        Sin(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.sin(a.eval(ctx)); }
    }

    private static final class Cos extends Unary {
        Cos(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.cos(a.eval(ctx)); }
    }

    private static final class Abs extends Unary {
        Abs(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.abs(a.eval(ctx)); }
    }

    private static final class Floor extends Unary {
        Floor(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.floor(a.eval(ctx)); }
    }

    private static final class Ceil extends Unary {
        Ceil(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.ceil(a.eval(ctx)); }
    }

    private static final class Sqrt extends Unary {
        Sqrt(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.sqrt(Math.max(0, a.eval(ctx))); }
    }

    private static final class ToRad extends Unary {
        ToRad(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.toRadians(a.eval(ctx)); }
    }

    private static final class ToDeg extends Unary {
        ToDeg(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.toDegrees(a.eval(ctx)); }
    }

    private static final class Signum extends Unary {
        Signum(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) { return Math.signum(a.eval(ctx)); }
    }

    private static final class Frac extends Unary {
        Frac(CEMCompiled a) { super(a); }
        @Override public double eval(CEMRenderContext ctx) {
            double v = a.eval(ctx);
            return v - Math.floor(v);
        }
    }

    private static final class Min extends Binary {
        Min(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return Math.min(l.eval(ctx), r.eval(ctx)); }
    }

    private static final class Max extends Binary {
        Max(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return Math.max(l.eval(ctx), r.eval(ctx)); }
    }

    private static final class Pow extends Binary {
        Pow(CEMCompiled l, CEMCompiled r) { super(l, r); }
        @Override public double eval(CEMRenderContext ctx) { return Math.pow(l.eval(ctx), r.eval(ctx)); }
    }

    private abstract static class Ternary extends CEMCompiled {
        final CEMCompiled a;
        final CEMCompiled b;
        final CEMCompiled c;
        Ternary(CEMCompiled a, CEMCompiled b, CEMCompiled c) { this.a = a; this.b = b; this.c = c; }
    }

    private static final class Clamp extends Ternary {
        Clamp(CEMCompiled a, CEMCompiled b, CEMCompiled c) { super(a, b, c); }
        @Override public double eval(CEMRenderContext ctx) {
            double val = a.eval(ctx);
            double min = b.eval(ctx);
            double max = c.eval(ctx);
            return Math.max(min, Math.min(max, val));
        }
    }

    private static final class Lerp extends Ternary {
        Lerp(CEMCompiled a, CEMCompiled b, CEMCompiled c) { super(a, b, c); }
        @Override public double eval(CEMRenderContext ctx) {
            double delta = a.eval(ctx);
            double start = b.eval(ctx);
            double end = c.eval(ctx);
            return start + delta * (end - start);
        }
    }

    private static final class Between extends Ternary {
        Between(CEMCompiled a, CEMCompiled b, CEMCompiled c) { super(a, b, c); }
        @Override public double eval(CEMRenderContext ctx) {
            double val = a.eval(ctx);
            double min = b.eval(ctx);
            double max = c.eval(ctx);
            return val >= min && val <= max ? 1 : 0;
        }
    }
}
