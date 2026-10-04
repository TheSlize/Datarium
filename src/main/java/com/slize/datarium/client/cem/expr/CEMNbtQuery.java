package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cet.CETNbt;

import javax.annotation.Nullable;

public final class CEMNbtQuery implements CEMExpression {

    private final String path;
    private final String matcher;
    @Nullable private final CETNbt.Tester tester;

    public CEMNbtQuery(String path, String matcher) {
        this.path = path;
        this.matcher = matcher;
        this.tester = CETNbt.Tester.of(path, matcher);
        if (tester == null) {
            DatariumMain.LOGGER.error("[CEM] nbt() animation function did not parse: nbt({} {}), please check your syntax.", path, matcher);
        }
    }

    public String path() { return path; }
    public String matcher() { return matcher; }

    @Override
    public double evaluate(CEMRenderContext ctx) {
        return test(ctx) ? 1 : 0;
    }

    boolean test(CEMRenderContext ctx) {
        if (CEMProfiler.enabled) CEMProfiler.hitNbt(path);
        return tester != null && tester.test(CETNbt.of(ctx.getRenderedObject()));
    }
}
