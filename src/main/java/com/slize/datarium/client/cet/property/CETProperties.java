package com.slize.datarium.client.cet.property;

import com.slize.datarium.client.cet.CETConfig;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Properties;

public final class CETProperties {

    @FunctionalInterface
    public interface Reader {
        CETProperty read(Properties properties, int ruleNumber) throws CETProperty.Invalid;
    }

    public static final class Factory {
        private final String id;
        private final Reader reader;
        private final boolean spawnLocked;

        private Factory(String id, Reader reader, boolean spawnLocked) {
            this.id = id;
            this.reader = reader;
            this.spawnLocked = spawnLocked;
        }

        public String getPropertyId() {
            return id;
        }

        public boolean updatesOverTime() {
            return !spawnLocked;
        }

        @Nullable
        public CETProperty getPropertyOrNull(Properties properties, int ruleNumber) {
            if (properties == null || CETConfig.isPropertyDisabled(id)) return null;
            try {
                CETProperty property = reader.read(properties, ruleNumber);
                if (property == null) return null;
                property.setCanUpdate(CETConfig.canPropertyUpdate(id, updatesOverTime()));
                return property;
            } catch (CETProperty.Invalid e) {
                return null;
            } catch (RuntimeException e) {
                return null;
            }
        }
    }

    private static final List<Factory> REGISTERED = new ArrayList<>();

    static {
        register("angry", EtfProperties.Angry::new, false);
        register("creeperCharged", EtfProperties.ChargedCreeper::new, false);
        register("distance", EtfProperties.Distance::new, false);
        register("items", EtfProperties.Items::new, false);
        register("jumpStrength", EtfProperties.Jump::new, true);
        register("llamaInventory", EtfProperties.LlamaInventory::new, true);
        register("maxHealth", EtfProperties.MaxHealth::new, true);
        register("moving", EtfProperties.Moving::new, false);
        register("hiddenGene", EtfProperties.PandaGene::new, true);
        register("playerCreated", EtfProperties.PlayerCreated::new, true);
        register("screamingGoat", EtfProperties.ScreamingGoat::new, true);
        register("maxSpeed", EtfProperties.Speed::new, true);
        register("isSpawner", EtfProperties.Spawner::new, true);
        register("dimension", (p, n) -> new EtfProperties.Dimension(CETProperty.read(p, n, "dimension")), true);
        register("light", EtfProperties.Light::new, false);
        register("variant", EtfProperties.Variant::new, true);
        register("isCreative", EtfProperties.Creative::new, false);
        register("isTeammate", EtfProperties.Teammate::new, false);
        register("isClientPlayer", EtfProperties.ClientPlayer::new, true);
        register("teams", EtfProperties.Teams::new, false);
        register("blockSpawned", EtfProperties.BlockSpawned::new, true);
        register("nbtVehicle", EtfProperties::vehicleNbt, false);
        register("blockAbove", (p, n) -> new EtfProperties.BlockSearch(p, n, "blockAbove", true, false), false);
        register("blockAboveSolid", (p, n) -> new EtfProperties.BlockSearch(p, n, "blockAboveSolid", true, true), false);
        register("blockBelow", (p, n) -> new EtfProperties.BlockSearch(p, n, "blockBelow", false, false), false);
        register("blockBelowSolid", (p, n) -> new EtfProperties.BlockSearch(p, n, "blockBelowSolid", false, true), false);
        register("biomeTag", EtfProperties.BiomeTag::new, true);
        register("temperature", EtfProperties.Temperature::new, true);

        register("hour", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.HOUR_OF_DAY, "hour"), false);
        register("minute", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.MINUTE, "minute"), false);
        register("monthDay", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.DAY_OF_MONTH, "monthDay", "dayMonth"), true);
        register("month", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.MONTH, "month"), true);
        register("second", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.SECOND, "second"), false);
        register("weekDay", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.DAY_OF_WEEK, "weekDay", "dayWeek"), true);
        register("yearDay", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.DAY_OF_YEAR, "yearDay", "dayYear"), true);
        register("year", (p, n) -> new ExternalProperties.CalendarField(p, n, Calendar.YEAR, "year"), true);
        register("language", ExternalProperties.Language::new, true);
        register("textureSuffix", ExternalProperties::textureSuffix, false);
        register("textureRule", ExternalProperties::textureRule, false);
        register("modLoaded", ExternalProperties.ModLoaded::new, false);
        register("nbtClient", ExternalProperties::clientNbt, false);
        register("minecraftVersion", ExternalProperties.MinecraftVersion::new, false);
        register("Difficulty", ExternalProperties::difficulty, false);
        register("regionalDifficulty", ExternalProperties.RegionalDifficulty::new, false);
        register("clientGameMode", ExternalProperties::clientGameMode, false);
        register("hardcore", ExternalProperties.Hardcore::new, false);
        register("usingShaders", ExternalProperties.UsingShaders::new, false);
        register("resourcepack", ExternalProperties.ResourcePackLoaded::new, false);

        register("baby", OptiFineProperties.Baby::new, false);
        register("biomes", OptiFineProperties.Biomes::create, true);
        register("blocks", OptiFineProperties.BlocksProperty::create, false);
        register("colors", OptiFineProperties.Colors::new, false);
        register("health", OptiFineProperties.Health::new, false);
        register("heights", OptiFineProperties.Height::create, true);
        register("moonPhase", OptiFineProperties.MoonPhase::new, true);
        register("name", OptiFineProperties.Name::create, false);
        register("nbt", OptiFineProperties.Nbt::create, false);
        register("professions", OptiFineProperties.Professions::new, false);
        register("sizes", OptiFineProperties.Size::new, false);
        register("dayTime", OptiFineProperties.DayTime::new, false);
        register("weather", OptiFineProperties.Weather::new, true);
    }

    private CETProperties() {}

    public static void register(String id, Reader reader, boolean spawnLocked) {
        REGISTERED.removeIf(factory -> factory.id.equals(id));
        REGISTERED.add(new Factory(id, reader, spawnLocked));
    }

    public static CETProperty[] getAllOfRule(Properties properties, int ruleNumber) {
        List<CETProperty> out = new ArrayList<>();
        for (Factory factory : REGISTERED) {
            CETProperty property = factory.getPropertyOrNull(properties, ruleNumber);
            if (property != null) out.add(property);
        }
        return out.toArray(new CETProperty[0]);
    }
}
