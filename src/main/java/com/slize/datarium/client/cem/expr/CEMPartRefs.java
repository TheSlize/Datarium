package com.slize.datarium.client.cem.expr;

import java.util.Set;

/** Which "part.property" names an expression reads, for skipping animations nothing depends on. */
public final class CEMPartRefs {
    private CEMPartRefs() {}

    /** @return false if the expression holds a node this cannot see through. */
    public static boolean collect(CEMExpression e, Set<String> out) {
        if (e instanceof CEMLiteral || e instanceof CEMNbtQuery) return true;
        if (e instanceof CEMVariable v) {
            if (v.ref().kind == CEMVarRef.Kind.PART) out.add(v.name());
            return true;
        }
        if (e instanceof CEMBinaryOp(CEMBinaryOp.Op _, CEMExpression left, CEMExpression right)) {
            return collect(left, out) & collect(right, out);
        }
        if (e instanceof CEMUnaryOp(CEMUnaryOp.Op _, CEMExpression operand)) return collect(operand, out);
        if (e instanceof CEMFunction f) {
            boolean known = true;
            for (CEMExpression arg : f.args()) known &= collect(arg, out);
            return known;
        }
        return false;
    }
}
