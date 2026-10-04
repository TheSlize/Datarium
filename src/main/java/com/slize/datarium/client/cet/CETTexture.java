package com.slize.datarium.client.cet;

import com.slize.datarium.util.PackConverter;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Properties;
import java.util.function.UnaryOperator;

public final class CETTexture {
    public final ResourceLocation thisIdentifier;
    private State currentState = State.NORMAL;

    @Nullable private ResourceLocation emissiveIdentifier;
    @Nullable private ResourceLocation emissiveBlinkIdentifier;
    @Nullable private ResourceLocation emissiveBlink2Identifier;
    @Nullable private ResourceLocation enchantIdentifier;
    @Nullable private ResourceLocation enchantBlinkIdentifier;
    @Nullable private ResourceLocation enchantBlink2Identifier;
    @Nullable private ResourceLocation blinkIdentifier;
    @Nullable private ResourceLocation blink2Identifier;
    private int blinkLength = CETConfig.blinkLength;
    private int blinkFrequency = CETConfig.blinkFrequency;

    public CETTexture(ResourceLocation identifier) {
        this.thisIdentifier = identifier;
        if (identifier.getPath().endsWith(".png")) {
            String basePack = CETUtils.packOf(identifier);
            if (basePack != null) {
                List<ResourceLocation> modern = PackConverter.modernTextures(identifier);
                setupBlinking(identifier, basePack);
                for (int i = 0; blinkIdentifier == null && i < modern.size(); i++) setupBlinking(modern.get(i), basePack);
                setupEmissives(identifier, basePack);
                for (int i = 0; emissiveIdentifier == null && i < modern.size(); i++) setupEmissives(modern.get(i), basePack);
                setupEnchants(identifier, basePack);
                for (int i = 0; enchantIdentifier == null && i < modern.size(); i++) setupEnchants(modern.get(i), basePack);
            }
        }
    }

    CETTexture layered(UnaryOperator<ResourceLocation> composer) {
        CETTexture texture = new CETTexture(composer.apply(thisIdentifier),
                blinkIdentifier == null ? null : composer.apply(blinkIdentifier),
                blink2Identifier == null ? null : composer.apply(blink2Identifier),
                emissiveIdentifier, emissiveBlinkIdentifier, emissiveBlink2Identifier,
                enchantIdentifier, enchantBlinkIdentifier, enchantBlink2Identifier);
        texture.blinkLength = blinkLength;
        texture.blinkFrequency = blinkFrequency;
        return texture;
    }

    private CETTexture(ResourceLocation identifier,
                       @Nullable ResourceLocation blink, @Nullable ResourceLocation blink2,
                       @Nullable ResourceLocation emissive, @Nullable ResourceLocation emissiveBlink, @Nullable ResourceLocation emissiveBlink2,
                       @Nullable ResourceLocation enchant, @Nullable ResourceLocation enchantBlink, @Nullable ResourceLocation enchantBlink2) {
        this.thisIdentifier = identifier;
        this.blinkIdentifier = blink;
        this.blink2Identifier = blink2;
        this.emissiveIdentifier = emissive;
        this.emissiveBlinkIdentifier = emissiveBlink;
        this.emissiveBlink2Identifier = emissiveBlink2;
        this.enchantIdentifier = enchant;
        this.enchantBlinkIdentifier = enchantBlink;
        this.enchantBlink2Identifier = enchantBlink2;
    }

    public static CETTexture manual(ResourceLocation identifier,
                                    @Nullable ResourceLocation blink, @Nullable ResourceLocation blink2,
                                    @Nullable ResourceLocation emissive, @Nullable ResourceLocation emissiveBlink, @Nullable ResourceLocation emissiveBlink2,
                                    @Nullable ResourceLocation enchant, @Nullable ResourceLocation enchantBlink, @Nullable ResourceLocation enchantBlink2) {
        CETTexture texture = new CETTexture(identifier, blink, blink2, emissive, emissiveBlink, emissiveBlink2, enchant, enchantBlink, enchantBlink2);
        CETManager.putTexture(identifier, texture);
        if (blink != null) CETManager.putTexture(blink, texture);
        if (blink2 != null) CETManager.putTexture(blink2, texture);
        return texture;
    }

    public static CETTexture manual(ResourceLocation identifier, @Nullable ResourceLocation emissive, @Nullable ResourceLocation enchant) {
        return manual(identifier, null, null, emissive, null, null, enchant, null, null);
    }

