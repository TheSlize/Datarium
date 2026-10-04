package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.CEMManager;
import net.minecraft.client.Minecraft;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link CEMFunction.Fn} implementations over already evaluated args.
 * IF/IFB/CUSTOM are handled by {@link CEMFunction} itself.
 */
final class CEMFunctionOps {
    private CEMFunctionOps() {}

    private static final Set<String> REPORTED_CATCHES = ConcurrentHashMap.newKeySet();

    static double call(CEMFunction.Fn fn, double[] args) {
        if (CEMProfiler.enabled) CEMProfiler.hitFn(fn);
        return switch (fn) {
            case SIN -> Math.sin(a(args, 0));
            case COS -> Math.cos(a(args, 0));
            case TAN -> Math.tan(a(args, 0));
            case ASIN -> Math.asin(clamp(a(args, 0), -1, 1));
            case ACOS -> Math.acos(clamp(a(args, 0), -1, 1));
            case ATAN -> Math.atan(a(args, 0));
            case ATAN2 -> Math.atan2(a(args, 0), a(args, 1));
            case ABS -> Math.abs(a(args, 0));
            case FLOOR -> Math.floor(a(args, 0));
            case CEIL -> Math.ceil(a(args, 0));
            case ROUND -> Math.round(a(args, 0));
            case SQRT -> Math.sqrt(Math.max(0, a(args, 0)));
            case POW -> Math.pow(a(args, 0), a(args, 1));
            case EXP -> Math.exp(a(args, 0));
            case LOG -> Math.log(Math.max(0.0001, a(args, 0)));
            case MIN -> {
                if (args.length == 0) yield 0;
                double r = args[0];
                for (int i = 1; i < args.length; i++) r = Math.min(r, args[i]);
                yield r;
            }
            case MAX -> {
                if (args.length == 0) yield 0;
                double r = args[0];
                for (int i = 1; i < args.length; i++) r = Math.max(r, args[i]);
                yield r;
            }
            case CLAMP -> clamp(a(args, 0), a(args, 1), a(args, 2));
            case TORAD -> Math.toRadians(a(args, 0));
            case TODEG -> Math.toDegrees(a(args, 0));
            case IF, IFB, CUSTOM, UNKNOWN -> 0;
            case BETWEEN -> {
                if (args.length < 3) yield 0;
                double val = args[0], min = args[1], max = args[2];
                yield (val >= min && val <= max) ? 1 : 0;
            }
            case EQUALS -> {
                if (args.length < 2) yield 0;
                double av = args[0], bv = args[1];
                double tolerance = args.length >= 3 ? args[2] : 0.0001;
                yield Math.abs(av - bv) <= tolerance ? 1 : 0;
            }
            case RANDOM -> random(args);
            case RANDOMB -> random(args) >= 0.5 ? 1 : 0;
            case LERP -> lerp(a(args, 0), a(args, 1), a(args, 2));
            case FMOD -> {
                if (args.length < 2) yield 0;
                int bv = (int) (float) args[1];
                yield bv != 0 ? Math.floorMod((int) (float) args[0], bv) : Double.NaN;
            }
            case SIGNUM -> Math.signum(a(args, 0));
            case IN -> {
                double v0 = a(args, 0);
                for (int i = 1; i < args.length; i++) if (v0 == args[i]) yield 1;
                yield 0;
            }
            case WRAPDEG -> wrapDegrees(a(args, 0));
            case WRAPRAD -> Math.toRadians(wrapDegrees(Math.toDegrees(a(args, 0))));
            case DEGDIFF -> Math.abs(wrapDegrees(a(args, 1) - a(args, 0)));
            case RADDIFF -> Math.toRadians(Math.abs(wrapDegrees(
                    Math.toDegrees(a(args, 1)) - Math.toDegrees(a(args, 0)))));
            case FRAC -> {
                double v = a(args, 0);
                yield v - Math.floor(v);
            }
            case PRINT -> print(args, false);
            case PRINTB -> print(args, true);
            case CATCH -> {
                double x = a(args, 0);
                if (!Double.isNaN(x)) yield x;
                if (args.length >= 3) {
                    String id = CEMFunction.labelText(args[2]);
                    if (REPORTED_CATCHES.add(id)) DatariumMain.LOGGER.info("[CEM] print: catch({}) found NaN in x.", id);
                }
                yield a(args, 1);
            }
            case KEYFRAME -> keyframe(args, false);
            case KEYFRAMELOOP -> keyframe(args, true);
            case CATMULLROM -> catmullRom(a(args, 0), a(args, 1), a(args, 2),
                    a(args, 3), a(args, 4));
            case HERMITE -> {
                double t = a(args, 0), t2 = t * t, t3 = t2 * t;
                yield (2 * t3 - 3 * t2 + 1) * a(args, 1) + (t3 - 2 * t2 + t) * a(args, 3)
                        + (-2 * t3 + 3 * t2) * a(args, 2) + (t3 - t2) * a(args, 4);
            }
            case CUBICBEZIER -> {
                double t = a(args, 0), u = 1 - t;
                yield u * u * u * a(args, 1) + 3 * u * u * t * a(args, 2)
                        + 3 * u * t * t * a(args, 3) + t * t * t * a(args, 4);
            }
            case QUADBEZIER -> {
                double t = a(args, 0), u = 1 - t;
                yield u * u * a(args, 1) + 2 * u * t * a(args, 2) + t * t * a(args, 3);
            }
            default -> ease(fn, a(args, 0), a(args, 1), a(args, 2));
        };
    }

