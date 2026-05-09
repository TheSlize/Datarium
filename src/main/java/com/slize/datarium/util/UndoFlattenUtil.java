package com.slize.datarium.util;

import javax.annotation.Nullable;

public class UndoFlattenUtil {

    public record FlattenedItem(String itemId, Integer damage) {
    }

    private static final String[] COLORS = {
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
    };

    private static final String[] WOOD_TYPES = {
            "oak", "spruce", "birch", "jungle", "acacia", "dark_oak"
    };

    @Nullable
    public static FlattenedItem getUnflattenedItem(String modernId) {
        if (!modernId.startsWith("minecraft:")) return null;
        String name = modernId.substring("minecraft:".length());

        switch (name) {
            case "terracotta" -> {
                return new FlattenedItem("minecraft:hardened_clay", null);
            }
            case "ink_sac" -> {
                return new FlattenedItem("minecraft:dye", 0);
            }
            case "rose_red" -> {
                return new FlattenedItem("minecraft:dye", 1);
            }
            case "cactus_green" -> {
                return new FlattenedItem("minecraft:dye", 2);
            }
            case "cocoa_beans" -> {
                return new FlattenedItem("minecraft:dye", 3);
            }
            case "lapis_lazuli" -> {
                return new FlattenedItem("minecraft:dye", 4);
            }
            case "dandelion_yellow" -> {
                return new FlattenedItem("minecraft:dye", 11);
            }
            case "bone_meal" -> {
                return new FlattenedItem("minecraft:dye", 15);
            }
        }

        for (int i = 0; i < COLORS.length; i++) {
            String color = COLORS[i];

            if (name.equals(color + "_wool")) return new FlattenedItem("minecraft:wool", i);
            if (name.equals(color + "_carpet")) return new FlattenedItem("minecraft:carpet", i);
            if (name.equals(color + "_stained_glass")) return new FlattenedItem("minecraft:stained_glass", i);
            if (name.equals(color + "_stained_glass_pane")) return new FlattenedItem("minecraft:stained_glass_pane", i);
            if (name.equals(color + "_concrete")) return new FlattenedItem("minecraft:concrete", i);
            if (name.equals(color + "_concrete_powder")) return new FlattenedItem("minecraft:concrete_powder", i);
            if (name.equals(color + "_terracotta")) return new FlattenedItem("minecraft:stained_hardened_clay", i);
            if (name.equals(color + "_bed")) return new FlattenedItem("minecraft:bed", i);
            if (name.equals(color + "_dye")) return new FlattenedItem("minecraft:dye", 15 - i);
        }

        for (int i = 0; i < WOOD_TYPES.length; i++) {
            String wood = WOOD_TYPES[i];
            String logItem = (i >= 4) ? "minecraft:log2" : "minecraft:log";
            String leafItem = (i >= 4) ? "minecraft:leaves2" : "minecraft:leaves";
            int logMeta = (i >= 4) ? i - 4 : i;

            if (name.equals(wood + "_log")) return new FlattenedItem(logItem, logMeta);
            if (name.equals(wood + "_leaves")) return new FlattenedItem(leafItem, logMeta);
            if (name.equals(wood + "_planks")) return new FlattenedItem("minecraft:planks", i);
            if (name.equals(wood + "_sapling")) return new FlattenedItem("minecraft:sapling", i);
            if (name.equals(wood + "_slab")) return new FlattenedItem("minecraft:wooden_slab", i);
        }

        return null;
    }
}
