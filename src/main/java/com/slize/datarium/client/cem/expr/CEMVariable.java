package com.slize.datarium.client.cem.expr;

import org.jspecify.annotations.NonNull;

public final class CEMVariable implements CEMExpression {
    private final String name;
    private final CEMVarRef ref;

    public CEMVariable(String name) {
        this.name = name;
        this.ref = CEMVarRef.classify(name);
    }

    public String name() {
        return name;
    }

    CEMVarRef ref() {
        return ref;
    }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        return ctx.resolve(ref);
    }

    @Override
    public @NonNull String toString() {
        return name;
    }
}