    private static double a(double[] args, int index) {
        return index < args.length ? args[index] : 0;
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static double wrapDegrees(double value) {
        double d = value % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    private static double random(double[] args) {
        if (args.length == 0) return Math.random();
        int x = Float.floatToIntBits((float) args[0]);
        x = x ^ 61 ^ x >> 16;
        x += x << 3;
        x ^= x >> 4;
        x *= 668265261;
        x ^= x >> 15;
        return Math.abs(x) / 2.14748365E9;
    }

    private static double print(double[] args, boolean bool) {
        double x = a(args, 2);
        int n = (int) a(args, 1);
        if (!Minecraft.getMinecraft().isGamePaused() && (n <= 0 || CEMManager.getFrameCounter() % n == 0)) {
            DatariumMain.LOGGER.info("[CEM] print: [{}] = {}", CEMFunction.labelText(a(args, 0)),
                    bool ? String.valueOf(x != 0) : String.valueOf(x));
        }
        return bool ? (x != 0 ? 1 : 0) : x;
    }

    private static double catmullRom(double t, double p0, double p1, double p2, double p3) {
        return 0.5 * (2.0 * p1 + (p2 - p0) * t
                + (2.0 * p0 - 5.0 * p1 + 4.0 * p2 - p3) * t * t
                + (3.0 * p1 - p0 - 3.0 * p2 + p3) * t * t * t);
    }

    private static double keyframe(double[] args, boolean loop) {
        int frames = args.length - 1;
        if (frames <= 0) return 0;
        int first = 1;
        double delta = args[0];
        int floor = (int) Math.floor(delta);
        double frac = delta - Math.floor(delta);
        if (loop) {
            return catmullRom(frac,
                    args[first + Math.floorMod(floor - 1, frames)],
                    args[first + Math.floorMod(floor, frames)],
                    args[first + Math.floorMod(floor + 1, frames)],
                    args[first + Math.floorMod(floor + 2, frames)]);
        }
        int end = frames - 1;
        if (floor >= end) return args[first + end];
        if (floor <= 0) return args[first];
        return catmullRom(frac,
                args[first + Math.clamp(floor - 1, 0, end)],
                args[first + floor],
                args[first + Math.min(floor + 1, end)],
                args[first + Math.min(floor + 2, end)]);
    }

    private static double ease(CEMFunction.Fn fn, double t, double start, double end) {
        double delta = end - start;
        return switch (fn) {
            case EASEINQUAD -> start + delta * t * t;
            case EASEOUTQUAD -> start + delta * -t * (t - 2);
            case EASEINOUTQUAD -> t < 0.5 ? start + delta * (2 * t * t) : start + delta * (-2 * t * (t - 2) - 1);
            case EASEINCUBIC -> start + delta * t * t * t;
            case EASEOUTCUBIC -> {
                double s = t - 1;
                yield start + delta * s * s * s + 1;
            }
            case EASEINOUTCUBIC -> {
                if (t < 0.5) yield start + delta * 4 * t * t * t;
                double s = t - 1;
                yield start + delta * s * (2 * s * s + 2) + 1;
            }
            case EASEINQUART -> start + delta * t * t * t * t;
            case EASEOUTQUART -> {
                double s = t - 1;
                yield start + delta * s * s * s * s + 1;
            }
            case EASEINOUTQUART -> {
                if (t < 0.5) yield start + delta * 8 * t * t * t * t;
                double s = t - 1;
                yield start + delta * s * (8 * s * s * s + 1) + 1;
            }
            case EASEINQUINT -> start + delta * t * t * t * t * t;
            case EASEOUTQUINT -> {
                double s = t - 1;
                yield start + delta * s * s * s * s * s + 1;
            }
            case EASEINOUTQUINT -> {
                if (t < 0.5) yield start + delta * 16 * t * t * t * t * t;
                double s = t - 1;
                yield start + delta * s * (16 * s * s * s * s + 1) + 1;
            }
            case EASEINSINE -> start + delta * (1 - Math.cos(t * Math.PI / 2));
            case EASEOUTSINE -> start + delta * Math.sin(t * Math.PI / 2);
            case EASEINOUTSINE -> start + delta * (-0.5 * (Math.cos(Math.PI * t) - 1));
            case EASEINEXPO -> start + delta * Math.pow(2, 10 * (t - 1));
            case EASEOUTEXPO -> start + delta * (-Math.pow(2, -10 * t) + 1);
            case EASEINOUTEXPO -> t < 1
                    ? start + delta * (0.5 * Math.pow(2, 10 * (t - 1)))
                    : start + delta * (0.5 * (-Math.pow(2, -10 * (t - 1)) + 2));
            case EASEINCIRC -> start + delta * -(Math.sqrt(1 - t * t) - 1);
            case EASEOUTCIRC -> start + delta * Math.sqrt(1 - (t - 1) * (t - 1));
            case EASEINOUTCIRC -> {
                double t2 = t * 2;
                if (t2 < 1) yield start + delta * (-0.5 * (Math.sqrt(1 - t2 * t2) - 1));
                double s = t2 - 2;
                yield start + delta * (0.5 * (Math.sqrt(1 - s * s) + 1));
            }
            case EASEINELASTIC -> {
                double s = t - 1;
                yield start + delta * (-Math.pow(2, 10 * s) * Math.sin((s - 0.3 / 4) * (2 * Math.PI) / 0.3));
            }
            case EASEOUTELASTIC -> start + delta * (Math.pow(2, -10 * t) * Math.sin((t - 0.3 / 4) * (2 * Math.PI) / 0.3) + 1);
            case EASEINOUTELASTIC -> {
                double s = t - 1;
                if (t < 0.5) yield start + delta * (-0.5 * Math.pow(2, 10 * s) * Math.sin((s - 0.225 / 4) * (2 * Math.PI) / 0.45));
                yield start + delta * (0.5 * Math.pow(2, -10 * s) * Math.sin((s - 0.225 / 4) * (2 * Math.PI) / 0.45) + 1);
            }
            case EASEINBOUNCE -> start + delta * (1 - bounceOut(1 - t));
            case EASEOUTBOUNCE -> start + delta * bounceOut(t);
            case EASEINOUTBOUNCE -> t < 0.5
                    ? start + delta * (0.5 * (1 - bounceOut(1 - t * 2)))
                    : start + delta * (0.5 * bounceOut(t * 2 - 1) + 0.5);
            case EASEINBACK -> start + delta * (t * t * (2.70158 * t - 1.70158));
            case EASEOUTBACK -> {
                double s = t - 1;
                yield start + delta * (s * s * (2.70158 * s + 1.70158) + 1);
            }
            case EASEINOUTBACK -> {
                if (t < 0.5) yield start + delta * (t * t * (7 * t - 2.5) * 2);
                double s = t - 1;
                yield start + delta * ((s * s * (7 * s + 2.5) + 2) * 2);
            }
            default -> 0;
        };
    }

    private static double bounceOut(double t) {
        if (t < 1 / 2.75) return 7.5625 * t * t;
        if (t < 2 / 2.75) {
            double s = t - 1.5 / 2.75;
            return 7.5625 * s * s + 0.75;
        }
        if (t < 2.5 / 2.75) {
            double s = t - 2.25 / 2.75;
            return 7.5625 * s * s + 0.9375;
        }
        double s = t - 2.625 / 2.75;
        return 7.5625 * s * s + 0.984375;
    }
}
