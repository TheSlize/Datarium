package com.slize.datarium.client.cit;

import net.minecraft.init.Items;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class CITPotions {
    private static final int MASK_SPLASH = 16384;
    private static final Map<String, Integer> POTION_DAMAGES = new HashMap<>();
    private static final Map<String, int[]> IMAGE_DAMAGES = new HashMap<>();

    static {
        addPotion("water", 0, false);
        addPotion("awkward", 16, false);
        addPotion("thick", 32, false);
        addPotion("mundane", 64, false);
        addPotion("regeneration", 1, true);
        addPotion("swiftness", 2, true);
        addPotion("fire_resistance", 3, true);
        addPotion("poison", 4, true);
        addPotion("healing", 5, true);
        addPotion("night_vision", 6, true);
        addPotion("weakness", 8, true);
        addPotion("strength", 9, true);
        addPotion("slowness", 10, true);
        addPotion("leaping", 11, true);
        addPotion("harming", 12, true);
        addPotion("water_breathing", 13, true);
        addPotion("invisibility", 14, true);

        IMAGE_DAMAGES.put("water", single(0, 0));
        IMAGE_DAMAGES.put("awkward", single(0, 1));
        IMAGE_DAMAGES.put("thick", single(0, 2));
        IMAGE_DAMAGES.put("potent", single(0, 3));
        IMAGE_DAMAGES.put("regeneration", all(1));
        IMAGE_DAMAGES.put("movespeed", all(2));
        IMAGE_DAMAGES.put("fireresistance", all(3));
        IMAGE_DAMAGES.put("poison", all(4));
        IMAGE_DAMAGES.put("heal", all(5));
        IMAGE_DAMAGES.put("nightvision", all(6));
        IMAGE_DAMAGES.put("clear", single(7, 0));
        IMAGE_DAMAGES.put("bungling", single(7, 1));
        IMAGE_DAMAGES.put("charming", single(7, 2));
        IMAGE_DAMAGES.put("rank", single(7, 3));
        IMAGE_DAMAGES.put("weakness", all(8));
        IMAGE_DAMAGES.put("damageboost", all(9));
        IMAGE_DAMAGES.put("moveslowdown", all(10));
        IMAGE_DAMAGES.put("leaping", all(11));
        IMAGE_DAMAGES.put("harm", all(12));
        IMAGE_DAMAGES.put("waterbreathing", all(13));
        IMAGE_DAMAGES.put("invisibility", all(14));
        IMAGE_DAMAGES.put("thin", single(15, 0));
        IMAGE_DAMAGES.put("debonair", single(15, 1));
        IMAGE_DAMAGES.put("sparkling", single(15, 2));
        IMAGE_DAMAGES.put("stinky", single(15, 3));
        IMAGE_DAMAGES.put("mundane", single(0, 4));
        IMAGE_DAMAGES.put("speed", IMAGE_DAMAGES.get("movespeed"));
        IMAGE_DAMAGES.put("fire_resistance", IMAGE_DAMAGES.get("fireresistance"));
        IMAGE_DAMAGES.put("instant_health", IMAGE_DAMAGES.get("heal"));
        IMAGE_DAMAGES.put("night_vision", IMAGE_DAMAGES.get("nightvision"));
        IMAGE_DAMAGES.put("strength", IMAGE_DAMAGES.get("damageboost"));
        IMAGE_DAMAGES.put("slowness", IMAGE_DAMAGES.get("moveslowdown"));
        IMAGE_DAMAGES.put("instant_damage", IMAGE_DAMAGES.get("harm"));
        IMAGE_DAMAGES.put("water_breathing", IMAGE_DAMAGES.get("waterbreathing"));
    }

    private CITPotions() {
    }

    private static void addPotion(String name, int value, boolean extended) {
        if (extended) value |= 8192;
        POTION_DAMAGES.put("minecraft:" + name, value);
        if (extended) {
            POTION_DAMAGES.put("minecraft:strong_" + name, value | 32);
            POTION_DAMAGES.put("minecraft:long_" + name, value | 64);
        }
    }

    private static int[] all(int base) {
        return new int[]{base, base + 16, base + 32, base + 48};
    }

    private static int[] single(int base, int sub) {
        return new int[]{base + sub * 16};
    }

    public static int getItemDamage(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemPotion)) return stack.getItemDamage();
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return 0;
        String name = tag.getString("Potion");
        if (name.isEmpty()) return 0;
        if (name.indexOf(':') < 0) name = "minecraft:" + name;
        Integer value = POTION_DAMAGES.get(name);
        if (value == null) return -1;
        return stack.getItem() == Items.SPLASH_POTION ? value | MASK_SPLASH : value;
    }

    @Nullable
    public static Properties makeImageProperties(String type, String name) {
        if (name.endsWith("_n") || name.endsWith("_s")) return null;
        Properties props = new Properties();
        props.setProperty("type", "item");
        if (name.equals("empty") && type.equals("normal")) {
            props.setProperty("items", "minecraft:glass_bottle");
            return props;
        }
        int[] damages = IMAGE_DAMAGES.get(name);
        if (damages == null) return null;
        boolean splash = type.equals("splash");
        String item = switch (type) {
            case "splash" -> "minecraft:splash_potion";
            case "linger" -> "minecraft:lingering_potion";
            default -> "minecraft:potion";
        };
        StringBuilder damage = new StringBuilder();
        for (int d : damages) {
            if (!damage.isEmpty()) damage.append(' ');
            damage.append(splash ? d | MASK_SPLASH : d);
        }
        int mask = 16447;
        if (name.equals("water") || name.equals("mundane")) mask |= 64;
        props.setProperty("items", item);
        props.setProperty("damage", damage.toString());
        props.setProperty("damageMask", Integer.toString(mask));
        props.setProperty(splash ? "texture.potion_bottle_splash" : "texture.potion_bottle_drinkable", name);
        return props;
    }
}
