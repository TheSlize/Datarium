package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cet.CETRender;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

import javax.annotation.Nullable;
import java.util.*;

public class CEMModelWrapper {
    private final CEMModel cemModel;
    private final String modelName;
    private final Map<String, CEMModelRenderer> partRenderers;
    private final Map<String, CEMModelRenderer> hierarchyCache = new HashMap<>();
    private final Map<String, CEMModelRenderer> conventionCache = new HashMap<>();
    private final Map<String, CEMModelRenderer> attachmentCache = new HashMap<>();
    private final List<CEMModelRenderer> rootRenderers = new ArrayList<>();

    /** tokens that never appear in CEM part names */
    private static final Set<String> NOISE_TOKENS = new HashSet<>(Arrays.asList(
            "model", "biped", "entity", "main", "renderer", "part", "the"));

    public CEMModelWrapper(CEMModel cemModel, ModelBase vanillaModel, String modelName) {
        this.cemModel = cemModel;
        this.modelName = CEMManager.mappingName(modelName);
        this.partRenderers = new HashMap<>();
        buildRenderers(vanillaModel);
    }

    private void registerRendererRecursive(CEMModelRenderer renderer, CEMModelPart part, @Nullable String parentPath) {
        String id = part.id != null && !part.id.isEmpty() ? part.id : null;
        String partName = part.part != null && !part.part.isEmpty() ? part.part : null;

        String currentPath = null;
        if (id != null) {
            currentPath = parentPath != null ? parentPath + "." + id : id;
        } else if (partName != null) {
            currentPath = parentPath != null ? parentPath + "." + partName : partName;
        }

        if (id != null) partRenderers.put(id, renderer);
        if (partName != null) partRenderers.putIfAbsent(partName, renderer);
        if (currentPath != null && !currentPath.equals(id) && !currentPath.equals(partName)) {
            partRenderers.put(currentPath, renderer);
        }

        List<CEMModelRenderer> children = renderer.getCemChildren();
        for (int i = 0; i < children.size(); i++) {
            registerRendererRecursive(children.get(i), part.submodels.get(i), currentPath);
        }
    }

    @Nullable
    public CEMModelRenderer getPartRenderer(String partName) {
        CEMModelRenderer result = partRenderers.get(partName);
        if (result != null) return result;
        if (partName.indexOf(':') >= 0) return resolveHierarchy(partName);

        if (conventionCache.containsKey(partName)) return conventionCache.get(partName);
        result = resolveByConvention(partName);
        conventionCache.put(partName, result);
        return result;
    }

    @Nullable
    public CEMModelRenderer findAttachment(String type) {
        if (attachmentCache.containsKey(type)) return attachmentCache.get(type);
        CEMModelRenderer found = null;
        ArrayDeque<CEMModelRenderer> queue = new ArrayDeque<>(rootRenderers);
        while (!queue.isEmpty() && found == null) {
            CEMModelRenderer renderer = queue.poll();
            if (renderer.getCemPart().attachments.containsKey(type)) found = renderer;
            queue.addAll(renderer.getCemChildren());
        }
        attachmentCache.put(type, found);
        return found;
    }

    @Nullable
    public CEMModelRenderer findRootByPart(String part) {
        for (CEMModelRenderer renderer : rootRenderers) {
            if (part.equals(renderer.getCemPart().part)) return renderer;
        }
        return null;
    }

    @Nullable
    private CEMModelRenderer resolveHierarchy(String path) {
        if (hierarchyCache.containsKey(path)) return hierarchyCache.get(path);
        String[] segments = path.split(":");
        CEMModelRenderer current = segments.length > 0 && !segments[0].isEmpty() ? getPartRenderer(segments[0]) : null;
        for (int i = 1; i < segments.length && current != null; i++) {
            current = findDescendant(current, segments[i]);
        }
        hierarchyCache.put(path, current);
        return current;
    }

