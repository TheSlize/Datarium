package com.slize.datarium.client.cem.expr;

public record CEMUnaryOp(Op op, CEMExpression operand) implements CEMExpression {
    @Override
    public double evaluate(CEMRenderContext ctx) {
        double val = operand.evaluate(ctx);
        return switch (op) {
            case NEG -> -val;
            case NOT -> val == 0 ? 1 : 0;
        };
    }

    public enum Op {
        NEG, NOT
    }
}