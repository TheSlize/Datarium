package com.slize.datarium.client.cet;

import com.slize.datarium.Reference;
import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// Th3_Sl1ze: For those asking. Yes, the current configs are meant to be ugly. That's not my top priority thing to do
@Config(modid = Reference.MOD_ID, name = Reference.MOD_ID + "/custom_entity_textures", category = "cet")
public final class CETConfig {

    @Config.Comment("Enable random/custom entity textures (OptiFine & ETF format)")
    public static boolean enableCustomTextures = true;

    @Config.Comment("Allow block entities (chests, beds, shulker boxes, banners...) to use random/custom textures")
    public static boolean enableCustomBlockEntities = true;

    @Config.Comment("How often textures chosen by updatable properties (name, health, items...) are re-evaluated")
    public static UpdateFrequency textureUpdateFrequency = UpdateFrequency.Fast;

    @Config.Comment("Enable emissive textures (texture_e.png)")
    public static boolean enableEmissiveTextures = true;

    @Config.Comment("Enable enchanted textures (texture_enchant.png)")
    public static boolean enableEnchantedTextures = true;

    @Config.Comment("Allow block entities to use emissive textures")
    public static boolean enableEmissiveBlockEntities = true;

    @Config.Comment("DULL: OptiFine-like emissives with directional shading. BRIGHT: unshaded, brighter emissives")
    public static EmissiveRenderMode emissiveRenderMode = EmissiveRenderMode.DULL;

    @Config.Comment("Always check the default '_e' emissive suffix even when a pack declares a different one")
    public static boolean alwaysCheckVanillaEmissiveSuffix = true;

    @Config.Comment("Allow armor textures to use emissive and enchanted textures")
    public static boolean enableArmorAndTrims = true;

    @Config.Comment("Enable blinking textures (texture_blink.png / texture_blink2.png)")
    public static boolean enableBlinking = true;

    @Config.Comment("Default average ticks between blinks")
    @Config.RangeInt(min = 1, max = 1024)
    public static int blinkFrequency = 150;

    @Config.Comment("Default length of a blink in ticks")
    @Config.RangeInt(min = 1, max = 20)
    public static int blinkLength = 1;

    @Config.Comment("Enable ETF player skin features (emissive/enchanted pixels, blinking, jacket extensions, villager nose, transparency)")
    public static boolean skinFeaturesEnabled = true;

    @Config.Comment("Which skins may render with transparency")
    public static SkinTransparencyMode skinTransparencyMode = SkinTransparencyMode.ETF_SKINS_ONLY;

    @Config.Comment("Allow skin features for players on an enemy scoreboard team")
    public static boolean enableEnemyTeamPlayersSkinFeatures = true;

    @Config.Comment("OptiFine compat: allow gaps in random texture numbering when no .properties file exists")
    public static boolean optifineAllowWeirdSkipsInTrueRandom = true;

    @Config.Comment("OptiFine compat: never use a base texture placed in the OptiFine random directory")
    public static boolean optifinePreventBaseTextureInOptifineDirectory = true;

    @Config.Comment("Random property ids that are completely ignored (e.g. 'biomes', 'nbt')")
    public static String[] propertiesDisabled = new String[0];

    @Config.Comment("Random property ids whose spawn-locked/updating behaviour is inverted")
    public static String[] propertyInvertUpdatingOverrides = new String[0];

    @Config.Comment("Log texture variant initialization")
    public static boolean logTextureDataInitialization = false;

    private static Set<String> disabledCache;
    private static Set<String> invertedCache;

    private CETConfig() {}

    public static boolean isPropertyDisabled(String id) {
        if (disabledCache == null) disabledCache = new HashSet<>(Arrays.asList(propertiesDisabled));
        return disabledCache.contains(id);
    }

    public static boolean canPropertyUpdate(String id, boolean updatesOverTime) {
        if (invertedCache == null) invertedCache = new HashSet<>(Arrays.asList(propertyInvertUpdatingOverrides));
        return invertedCache.contains(id) != updatesOverTime;
    }

    public static boolean canDoCustomTextures() {
        if (!enableCustomTextures) return false;
        CETSubject subject = CETState.subject();
        return subject == null || !subject.isBlockEntity() || enableCustomBlockEntities;
    }

    public static boolean canDoEmissiveTextures() {
        if (!enableEmissiveTextures) return false;
        CETSubject subject = CETState.subject();
        return subject == null || !subject.isBlockEntity() || enableEmissiveBlockEntities;
    }

    public enum UpdateFrequency {
        Never(-1), Slow(80), Average(20), Fast(5), Instant(1);

        private final int delay;

        UpdateFrequency(int delay) {
            this.delay = delay;
        }

        public int getDelay() {
            return delay;
        }
    }

    public enum EmissiveRenderMode {
        DULL, BRIGHT
    }

    public enum SkinTransparencyMode {
        VANILLA, ETF_SKINS_ONLY, ALL
    }

    @Mod.EventBusSubscriber(modid = Reference.MOD_ID, value = Side.CLIENT)
    public static final class Events {
        private Events() {}

        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!Reference.MOD_ID.equals(event.getModID())) return;
            ConfigManager.sync(Reference.MOD_ID, Config.Type.INSTANCE);
            disabledCache = null;
            invertedCache = null;
            CETManager.reset();
        }
    }
}
