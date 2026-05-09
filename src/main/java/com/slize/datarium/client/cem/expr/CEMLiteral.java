package com.slize.datarium.client.cem.expr;

import org.jetbrains.annotations.NotNull;

public record CEMLiteral(double value) implements CEMExpression {

    @Override
    public double evaluate(CEMRenderContext ctx) {
        return value;
    }

    @Override
    public @NotNull String toString() {
        return String.valueOf(value);
    }
}