    private void setupBlinking(ResourceLocation source, String basePack) {
        if (!CETConfig.enableBlinking) return;
        ResourceLocation blink = CETUtils.replace(source, "\\.png$", "_blink.png");
        String blinkPack = CETUtils.packOf(blink);
        if (blinkPack == null || !CETUtils.isFromSameOrHigherPack(blinkPack, basePack)) return;
        blinkIdentifier = blink;

        ResourceLocation blink2 = CETUtils.replace(source, "\\.png$", "_blink2.png");
        if (blinkPack.equals(CETUtils.packOf(blink2))) blink2Identifier = blink2;

        ResourceLocation propertiesId = CETUtils.replace(blink, "\\.png$", ".properties");
        Properties properties = CETUtils.readProperties(propertiesId);
        if (properties == null || !CETUtils.isFromSameOrHigherPack(CETUtils.packOf(propertiesId), blinkPack)) return;
        try {
            if (properties.containsKey("blinkLength")) {
                blinkLength = Integer.parseInt(properties.getProperty("blinkLength").replaceAll("\\D", ""));
            }
            if (properties.containsKey("blinkFrequency")) {
                blinkFrequency = Integer.parseInt(properties.getProperty("blinkFrequency").replaceAll("\\D", ""));
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void setupEmissives(ResourceLocation source, String basePack) {
        for (String suffix : CETManager.emissiveSuffixes()) {
            ResourceLocation emissive = CETUtils.replace(source, "\\.png$", suffix + ".png");
            String emissivePack = CETUtils.packOf(emissive);
            if (emissivePack == null || !CETUtils.isFromSameOrHigherPack(emissivePack, basePack)) continue;
            emissiveIdentifier = emissive;
            ResourceLocation blink = CETUtils.replace(source, "\\.png$", "_blink" + suffix + ".png");
            if (emissivePack.equals(CETUtils.packOf(blink))) {
                emissiveBlinkIdentifier = blink;
                ResourceLocation blink2 = CETUtils.replace(source, "\\.png$", "_blink2" + suffix + ".png");
                if (emissivePack.equals(CETUtils.packOf(blink2))) emissiveBlink2Identifier = blink2;
            }
            break;
        }
    }

    private void setupEnchants(ResourceLocation source, String basePack) {
        if (!CETConfig.enableEnchantedTextures) return;
        ResourceLocation enchant = CETUtils.replace(source, "\\.png$", "_enchant.png");
        if (!CETUtils.isFromSameOrHigherPack(CETUtils.packOf(enchant), basePack)) return;
        enchantIdentifier = enchant;
        ResourceLocation blink = CETUtils.replace(source, "\\.png$", "_blink_enchant.png");
        if (!CETUtils.isFromSameOrHigherPack(CETUtils.packOf(blink), basePack)) return;
        enchantBlinkIdentifier = blink;
        ResourceLocation blink2 = CETUtils.replace(source, "\\.png$", "_blink2_enchant.png");
        if (CETUtils.isFromSameOrHigherPack(CETUtils.packOf(blink2), basePack)) enchantBlink2Identifier = blink2;
    }

    public ResourceLocation getTextureIdentifier(@Nullable CETSubject subject) {
        currentState = State.NORMAL;
        if (blinkIdentifier == null || subject == null || !(subject.entity() instanceof EntityLivingBase living)) {
            return identifierOfCurrentState();
        }
        if (living.isPlayerSleeping()) {
            currentState = State.BLINK;
        } else if (living.isPotionActive(MobEffects.BLINDNESS)) {
            currentState = blink2Identifier != null ? State.BLINK2 : State.BLINK;
        } else {
            setBlink(living.ticksExisted, Math.abs(subject.uuid().hashCode()));
        }
        return identifierOfCurrentState();
    }

    private void setBlink(int currentTime, int hash) {
        int uuidHash = hash % (blinkFrequency * 2) + 20 + blinkFrequency;
        int timeModulated = Math.abs(currentTime % uuidHash);
        if (timeModulated <= blinkLength + blinkLength) {
            if (blink2Identifier != null) {
                if (timeModulated >= (blinkLength / 1.5) && timeModulated <= blinkLength + 1 + (blinkLength / 3)) {
                    currentState = State.BLINK;
                } else {
                    currentState = State.BLINK2;
                }
            } else {
                currentState = State.BLINK;
            }
        }
    }

    private ResourceLocation identifierOfCurrentState() {
        return CETLayout.adapt(switch (currentState) {
            case BLINK -> blinkIdentifier;
            case BLINK2 -> blink2Identifier;
            default -> thisIdentifier;
        }, false);
    }

    @Nullable
    public ResourceLocation getEmissiveIdentifierOfCurrentState() {
        return CETLayout.adapt(switch (currentState) {
            case BLINK -> emissiveBlinkIdentifier;
            case BLINK2 -> emissiveBlink2Identifier;
            default -> emissiveIdentifier;
        }, true);
    }

    @Nullable
    public ResourceLocation getEnchantIdentifierOfCurrentState() {
        return CETLayout.adapt(switch (currentState) {
            case BLINK -> enchantBlinkIdentifier;
            case BLINK2 -> enchantBlink2Identifier;
            default -> enchantIdentifier;
        }, true);
    }

    public boolean isEmissive() {
        return emissiveIdentifier != null;
    }

    public boolean isEnchanted() {
        return enchantIdentifier != null;
    }

    public boolean hasOverlays() {
        return emissiveIdentifier != null || enchantIdentifier != null;
    }

    public boolean doesBlink() {
        return blinkIdentifier != null;
    }

    @Override
    public String toString() {
        return "[" + thisIdentifier + ", emissive=" + isEmissive() + ", blinks=" + doesBlink() + "]";
    }

    private enum State {
        NORMAL, BLINK, BLINK2
    }
}
