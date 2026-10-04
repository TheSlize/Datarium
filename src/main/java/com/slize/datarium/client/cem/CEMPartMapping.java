package com.slize.datarium.client.cem;

import com.slize.datarium.util.PackConverter;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.*;

public class CEMPartMapping {
    private static final String VANILLA = "net.minecraft.";
    private static final String VANILLA_MODELS = "net.minecraft.client.model.";
    private static final String ANY_MODEL = "*";

    @Nullable
    public static String getExtraHost(String cemPartName) {
        return PackConverter.partHost(cemPartName);
    }

    public static Map<String, ModelRenderer> mapParts(ModelBase model, @Nullable String variantName) {
        Map<String, ModelRenderer> result = new HashMap<>();
        if (model == null) return result;
        String modelName = variantName != null ? CEMManager.mappingName(variantName) : null;

        for (Class<?> current = model.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            String name = current.getName();
            if (!name.startsWith(VANILLA_MODELS)) continue;
            bind(model, current, PackConverter.modelParts(name.substring(VANILLA_MODELS.length())), result);
        }

        for (Class<?> current = model.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
            if (current.getName().startsWith(VANILLA)) continue;
            bind(model, current, PackConverter.modelParts(ANY_MODEL), result);
            for (Field field : current.getDeclaredFields()) {
                if (!ModelRenderer.class.isAssignableFrom(field.getType())) continue;
                String cem = toCemName(field.getName(), modelName);
                if (cem == null) continue;
                try {
                    field.setAccessible(true);
                    ModelRenderer renderer = (ModelRenderer) field.get(model);
                    if (renderer != null) result.putIfAbsent(cem, renderer);
                } catch (Exception ignored) {
                }
            }
        }

        return result;
    }

    private static void bind(ModelBase model, Class<?> owner, List<PackConverter.ModelPart> rows, Map<String, ModelRenderer> result) {
        for (PackConverter.ModelPart row : rows) {
            Object value = read(model, owner, row);
            if (value instanceof ModelRenderer renderer) {
                if (row.index() != PackConverter.ModelPart.WHOLE) continue;
                for (String part : row.parts()) result.putIfAbsent(part, renderer);
            } else if (value instanceof ModelRenderer[] array) {
                if (row.index() != PackConverter.ModelPart.WHOLE) {
                    if (row.index() >= array.length || array[row.index()] == null) continue;
                    for (String part : row.parts()) result.putIfAbsent(part, array[row.index()]);
                    continue;
                }
                for (int i = 0; i < array.length; i++) {
                    if (array[i] == null) continue;
                    for (String part : row.parts()) result.putIfAbsent(part + (i + 1), array[i]);
                }
            }
        }
    }

    @Nullable
    private static Object read(ModelBase model, Class<?> owner, PackConverter.ModelPart row) {
        for (String name : new String[]{row.field(), row.srg()}) {
            if (name == null) continue;
            try {
                Field field = owner.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(model);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static void collectFields(ModelBase model, Map<String, ModelRenderer> fields, Map<String, ModelRenderer[]> arrays) {
        Class<?> current = model.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                try {
                    if (ModelRenderer.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        ModelRenderer renderer = (ModelRenderer) field.get(model);
                        if (renderer != null) fields.putIfAbsent(field.getName(), renderer);
                    } else if (field.getType().isArray() && ModelRenderer.class.isAssignableFrom(field.getType().getComponentType())) {
                        field.setAccessible(true);
                        ModelRenderer[] value = (ModelRenderer[]) field.get(model);
                        if (value != null) arrays.putIfAbsent(field.getName(), value);
                    }
                } catch (Exception ignored) {
                }
            }
            current = current.getSuperclass();
        }
    }

    public static void applyTransformAliases(String modelName, Map<String, CEMPartTransform> transforms) {
        Map<String, String> aliases = modelName != null ? PackConverter.transformAliases(CEMManager.mappingName(modelName)) : null;
        if (aliases == null) return;
        for (Map.Entry<String, String> e : aliases.entrySet()) {
            CEMPartTransform src = transforms.get(e.getValue());
            if (src == null) continue;
            transforms.computeIfAbsent(e.getKey(), _ -> new CEMPartTransform()).copyUnsetFrom(src);
        }
    }

    /** ModelRenderer -> vanilla field name, for diagnostics. */
    public static Map<ModelRenderer, String> fieldNames(ModelBase model) {
        Map<ModelRenderer, String> out = new java.util.IdentityHashMap<>();
        if (model == null) return out;

        Map<String, ModelRenderer> fields = new HashMap<>();
        Map<String, ModelRenderer[]> arrays = new HashMap<>();
        collectFields(model, fields, arrays);

        for (Map.Entry<String, ModelRenderer> e : fields.entrySet()) {
            if (e.getValue() != null) out.putIfAbsent(e.getValue(), e.getKey());
        }
        for (Map.Entry<String, ModelRenderer[]> e : arrays.entrySet()) {
            ModelRenderer[] arr = e.getValue();
            for (int i = 0; i < arr.length; i++) {
                if (arr[i] != null) out.putIfAbsent(arr[i], e.getKey() + "[" + i + "]");
            }
        }
        return out;
    }

    @Nullable
    private static String toCemName(String fieldName, @Nullable String modelName) {
        if (fieldName.startsWith("field_")) return null;

        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            boolean boundary = (Character.isUpperCase(c) || Character.isDigit(c) != (!token.isEmpty()
                    && Character.isDigit(token.charAt(token.length() - 1))));
            if (boundary && !token.isEmpty()) {
                tokens.add(token.toString().toLowerCase(Locale.ROOT));
                token.setLength(0);
            }
            token.append(c);
        }
        if (!token.isEmpty()) tokens.add(token.toString().toLowerCase(Locale.ROOT));

        tokens.removeIf(t -> t.equals("model") || t.equals("biped") || t.equals("main")
                || (modelName != null && (modelName.equals(t) || modelName.startsWith(t + "_"))));
        if (tokens.isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        for (String t : tokens) {
            if (!out.isEmpty() && !Character.isDigit(t.charAt(0))) out.append('_');
            out.append(t);
        }
        return out.toString();
    }


    @Nullable
    public static Map<String, String> getReparents(String modelName) {
        return modelName != null ? PackConverter.reparents(CEMManager.mappingName(modelName)) : null;
    }
}
