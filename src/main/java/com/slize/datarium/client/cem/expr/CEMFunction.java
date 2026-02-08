package com.slize.datarium.client.cem.expr;

import java.util.List;
import java.util.Random;

public class CEMFunction implements CEMExpression {
    private final String name;
    private final List<CEMExpression> args;
    private static final Random RANDOM = new Random();

    public CEMFunction(String name, List<CEMExpression> args) {
        this.name = name;
        this.args = args;
    }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        return switch (name) {
            case "sin" -> Math.sin(arg(0, ctx));
            case "cos" -> Math.cos(arg(0, ctx));
            case "tan" -> Math.tan(arg(0, ctx));
            case "asin" -> Math.asin(clamp(arg(0, ctx), -1, 1));
            case "acos" -> Math.acos(clamp(arg(0, ctx), -1, 1));
            case "atan" -> Math.atan(arg(0, ctx));
            case "atan2" -> Math.atan2(arg(0, ctx), arg(1, ctx));
            case "abs" -> Math.abs(arg(0, ctx));
            case "floor" -> Math.floor(arg(0, ctx));
            case "ceil" -> Math.ceil(arg(0, ctx));
            case "round" -> Math.round(arg(0, ctx));
            case "sqrt" -> Math.sqrt(Math.max(0, arg(0, ctx)));
            case "pow" -> Math.pow(arg(0, ctx), arg(1, ctx));
            case "exp" -> Math.exp(arg(0, ctx));
            case "log" -> Math.log(Math.max(0.0001, arg(0, ctx)));
            case "min" -> evalMin(ctx);
            case "max" -> evalMax(ctx);
            case "clamp" -> clamp(arg(0, ctx), arg(1, ctx), arg(2, ctx));
            case "torad" -> Math.toRadians(arg(0, ctx));
            case "todeg" -> Math.toDegrees(arg(0, ctx));
            case "if" -> evaluateIf(ctx);
            case "between" -> evaluateBetween(ctx);
            case "equals" -> evaluateEquals(ctx);
            case "random" -> evaluateRandom(ctx);
            case "lerp" -> evaluateLerp(ctx);
            case "fmod" -> evaluateFmod(ctx);
            case "sign" -> Math.signum(arg(0, ctx));
            case "frac" -> {
                double v = arg(0, ctx);
                yield v - Math.floor(v);
            }
            case "print" -> {
                double val = arg(0, ctx);
                System.out.println("[CEM] print: " + val);
                yield val;
            }
            default -> 0;
        };
    }

    private double arg(int index, CEMRenderContext ctx) {
        if (index < args.size()) {
            return args.get(index).evaluate(ctx);
        }
        return 0;
    }

    private double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private double evalMin(CEMRenderContext ctx) {
        if (args.isEmpty()) return 0;
        double result = arg(0, ctx);
        for (int i = 1; i < args.size(); i++) {
            result = Math.min(result, args.get(i).evaluate(ctx));
        }
        return result;
    }

    private double evalMax(CEMRenderContext ctx) {
        if (args.isEmpty()) return 0;
        double result = arg(0, ctx);
        for (int i = 1; i < args.size(); i++) {
            result = Math.max(result, args.get(i).evaluate(ctx));
        }
        return result;
    }

    private double evaluateIf(CEMRenderContext ctx) {
        // Multi-branch if: if(cond1, val1, cond2, val2, ..., default)
        // Or simple: if(cond, true_val, false_val)
        int size = args.size();

        if (size >= 3) {
            // Check pairs of (condition, value)
            for (int i = 0; i + 1 < size - 1; i += 2) {
                double cond = args.get(i).evaluate(ctx);
                if (cond != 0) {
                    return args.get(i + 1).evaluate(ctx);
                }
            }

            // If odd number of args, last is default
            // If even, treat last pair as condition + value, then no default (return 0)
            if (size % 2 == 1) {
                return args.get(size - 1).evaluate(ctx);
            } else {
                // Check last pair
                double cond = args.get(size - 2).evaluate(ctx);
                if (cond != 0) {
                    return args.get(size - 1).evaluate(ctx);
                }
            }
        }
        return 0;
    }

    private double evaluateBetween(CEMRenderContext ctx) {
        if (args.size() >= 3) {
            double val = arg(0, ctx);
            double min = arg(1, ctx);
            double max = arg(2, ctx);
            return (val >= min && val <= max) ? 1 : 0;
        }
        return 0;
    }

    private double evaluateEquals(CEMRenderContext ctx) {
        if (args.size() >= 2) {
            double a = arg(0, ctx);
            double b = arg(1, ctx);
            double tolerance = args.size() >= 3 ? arg(2, ctx) : 0.0001;
            return Math.abs(a - b) <= tolerance ? 1 : 0;
        }
        return 0;
    }

    private double evaluateRandom(CEMRenderContext ctx) {
        if (!args.isEmpty()) {
            double seed = arg(0, ctx);
            // Use entity ID for consistent randomness per entity
            long longSeed = Double.doubleToLongBits(seed) ^ ctx.getEntityId();
            RANDOM.setSeed(longSeed);
            return RANDOM.nextDouble();
        }
        return RANDOM.nextDouble();
    }

    private double evaluateLerp(CEMRenderContext ctx) {
        if (args.size() >= 3) {
            double a = arg(0, ctx);
            double b = arg(1, ctx);
            double t = arg(2, ctx);
            return a + (b - a) * t;
        }
        return 0;
    }

    private double evaluateFmod(CEMRenderContext ctx) {
        if (args.size() >= 2) {
            double a = arg(0, ctx);
            double b = arg(1, ctx);
            if (b != 0) {
                return a - b * Math.floor(a / b);
            }
        }
        return 0;
    }
}