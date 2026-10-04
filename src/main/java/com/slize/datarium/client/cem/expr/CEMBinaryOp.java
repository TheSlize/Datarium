package com.slize.datarium.client.cem.expr;

public record CEMBinaryOp(Op op, CEMExpression left, CEMExpression right) implements CEMExpression {
    static CEMExpression of(Op op, CEMExpression left, CEMExpression right) {
        CEMBinaryOp node = new CEMBinaryOp(op, left, right);
        return left instanceof CEMLiteral && right instanceof CEMLiteral ? new CEMLiteral(node.evaluate(null)) : node;
    }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        if (op == Op.AND) return left.evaluate(ctx) != 0 && right.evaluate(ctx) != 0 ? 1 : 0;
        if (op == Op.OR) return left.evaluate(ctx) != 0 || right.evaluate(ctx) != 0 ? 1 : 0;

        double l = left.evaluate(ctx);
        double r = right.evaluate(ctx);
        return switch (op) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case DIV -> r != 0 ? l / r : 0;
            case MOD -> r != 0 ? l % r : 0;
            case EQ -> Math.abs(l - r) < 0.0001 ? 1 : 0;
            case NEQ -> Math.abs(l - r) >= 0.0001 ? 1 : 0;
            case LT -> l < r ? 1 : 0;
            case GT -> l > r ? 1 : 0;
            case LTE -> l <= r ? 1 : 0;
            case GTE -> l >= r ? 1 : 0;
            default -> 0.0;
        };
    }

    public enum Op {
        ADD, SUB, MUL, DIV, MOD,
        EQ, NEQ, LT, GT, LTE, GTE,
        AND, OR
    }
}