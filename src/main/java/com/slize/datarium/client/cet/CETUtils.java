package com.slize.datarium.client.cet;

import com.google.common.collect.MapMaker;
import com.slize.datarium.DatariumMain;
import com.slize.datarium.util.PackConverter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public final class CETUtils {

    private static List<String> packOrder;
    private static final Map<Properties, Integer> PROPERTIES_FORMATS = new MapMaker().weakKeys().makeMap();

    private CETUtils() {}

    public static void resetPackOrder() {
        packOrder = null;
    }

    public static int formatOf(Properties properties) {
        Integer format = PROPERTIES_FORMATS.get(properties);
        return format == null ? PackConverter.LEGACY : format;
    }

    private static List<String> packOrder() {
        List<String> order = packOrder;
        if (order == null) {
            order = new ArrayList<>();
            for (ResourcePackRepository.Entry entry : Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries()) {
                order.add(entry.getResourcePackName());
            }
            packOrder = order;
        }
        return order;
    }

    public static int packIndex(@Nullable String pack) {
        return pack == null ? Integer.MIN_VALUE : packOrder().indexOf(pack);
    }

    @Nullable
    public static String highestPackOfTwo(@Nullable String pack1, @Nullable String pack2) {
        if (pack1 == null) return null;
        if (pack1.equals(pack2) || pack2 == null) return pack1;
        return packIndex(pack1) >= packIndex(pack2) ? pack1 : pack2;
    }

    @Nullable
    public static String highestPackOf(List<String> packs) {
        if (packs.isEmpty()) return null;
        String best = packs.get(0);
        for (int i = 1; i < packs.size(); i++) best = highestPackOfTwo(best, packs.get(i));
        return best;
    }

    public static boolean isFromSameOrHigherPack(@Nullable String candidate, @Nullable String other) {
        return candidate != null && candidate.equals(highestPackOfTwo(candidate, other));
    }

    @Nullable
    public static String packOf(@Nullable ResourceLocation location) {
        if (location == null) return null;
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
            return resource.getResourcePackName();
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean exists(@Nullable ResourceLocation location) {
        return packOf(location) != null;
    }

    @Nullable
    public static Properties readProperties(@Nullable ResourceLocation location) {
        if (location == null) return null;
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
             InputStream in = resource.getInputStream()) {
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            PROPERTIES_FORMATS.put(properties, PackConverter.formatOf(resource.getResourcePackName()));
            return properties;
        } catch (Exception e) {
            return null;
        }
    }

    public static List<Properties> readAllLayeredProperties(ResourceLocation location) {
        List<Properties> out = new ArrayList<>();
        try {
            for (IResource resource : Minecraft.getMinecraft().getResourceManager().getAllResources(location)) {
                try (IResource r = resource; InputStream in = r.getInputStream()) {
                    Properties properties = new Properties();
                    properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                    if (!properties.isEmpty()) out.add(properties);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Nullable
    public static BufferedImage readImage(@Nullable ResourceLocation location) {
        if (location == null) return null;
        try (IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location);
             InputStream in = resource.getInputStream()) {
            return toArgb(ImageIO.read(in));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static BufferedImage readLowestImage(@Nullable ResourceLocation location) {
        if (location == null) return null;
        BufferedImage image = null;
        try {
            List<IResource> resources = Minecraft.getMinecraft().getResourceManager().getAllResources(location);
            for (int i = 0; i < resources.size(); i++) {
                try (IResource resource = resources.get(i); InputStream in = resource.getInputStream()) {
                    if (i == 0) image = toArgb(ImageIO.read(in));
                }
            }
        } catch (Exception ignored) {
        }
        return image;
    }

    @Nullable
    public static BufferedImage toArgb(@Nullable BufferedImage image) {
        if (image == null || image.getType() == BufferedImage.TYPE_INT_ARGB) return image;
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(image, 0, 0, null);
        return out;
    }

    public static BufferedImage copy(BufferedImage image) {
        BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, image.getWidth(), image.getHeight(), image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()), 0, image.getWidth());
        return out;
    }

    public static BufferedImage empty(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public static void register(BufferedImage image, ResourceLocation location) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.getTextureManager().deleteTexture(location);
        mc.getTextureManager().loadTexture(location, new DynamicTexture(image));
    }

    @Nullable
    public static ResourceLocation replace(@Nullable ResourceLocation location, String regex, String replacement) {
        if (location == null) return null;
        try {
            return new ResourceLocation(location.getNamespace(), location.getPath().replaceFirst(regex, replacement));
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    public static ResourceLocation addVariantNumberSuffix(ResourceLocation location, int variant) {
        ResourceLocation changed = new ResourceLocation(addVariantNumberSuffix(location.toString(), variant));
        return location.equals(changed) ? null : changed;
    }

    public static String addVariantNumberSuffix(String location, int variant) {
        if (variant < 2) return location;
        String file = location.endsWith(".png") ? "png" : location.substring(location.lastIndexOf('.') + 1);
        if (location.matches("\\D+\\d+\\." + file)) {
            return location.replace("." + file, "." + variant + "." + file);
        }
        return location.replace("." + file, variant + "." + file);
    }

    public static int optifineHashing(int x) {
        x ^= 0x3D ^ x >> 16;
        x += x << 3;
        x ^= x >> 4;
        x *= 668265261;
        x ^= x >> 15;
        return x;
    }

    public static void log(String message) {
        DatariumMain.LOGGER.info("[CET] {}", message);
    }

    public static void warn(String message) {
        DatariumMain.LOGGER.warn("[CET] {}", message);
    }

    public static void error(String message) {
        DatariumMain.LOGGER.error("[CET] {}", message);
    }
}
