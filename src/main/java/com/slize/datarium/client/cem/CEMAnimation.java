package com.slize.datarium.client.cem;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

public class CEMAnimation {
    public Map<String, String> expressions;
    @Nullable public CEMModelPart owner;

    public CEMAnimation() {
        this.expressions = new LinkedHashMap<>();
    }
}
