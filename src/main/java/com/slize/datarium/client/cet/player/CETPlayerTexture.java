package com.slize.datarium.client.cet.player;

import com.slize.datarium.client.cet.CETConfig;
import com.slize.datarium.client.cet.CETManager;
import com.slize.datarium.client.cet.CETSubject;
import com.slize.datarium.client.cet.CETTexture;
import com.slize.datarium.client.cet.CETUtils;
import com.slize.datarium.mixin.accessors.AccessorSkinManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class CETPlayerTexture {
    private static final long RETRY_MILLIS = 2000L;

    public final ResourceLocation source;
    private final long retryAt;
    public boolean hasFeatures;
    public boolean hasEmissives;
    public boolean hasEnchant;
    public boolean hasVillagerNose;
    public boolean hasFatCoat;
    public boolean wasForcedSolid;
    public NoseType noseType = NoseType.NONE;
    public int coatStyle;
    public int coatLength = 1;
    @Nullable public CETTexture baseTexture;
    @Nullable public ResourceLocation coatIdentifier;
    @Nullable public ResourceLocation noseIdentifier;
    @Nullable private UUID uuid;

    public enum NoseType {
        NONE, VILLAGER, VILLAGER_TEXTURED, VILLAGER_REMOVE, VILLAGER_TEXTURED_REMOVE,
        TEXTURED_1, TEXTURED_2, TEXTURED_3, TEXTURED_4, TEXTURED_5
    }

    private CETPlayerTexture(ResourceLocation source, long retryAt) {
        this.source = source;
        this.retryAt = retryAt;
    }

    @Nullable
    public static CETPlayerTexture of(AbstractClientPlayer player) {
        if (!CETConfig.skinFeaturesEnabled) return null;
        ResourceLocation skin = player.getLocationSkin();
        UUID id = player.getUniqueID();
        CETPlayerTexture cached = CETManager.playerTextures().get(id);
        if (cached != null && cached.source.equals(skin) && (cached.retryAt == 0L || System.currentTimeMillis() < cached.retryAt)) {
            return cached.hasFeatures && cached.canUseFeaturesFor(player) ? cached : null;
        }
        CETPlayerTexture created = create(player, skin);
        CETManager.playerTextures().put(id, created);
        return created.hasFeatures && created.canUseFeaturesFor(player) ? created : null;
    }

    public boolean canUseFeaturesFor(AbstractClientPlayer player) {
        if (!hasFeatures) return false;
        if (CETConfig.enableEnemyTeamPlayersSkinFeatures) return true;
        return player.getTeam() == null || (Minecraft.getMinecraft().player != null && player.isOnSameTeam(Minecraft.getMinecraft().player));
    }

    @Nullable
    public ResourceLocation skinFor(CETSubject subject) {
        return baseTexture == null ? null : baseTexture.getTextureIdentifier(subject);
    }

    private static CETPlayerTexture create(AbstractClientPlayer player, ResourceLocation skin) {
        if (!skin.getPath().startsWith("skins/")) return new CETPlayerTexture(skin, 0L);
        String hash = skin.getPath().substring("skins/".length());
        File dir = ((AccessorSkinManager) Minecraft.getMinecraft().getSkinManager()).datarium$getSkinCacheDir();
        File file = new File(new File(dir, hash.length() > 2 ? hash.substring(0, 2) : "xx"), hash);
        if (!file.isFile()) return new CETPlayerTexture(skin, System.currentTimeMillis() + RETRY_MILLIS);
        BufferedImage original;
        try {
            original = CETUtils.toArgb(ImageIO.read(file));
        } catch (Exception e) {
            return new CETPlayerTexture(skin, System.currentTimeMillis() + RETRY_MILLIS);
        }
        CETPlayerTexture texture = new CETPlayerTexture(skin, 0L);
        if (original == null || original.getWidth() < 64 || original.getHeight() < 64) return texture;
        texture.uuid = player.getUniqueID();
        try {
            texture.process(original, player.getName());
        } catch (Exception e) {
            CETUtils.error("Player skin feature processing failed for " + player.getName() + ": " + e);
            texture.hasFeatures = false;
        }
        return texture;
    }

    private static int px(BufferedImage image, int x, int y) {
        return image.getRGB(x, y);
    }

    private static void set(BufferedImage image, int x, int y, int argb) {
        image.setRGB(x, y, argb);
    }

    private static boolean hasMarker(BufferedImage skin) {
        return px(skin, 1, 16) == -16776961
                && px(skin, 0, 16) == -16777089
                && px(skin, 0, 17) == -16776961
                && px(skin, 2, 16) == -16711936
                && px(skin, 3, 16) == -16744704
                && px(skin, 3, 17) == -16711936
                && px(skin, 0, 18) == -65536
                && px(skin, 0, 19) == -8454144
                && px(skin, 1, 19) == -65536
                && px(skin, 3, 18) == -1
                && px(skin, 2, 19) == -1;
    }

    public static int colourToNumber(int color) {
        return switch (color) {
            case -65281 -> 1;
            case -256 -> 2;
            case -16776961 -> 3;
            case -16711936 -> 4;
            case -16760705 -> 5;
            case -65536 -> 6;
            case -16744449 -> 7;
            case -14483457 -> 8;
            case -12362096 -> 666;
            default -> color;
        };
    }

    private static int[] bounds(String key) {
        return switch (key) {
            case "marker1" -> new int[]{56, 16, 63, 23};
            case "marker2" -> new int[]{56, 24, 63, 31};
            case "marker3" -> new int[]{56, 32, 63, 39};
            case "marker4" -> new int[]{56, 40, 63, 47};
            case "optimizedEyeSmall" -> new int[]{12, 16, 19, 16};
            case "optimizedEye2High" -> new int[]{12, 16, 19, 17};
            case "optimizedEye2High_second" -> new int[]{12, 18, 19, 19};
            case "optimizedEye4High" -> new int[]{12, 16, 19, 19};
            case "optimizedEye4High_second" -> new int[]{36, 16, 43, 19};
            case "face1" -> new int[]{0, 0, 7, 7};
            case "face2" -> new int[]{24, 0, 31, 7};
            case "face3" -> new int[]{32, 0, 39, 7};
            case "face4" -> new int[]{56, 0, 63, 7};
            case "cape1" -> new int[]{12, 32, 19, 35};
            case "cape2" -> new int[]{36, 32, 43, 35};
            case "cape3" -> new int[]{12, 48, 19, 51};
            case "cape4" -> new int[]{28, 48, 35, 51};
            case "cape5" -> new int[]{44, 48, 51, 51};
            default -> new int[]{0, 0, 0, 0};
        };
    }

    private static void copy(BufferedImage source, BufferedImage dest, int[] b, int toX, int toY) {
        copy(source, dest, b[0], b[1], b[2], b[3], toX, toY);
    }

    private static void copy(BufferedImage source, BufferedImage dest, int x1, int y1, int x2, int y2, int toX, int toY) {
        int dx = toX - x1;
        int dy = toY - y1;
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) {
                set(dest, x + dx, y + dy, px(source, x, y));
            }
        }
    }

    private static void delete(BufferedImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) set(image, x, y, 0);
        }
    }

    private static void stripAlpha(BufferedImage image, int x1, int y1, int x2, int y2) {
        for (int x = x1; x <= x2; x++) {
            for (int y = y1; y <= y2; y++) set(image, x, y, px(image, x, y) | -16777216);
        }
    }

    private static void forceSolidLowerSkin(BufferedImage skin) {
        stripAlpha(skin, 8, 0, 23, 15);
        stripAlpha(skin, 0, 20, 55, 31);
        stripAlpha(skin, 0, 8, 7, 15);
        stripAlpha(skin, 24, 8, 31, 15);
        stripAlpha(skin, 0, 16, 11, 19);
        stripAlpha(skin, 20, 16, 35, 19);
        stripAlpha(skin, 44, 16, 51, 19);
        stripAlpha(skin, 20, 48, 27, 51);
        stripAlpha(skin, 36, 48, 43, 51);
        stripAlpha(skin, 16, 52, 47, 63);
    }

    @Nullable
    private static BufferedImage matchPixels(BufferedImage base, int[] b, @Nullable BufferedImage second, boolean invert) {
        Set<Integer> colors = new HashSet<>();
        for (int x = b[0]; x <= b[2]; x++) {
            for (int y = b[1]; y <= b[3]; y++) {
                int color = px(base, x, y);
                if ((color >>> 24) != 0) colors.add(color);
            }
        }
        if (colors.isEmpty()) return null;
        BufferedImage texture = CETUtils.copy(second != null ? second : base);
        for (int x = 0; x < texture.getWidth(); x++) {
            for (int y = 0; y < texture.getHeight(); y++) {
                boolean contained = colors.contains(px(texture, x, y));
                if (invert == contained) set(texture, x, y, 0);
            }
        }
        return nullIfEmpty(texture);
    }

    @Nullable
    private static BufferedImage nullIfEmpty(BufferedImage image) {
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (px(image, x, y) != 0) return image;
            }
        }
        return null;
    }

    private static BufferedImage blinkFace(BufferedImage base, int[] eyeBounds, int eyeHeight, @Nullable int[] secondLayer) {
        BufferedImage texture = CETUtils.copy(base);
        copy(base, texture, eyeBounds, 8, 8 + (eyeHeight - 1));
        if (secondLayer != null) copy(base, texture, secondLayer, 40, 8 + (eyeHeight - 1));
        return texture;
    }

    private static BufferedImage coatTexture(BufferedImage skin, int length, boolean ignoreTop) {
        BufferedImage coat = CETUtils.empty(64, 64);
        if (!ignoreTop) {
            copy(skin, coat, 4, 32, 7, 35 + length, 20, 32);
            copy(skin, coat, 4, 48, 7, 51 + length, 24, 32);
        }
        copy(skin, coat, 0, 36, 7, 36 + length, 16, 36);
        copy(skin, coat, 12, 36, 15, 36 + length, 36, 36);
        copy(skin, coat, 4, 52, 15, 52 + length, 24, 36);
        return coat;
    }

    private ResourceLocation id(String suffix) {
        return new ResourceLocation(CETManager.SKIN_NAMESPACE, uuid + suffix + ".png");
    }

    private ResourceLocation register(BufferedImage image, String suffix) {
        ResourceLocation location = id(suffix);
        CETUtils.register(image, location);
        return location;
    }

    private void process(BufferedImage original, String playerName) {
        if (!hasMarker(original)) return;
        hasFeatures = true;
        CETUtils.log("Found Player {" + playerName + "} with ETF texture features in skin.");
        BufferedImage modified = CETUtils.copy(original);

        int[] choices = {
                colourToNumber(px(original, 52, 16)),
                colourToNumber(px(original, 52, 17)),
                colourToNumber(px(original, 52, 18)),
                colourToNumber(px(original, 52, 19)),
                colourToNumber(px(original, 53, 16)),
                colourToNumber(px(original, 53, 17)),
                colourToNumber(px(original, 53, 18))
        };
        if (choices[2] < 1 || choices[2] > 8) choices[2] = 1;

        boolean noseUpper = true;
        boolean noseLower = true;
        for (int x = 0; x < 2; x++) {
            for (int y = 13; y <= 15; y++) {
                noseUpper &= colourToNumber(px(original, 43 + x, y)) == 666;
                noseLower &= colourToNumber(px(original, 11 + x, y)) == 666;
            }
        }
        hasVillagerNose = noseLower || noseUpper;
        boolean removeNosePixels = noseUpper;
        if (noseUpper) delete(modified, 43, 13, 44, 15);

        BufferedImage noseTexture = null;
        int noseChoice = choices[5];
        if (noseChoice >= 1 && noseChoice <= 9) {
            if (noseChoice == 1 || noseChoice == 7 || noseChoice == 8 || noseChoice == 9) {
                hasVillagerNose = true;
                noseType = switch (noseChoice) {
                    case 7 -> NoseType.VILLAGER_TEXTURED;
                    case 8 -> NoseType.VILLAGER_REMOVE;
                    case 9 -> NoseType.VILLAGER_TEXTURED_REMOVE;
                    default -> NoseType.VILLAGER;
                };
                if (noseChoice > 7) {
                    removeNosePixels = true;
                    delete(modified, 43, 13, 44, 15);
                }
            } else {
                noseTexture = CETUtils.empty(8, 8);
                int[] b = switch (noseChoice) {
                    case 3 -> {
                        noseType = NoseType.TEXTURED_2;
                        yield bounds("cape2");
                    }
                    case 4 -> {
                        noseType = NoseType.TEXTURED_3;
                        yield bounds("cape3");
                    }
                    case 5 -> {
                        noseType = NoseType.TEXTURED_4;
                        yield bounds("cape4");
                    }
                    case 6 -> {
                        noseType = NoseType.TEXTURED_5;
                        yield bounds("cape5");
                    }
                    default -> {
                        noseType = NoseType.TEXTURED_1;
                        yield bounds("cape1");
                    }
                };
                int noseY = 0;
                for (int x = b[0]; x <= b[2]; x++) {
                    int noseX = 0;
                    for (int y = b[1]; y <= b[3]; y++) {
                        set(noseTexture, noseX, noseY, px(original, x, y));
                        noseX++;
                    }
                    noseY++;
                }
                for (int x = 4; x < 8; x++) {
                    for (int y = 0; y < 8; y++) set(noseTexture, x, y, px(noseTexture, 7 - x, y));
                }
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 4; y++) {
                        int lower = px(noseTexture, x, y + 4);
                        set(noseTexture, x, y + 4, px(noseTexture, x, y));
                        set(noseTexture, x, y, lower);
                    }
                }
            }
        }

        BufferedImage coatSkin = null;
        int coatChoice = choices[1];
        if (coatChoice >= 1 && coatChoice <= 8) {
            coatStyle = coatChoice;
            int length = choices[2] - 1;
            coatLength = length + 1;
            coatSkin = coatTexture(original, length, coatChoice >= 5);
            if (coatChoice == 2 || coatChoice == 4 || coatChoice == 6 || coatChoice == 8) {
                delete(modified, 4, 32, 7, 35);
                delete(modified, 4, 48, 7, 51);
                delete(modified, 0, 36, 15, 36 + length);
                delete(modified, 0, 52, 15, 52 + length);
            }
            hasFatCoat = coatChoice == 3 || coatChoice == 4 || coatChoice == 7 || coatChoice == 8;
        }

        wasForcedSolid = choices[6] == 1 || CETConfig.skinTransparencyMode == CETConfig.SkinTransparencyMode.VANILLA;
        if (wasForcedSolid) forceSolidLowerSkin(modified);

        BufferedImage blink = null;
        BufferedImage blink2 = null;
        int blinkChoice = choices[0];
        if (blinkChoice >= 1 && blinkChoice <= 5) {
            if (blinkChoice <= 2) {
                if (removeNosePixels) delete(modified, 35, 5, 36, 7);
                blink = blinkFace(modified, bounds("face1"), 1, bounds("face3"));
                if (blinkChoice == 2) {
                    if (removeNosePixels) delete(modified, 59, 5, 60, 7);
                    blink2 = blinkFace(modified, bounds("face2"), 1, bounds("face4"));
                }
            } else {
                int eyeHeight = choices[3];
                if (eyeHeight > 8 || eyeHeight < 1) eyeHeight = 1;
                if (blinkChoice == 3) {
                    blink = blinkFace(modified, bounds("optimizedEyeSmall"), eyeHeight, null);
                } else if (blinkChoice == 4) {
                    blink = blinkFace(modified, bounds("optimizedEye2High"), eyeHeight, null);
                    blink2 = blinkFace(modified, bounds("optimizedEye2High_second"), eyeHeight, null);
                } else {
                    blink = blinkFace(modified, bounds("optimizedEye4High"), eyeHeight, null);
                    blink2 = blinkFace(modified, bounds("optimizedEye4High_second"), eyeHeight, null);
                }
            }
        }

        List<Integer> markerChoices = Arrays.asList(
                colourToNumber(px(original, 1, 17)),
                colourToNumber(px(original, 1, 18)),
                colourToNumber(px(original, 2, 17)),
                colourToNumber(px(original, 2, 18)));

        ResourceLocation emissiveId = null;
        ResourceLocation blinkEmissiveId = null;
        ResourceLocation blink2EmissiveId = null;
        ResourceLocation coatEmissiveId = null;
        ResourceLocation noseEmissiveId = null;
        hasEmissives = markerChoices.contains(1);
        if (hasEmissives) {
            int[] b = bounds("marker" + (markerChoices.indexOf(1) + 1));
            BufferedImage emissive = matchPixels(modified, b, null, false);
            if (emissive != null) {
                emissiveId = register(emissive, "_e");
                if (blink != null) {
                    BufferedImage image = matchPixels(blink, b, null, false);
                    if (image != null) blinkEmissiveId = register(image, "_blink_e");
                }
                if (blink2 != null) {
                    BufferedImage image = matchPixels(blink2, b, null, false);
                    if (image != null) blink2EmissiveId = register(image, "_blink2_e");
                }
                if (coatSkin != null) {
                    BufferedImage image = matchPixels(modified, b, coatSkin, false);
                    if (image != null) coatEmissiveId = register(image, "_coat_e");
                }
                if (noseTexture != null) {
                    BufferedImage image = matchPixels(modified, b, noseTexture, false);
                    if (image != null) noseEmissiveId = register(image, "_nose_e");
                }
            } else {
                hasEmissives = false;
            }
        }

        ResourceLocation enchantId = null;
        ResourceLocation blinkEnchantId = null;
        ResourceLocation blink2EnchantId = null;
        ResourceLocation coatEnchantId = null;
        ResourceLocation noseEnchantId = null;
        hasEnchant = markerChoices.contains(2);
        if (hasEnchant) {
            int[] b = bounds("marker" + (markerChoices.indexOf(2) + 1));
            BufferedImage enchant = matchPixels(modified, b, null, true);
            if (enchant != null) {
                enchantId = register(enchant, "_enchant");
                if (blink != null) {
                    BufferedImage image = matchPixels(blink, b, null, true);
                    if (image != null) blinkEnchantId = register(image, "_blink_enchant");
                }
                if (blink2 != null) {
                    BufferedImage image = matchPixels(blink2, b, null, true);
                    if (image != null) blink2EnchantId = register(image, "_blink2_enchant");
                }
                if (coatSkin != null) {
                    BufferedImage image = matchPixels(modified, b, coatSkin, true);
                    if (image != null) coatEnchantId = register(image, "_coat_enchant");
                }
                if (noseTexture != null) {
                    BufferedImage image = matchPixels(modified, b, noseTexture, true);
                    if (image != null) noseEnchantId = register(image, "_nose_enchant");
                }
            } else {
                hasEnchant = false;
            }
        }

        if (coatSkin != null) {
            coatIdentifier = register(coatSkin, "_coat");
            CETTexture.manual(coatIdentifier, coatEmissiveId, coatEnchantId);
        }
        if (noseTexture != null) {
            noseIdentifier = register(noseTexture, "_nose");
            CETTexture.manual(noseIdentifier, noseEmissiveId, noseEnchantId);
        }

        ResourceLocation blinkId = blink != null ? register(blink, "_blink") : null;
        ResourceLocation blink2Id = blink2 != null ? register(blink2, "_blink2") : null;
        ResourceLocation skinId = register(modified, "");
        baseTexture = CETTexture.manual(skinId, blinkId, blink2Id,
                emissiveId, blinkEmissiveId, blink2EmissiveId,
                enchantId, blinkEnchantId, blink2EnchantId);
    }
}
