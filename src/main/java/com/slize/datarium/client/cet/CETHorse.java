package com.slize.datarium.client.cet;

import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class CETHorse {
    private static final Map<ResourceLocation, Map<CETTexture, CETTexture>> LEGACY = new HashMap<>();
    private static final Map<ResourceLocation, Map<CETTexture, CETTexture>> MODERN = new HashMap<>();

    private CETHorse() {}

    public static void reset() {
        LEGACY.clear();
        MODERN.clear();
    }

    @Nullable
    static CETTexture resolve(ResourceLocation bound, CETSubject subject) {
        if (!(subject.entity() instanceof EntityHorse horse) || !bound.getPath().startsWith("horse/")) return null;
        String[] layers = horse.getVariantTexturePaths();
        if (layers == null || layers.length == 0 || layers[0] == null) return null;

        ResourceLocation base = new ResourceLocation(layers[0]);
        ResourceLocation lookup = "minecraft".equals(base.getNamespace()) ? base : new ResourceLocation(base.getPath());
        CETTexture variant = CETManager.getVariant(lookup, subject);
        boolean modern = CETLayout.usesModernModel();
        Map<CETTexture, CETTexture> byVariant = (modern ? MODERN : LEGACY).computeIfAbsent(bound, key -> new IdentityHashMap<>());
        CETTexture resolved = byVariant.get(variant);
        if (resolved == null) {
            resolved = variant.layered(id -> id.equals(lookup) ? bound : compose(id, layers, bound, modern));
            byVariant.put(variant, resolved);
        }
        return resolved;
    }

    private static ResourceLocation compose(ResourceLocation base, String[] layers, ResourceLocation bound, boolean modern) {
        List<BufferedImage> images = new ArrayList<>();
        int size = 0;
        for (int i = 0; i < layers.length; i++) {
            if (layers[i] == null) continue;
            BufferedImage image = CETLayout.read(i == 0 ? base : new ResourceLocation(layers[i]));
            if (image == null) {
                if (i == 0) return bound;
                continue;
            }
            images.add(image);
            size = Math.max(size, image.getWidth());
        }
        BufferedImage result = CETUtils.empty(size, size);
        Graphics graphics = result.getGraphics();
        for (BufferedImage image : images) graphics.drawImage(image, 0, 0, size, size, null);
        graphics.dispose();

        ResourceLocation location = new ResourceLocation("datarium", "cet/" + bound.getPath() + (modern ? "/modern/" : "/legacy/")
                + base.getNamespace() + "/" + base.getPath());
        CETUtils.register(result, location);
        return location;
    }
}
