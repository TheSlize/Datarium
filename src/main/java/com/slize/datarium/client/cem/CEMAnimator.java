package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.expr.CEMExpression;
import com.slize.datarium.client.cem.expr.CEMExpressionParser;
import com.slize.datarium.client.cem.expr.CEMRenderContext;

import java.util.*;

public class CEMAnimator {
    private final CEMModel model;
    private final Map<String, CEMExpression> compiledExpressions;
    private final List<String> evaluationOrder;

    public CEMAnimator(CEMModel model) {
        this.model = model;
        this.compiledExpressions = new LinkedHashMap<>();
        this.evaluationOrder = new ArrayList<>();
        compileAnimations();
    }

    private void compileAnimations() {
        for (CEMAnimation anim : model.animations) {
            for (Map.Entry<String, String> entry : anim.expressions.entrySet()) {
                String key = entry.getKey();
                String exprStr = entry.getValue();

                try {
                    CEMExpression expr = CEMExpressionParser.parse(exprStr);
                    compiledExpressions.put(key, expr);
                    evaluationOrder.add(key);
                    //DatariumMain.LOGGER.debug("[CEM] Compiled animation: {} = {}", key, exprStr);
                } catch (Exception e) {
                    DatariumMain.LOGGER.warn("[CEM] Failed to compile expression: {} = {}", key, exprStr, e);
                }
            }
        }
        //DatariumMain.LOGGER.info("[CEM] Compiled {} expressions", compiledExpressions.size());
    }

    public void evaluate(CEMRenderContext ctx, Map<String, CEMPartTransform> transforms) {

        for (String key : evaluationOrder) {
            CEMExpression expr = compiledExpressions.get(key);
            if (expr == null) continue;

            try {
                double value = expr.evaluate(ctx);

                if (key.startsWith("var.")) {
                    ctx.setVariable(key.substring(4), value);
                } else if (key.startsWith("varb.")) {
                    ctx.setBoolVariable(key.substring(5), value != 0);
                } else if (key.contains(".")) {
                    int dotIdx = key.lastIndexOf('.');
                    String partId = key.substring(0, dotIdx);
                    String property = key.substring(dotIdx + 1);

                    ctx.setPartValue(partId, property, value);

                    CEMPartTransform transform = transforms.computeIfAbsent(partId, k -> new CEMPartTransform());
                    applyPropertyToTransform(transform, property, value);
                    //if(System.currentTimeMillis() % 10 == 0) DatariumMain.LOGGER.debug("[CEM] Animation target: {} property: {} value: {}", partId, property, value);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void applyPropertyToTransform(CEMPartTransform transform, String property, double value) {
        switch (property) {
            case "rx" -> { transform.rotateX = (float) value; transform.hasRotateX = true; }
            case "ry" -> { transform.rotateY = (float) value; transform.hasRotateY = true; }
            case "rz" -> { transform.rotateZ = (float) value; transform.hasRotateZ = true; }
            case "tx" -> { transform.translateX = (float) value; transform.hasTranslateX = true; }
            case "ty" -> { transform.translateY = (float) value; transform.hasTranslateY = true; }
            case "tz" -> { transform.translateZ = (float) value; transform.hasTranslateZ = true; }
            case "sx" -> { transform.scaleX = (float) value; transform.hasScaleX = true; }
            case "sy" -> { transform.scaleY = (float) value; transform.hasScaleY = true; }
            case "sz" -> { transform.scaleZ = (float) value; transform.hasScaleZ = true; }
            case "visible" -> { transform.visible = value != 0; transform.hasVisible = true; }
        }
    }
}