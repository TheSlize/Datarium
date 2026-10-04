package com.slize.datarium.client.cet;

import com.slize.datarium.client.cem.CEMManager;
import com.slize.datarium.util.PackConverter;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CETLayout {
    private static final String CHEST = "entity/chest/";
    private static final String HORSE = "entity/horse/";
    private static final Pattern HORSE_STEM = Pattern.compile("^(.+?)\\d*(?:_blink2?)?$");
    private static final ResourceLocation UNCHANGED = new ResourceLocation("datarium", "cet/layout/unchanged");
    private static final Map<ResourceLocation, ResourceLocation> ADAPTED = new HashMap<>();

    private CETLayout() {}

    public static void reset() {
        ADAPTED.clear();
    }

    @Nullable
    public static ResourceLocation adapt(@Nullable ResourceLocation id, boolean overlay) {
        if (id == null) return null;
        ResourceLocation adapted = ADAPTED.get(id);
        if (adapted == null) {
            adapted = UNCHANGED;
            BufferedImage image = convert(id, overlay);
            if (image != null) {
                adapted = new ResourceLocation("datarium", "cet/layout/" + id.getNamespace() + "/" + id.getPath());
                CETUtils.register(image, adapted);
            }
            ADAPTED.put(id, adapted);
        }
        return adapted == UNCHANGED || usesModernModel() ? id : adapted;
    }

    @Nullable
    static BufferedImage read(ResourceLocation id) {
        BufferedImage converted = usesModernModel() ? null : convert(id, false);
        return converted != null ? converted : CETUtils.readImage(id);
    }

    static boolean usesModernModel() {
        CETSubject subject = CETState.subject();
        if (subject == null) return false;
        TileEntity tile = subject.tile();
        if (tile != null) return CEMManager.getModelNameForTile(tile) != null;
        Entity entity = subject.entity();
        return entity != null && CEMManager.getModelNameForEntity(entity) != null;
    }

    @Nullable
    private static BufferedImage convert(ResourceLocation id, boolean overlay) {
        String path = id.getPath();
        if (!path.endsWith(".png")) return null;
        boolean chest = path.contains(CHEST);
        if (!chest && !path.contains(HORSE)) return null;
        String pack = CETUtils.packOf(id);
        if (pack == null) return null;
        int format = PackConverter.formatOf(pack);

        if (chest) {
            ResourceLocation right = rightHalf(id);
            if (right != null) {
                BufferedImage left = CETUtils.readImage(id);
                if (left == null) return null;
                BufferedImage other = CETUtils.readImage(right);
                return CETLayoutConverter.doubleChest(left, other == null ? left : other);
            }
            if (format < PackConverter.SPLIT_CHESTS) return null;
            BufferedImage image = CETUtils.readImage(id);
            return image == null || image.getWidth() != image.getHeight() ? null : CETLayoutConverter.chest(image);
        }

        if (format < PackConverter.FLATTENING) return null;
        BufferedImage image = CETUtils.readImage(id);
        if (image == null) return null;
        return CETLayoutConverter.horse(image, overlay ? null : CETUtils.readLowestImage(horseCanvas(path)));
    }

    @Nullable
    private static ResourceLocation rightHalf(ResourceLocation left) {
        String path = left.getPath();
        int name = path.lastIndexOf('/') + 1;
        int index = path.indexOf("_left", name);
        if (index < 0) return null;
        char next = path.charAt(index + 5);
        if (next != '.' && next != '_' && !Character.isDigit(next)) return null;
        return new ResourceLocation(left.getNamespace(), path.substring(0, index) + "_right" + path.substring(index + 5));
    }

    private static ResourceLocation horseCanvas(String path) {
        String name = path.substring(path.indexOf(HORSE) + HORSE.length(), path.length() - 4);
        Matcher matcher = HORSE_STEM.matcher(name);
        return new ResourceLocation("textures/" + HORSE + (matcher.matches() ? matcher.group(1) : name) + ".png");
    }
}
