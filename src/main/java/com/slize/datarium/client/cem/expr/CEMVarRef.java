package com.slize.datarium.client.cem.expr;

import javax.annotation.Nullable;

/** A {@link CEMVariable} name classified once at parse time. */
final class CEMVarRef {

    enum Kind { CUSTOM_VAR, CUSTOM_VARB, GLOBAL_VAR, GLOBAL_VARB, RENDER, PART, BUILTIN, API, UNKNOWN }

    final Kind kind;
    final String name;
    final int slot;
    final String partId;
    final String property;
    @Nullable final CEMBuiltinVar builtin;
    @Nullable final CEMRenderVar render;

    private CEMVarRef(Kind kind, String name, int slot, String partId, String property,
                      @Nullable CEMBuiltinVar builtin, @Nullable CEMRenderVar render) {
        this.kind = kind;
        this.name = name;
        this.slot = slot;
        this.partId = partId;
        this.property = property;
        this.builtin = builtin;
        this.render = render;
    }

    private static CEMVarRef slotted(Kind kind, String name, int slot) {
        return new CEMVarRef(kind, name, slot, null, null, null, null);
    }

    static CEMVarRef classify(String name) {
        int apiSlot = CEMApiRegistry.variableSlot(name);
        if (apiSlot >= 0) return slotted(Kind.API, name, apiSlot);
        if (name.startsWith("varb.")) return slotted(Kind.CUSTOM_VARB, name, CEMVarSlots.slotFor(name.substring(5)));
        if (name.startsWith("var.")) return slotted(Kind.CUSTOM_VAR, name, CEMVarSlots.slotFor(name.substring(4)));
        if (name.startsWith("global_varb.")) return slotted(Kind.GLOBAL_VARB, name, CEMGlobalVars.slotFor(name.substring(12)));
        if (name.startsWith("global_var.")) return slotted(Kind.GLOBAL_VAR, name, CEMGlobalVars.slotFor(name.substring(11)));
        if (name.startsWith("render.")) {
            CEMRenderVar render = CEMRenderVar.byName(name.substring(7));
            return render != null ? new CEMVarRef(Kind.RENDER, name, -1, null, null, null, render) : slotted(Kind.UNKNOWN, name, -1);
        }
        int lastDot = name.lastIndexOf('.');
        if (lastDot >= 0) {
            return new CEMVarRef(Kind.PART, name, -1, name.substring(0, lastDot), name.substring(lastDot + 1), null, null);
        }
        CEMBuiltinVar builtin = CEMBuiltinVar.byName(name);
        if (builtin != null) return new CEMVarRef(Kind.BUILTIN, name, -1, null, null, builtin, null);
        return slotted(Kind.UNKNOWN, name, CEMVarSlots.slotFor(name));
    }
}
