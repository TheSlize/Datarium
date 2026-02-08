package com.slize.datarium.client.cem.expr;

import org.jspecify.annotations.NonNull;

public record CEMVariable(String name) implements CEMExpression {

    @Override
    public double evaluate(CEMRenderContext ctx) {
        return ctx.getVariable(name);
    }

    @Override
    public @NonNull String toString() {
        return name;
    }
}