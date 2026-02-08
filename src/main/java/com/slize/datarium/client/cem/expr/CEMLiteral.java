package com.slize.datarium.client.cem.expr;

public class CEMLiteral implements CEMExpression {
    private final double value;

    public CEMLiteral(double value) {
        this.value = value;
    }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}