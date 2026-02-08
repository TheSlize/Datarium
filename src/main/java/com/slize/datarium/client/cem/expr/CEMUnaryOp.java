package com.slize.datarium.client.cem.expr;

public class CEMUnaryOp implements CEMExpression {
    public enum Op {
        NEG, NOT
    }

    private final Op op;
    private final CEMExpression operand;

    public CEMUnaryOp(Op op, CEMExpression operand) {
        this.op = op;
        this.operand = operand;
    }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        double val = operand.evaluate(ctx);
        return switch (op) {
            case NEG -> -val;
            case NOT -> val == 0 ? 1 : 0;
        };
    }
}