    @Nullable
    private static CEMModelRenderer findDescendant(CEMModelRenderer root, String id) {
        ArrayDeque<CEMModelRenderer> queue = new ArrayDeque<>(root.getCemChildren());
        while (!queue.isEmpty()) {
            CEMModelRenderer renderer = queue.poll();
            if (id.equals(renderer.getCemPart().id)) return renderer;
            queue.addAll(renderer.getCemChildren());
        }
        return null;
    }

    /**
     * Vanilla fields are camelCase and carry the mob name ("wolfHeadMain"), CEM parts are the
     * snake_case remainder ("head"). Tries progressively shorter token sets.
     */
    @Nullable
    private CEMModelRenderer resolveByConvention(String fieldName) {
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c) && !token.isEmpty()) {
                tokens.add(token.toString().toLowerCase(Locale.ROOT));
                token.setLength(0);
            }
            token.append(c);
        }
        if (!token.isEmpty()) tokens.add(token.toString().toLowerCase(Locale.ROOT));

        tokens.removeIf(t -> NOISE_TOKENS.contains(t) || t.equals(modelName) || modelName.startsWith(t));
        if (tokens.isEmpty()) return null;

        for (int end = tokens.size(); end > 0; end--) {
            for (int start = 0; start <= tokens.size() - end; start++) {
                CEMModelRenderer r = partRenderers.get(String.join("_", tokens.subList(start, start + end)));
                if (r != null) return r;
            }
        }
        return null;
    }

    private void buildRenderers(ModelBase vanillaModel) {
        int texW = cemModel.textureSize[0];
        int texH = cemModel.textureSize[1];

        for (CEMModelPart part : cemModel.parts) {
            CEMModelRenderer renderer = new CEMModelRenderer(vanillaModel, part, texW, texH, null);
            rootRenderers.add(renderer);
            registerRendererRecursive(renderer, part, null);
        }

        DatariumMain.LOGGER.info("[CEM] Registered {} part   renderers: {}", partRenderers.size(), partRenderers.keySet());
    }

    /** The wrapper is shared by every entity of the type - stale transforms leak between them. */
    public void clearTransforms() {
        for (CEMModelRenderer r : partRenderers.values()) r.setTransform(null);
    }

    public List<CEMModelRenderer> getRootRenderers() {
        return rootRenderers;
    }

    public void detachVanillaParts() {
        hostedRoots.clear();
        for (CEMModelRenderer r : partRenderers.values()) {
            r.setVanillaPart(null);
            r.setPivotOverride(null);
            r.clearExtras();
        }
    }

    public void renderOrphanRoots(float scale) {
        for (CEMModelRenderer r : rootRenderers) {
            if (r.isAttached() || r.getVanillaPart() != null || hostedRoots.contains(r)) continue;
            if (CETRender.isTracking()) CETRender.renderTopLevel(() -> r.renderWithVanilla(scale));
            else r.renderWithVanilla(scale);
        }
    }

    public void applyTransforms(Map<String, CEMPartTransform> transforms) {
        for (Map.Entry<String, CEMPartTransform> entry : transforms.entrySet()) {
            String key = entry.getKey();
            CEMModelRenderer renderer = key.indexOf(':') >= 0 ? resolveHierarchy(key) : partRenderers.get(key);
            if (renderer != null) {
                renderer.setTransform(entry.getValue());
            }
        }
    }

    public void renderDebug(float scale) {
        Set<ModelRenderer> nested = new HashSet<>();
        for (CEMModelRenderer r : new HashSet<>(partRenderers.values())) {
            ModelRenderer vp = r.getVanillaPart();
            if (vp != null && vp.childModels != null) nested.addAll(vp.childModels);
        }

        for (CEMModelPart part : cemModel.parts) {
            String key = part.id != null ? part.id : part.part;
            CEMModelRenderer renderer = getPartRenderer(key);
            if (renderer == null) continue;
            ModelRenderer vp = renderer.getVanillaPart();
            if (vp != null && nested.contains(vp)) continue;
            renderer.renderDebugOnly(scale);
        }
    }

    public Map<String, CEMModelRenderer> getAllParts() {
        return partRenderers;
    }

    private final Set<CEMModelRenderer> hostedRoots =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public void markHosted(CEMModelRenderer root) { hostedRoots.add(root); }

}