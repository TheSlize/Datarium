package com.slize.datarium.client.cet.property;

import com.slize.datarium.client.cet.CETNbt;
import com.slize.datarium.client.cet.CETSubject;
import com.slize.datarium.client.cet.CETUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.monster.AbstractIllager;
import net.minecraft.entity.monster.EntityBlaze;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySpellcasterIllager;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityLlama;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.Team;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.tileentity.TileEntityShulkerBox;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

public final class EtfProperties {
    private EtfProperties() {}

    public static final class Angry extends GenericProperties.BooleanProperty {
        Angry(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "angry", "isAngry", "is_angry", "aggressive", "is_aggressive"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            Entity entity = subject.entity();
            if (entity instanceof EntityEnderman enderman) return enderman.isScreaming();
            if (entity instanceof EntityBlaze blaze) return blaze.isBurning();
            if (entity instanceof EntityGuardian guardian) return guardian.hasTargetedEntity();
            if (entity instanceof EntitySpellcasterIllager caster) return caster.isSpellcasting();
            if (entity instanceof AbstractIllager illager) return illager.getArmPose() == AbstractIllager.IllagerArmPose.ATTACKING;
            if (entity instanceof EntityWolf wolf) return wolf.isAngry();
            if (entity instanceof EntityPigZombie pigman) return pigman.isAngry();
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"angry", "isAngry", "is_angry", "aggressive", "is_aggressive"};
        }
    }

    public static final class ChargedCreeper extends GenericProperties.BooleanProperty {
        ChargedCreeper(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "creeperCharged", "creeper_charged"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            return subject.entity() instanceof EntityCreeper creeper ? creeper.getPowered() : null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"creeperCharged", "creeper_charged"};
        }
    }

    public static final class ClientPlayer extends GenericProperties.BooleanProperty {
        ClientPlayer(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "isClientPlayer", "clientPlayer"));
        }

        @Override
        protected Boolean getValue(CETSubject subject) {
            return subject.isClientPlayer();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"isClientPlayer", "clientPlayer"};
        }
    }

    public static final class Creative extends GenericProperties.BooleanProperty {
        Creative(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "isCreative", "creative"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            return subject.entity() instanceof EntityPlayer player ? player.isCreative() : null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"isCreative", "creative"};
        }
    }

    public static final class Dimension extends GenericProperties.StringArrayOrRegexProperty {
        private final boolean print;

        Dimension(String data) throws Invalid {
            super(data.replace("print:", ""));
            print = data.startsWith("print:");
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            World world = subject.world();
            if (world == null) return null;
            String output = world.provider.getDimensionType().getName();
            if (print) CETUtils.log("[Dimension property print]: " + output);
            return output;
        }

        @Override
        protected boolean forceLowerCase() {
            return false;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"dimension"};
        }
    }

    public static final class Distance extends GenericProperties.FloatRangeProperty {
        Distance(Properties p, int n) throws Invalid {
            super(read(p, n, "distance", "distanceFromPlayer"));
        }

        @Nullable
        @Override
        protected Float getValue(CETSubject subject) {
            Entity player = Minecraft.getMinecraft().player;
            return player == null ? null : subject.distanceTo(player);
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"distance", "distanceFromPlayer"};
        }
    }

    public static final class Items extends GenericProperties.StringArrayOrRegexProperty {
        Items(Properties p, int n) throws Invalid {
            super(read(p, n, "items", "item").replaceAll("(?<=(^| ))minecraft:", ""));
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            if (values.size() == 1 && (values.contains("none") || values.contains("any") || values.contains("holding") || values.contains("wearing"))) {
                if (values.contains("none")) {
                    for (ItemStack stack : subject.itemsEquipped()) {
                        if (stack != null && !stack.isEmpty()) return false;
                    }
                    return true;
                }
                Iterable<ItemStack> items = values.contains("any") ? subject.itemsEquipped()
                        : values.contains("holding") ? subject.handItems() : subject.armorItems();
                for (ItemStack stack : items) {
                    if (stack != null && !stack.isEmpty()) return true;
                }
                return false;
            }
            for (ItemStack stack : subject.itemsEquipped()) {
                if (stack == null || stack.isEmpty()) continue;
                ResourceLocation id = stack.getItem().getRegistryName();
                if (id != null && matcher.test(id.toString().replaceFirst("^minecraft:", ""))) return true;
            }
            return false;
        }

        @Override
        protected boolean forceLowerCase() {
            return true;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"items", "item"};
        }
    }

    public static final class Jump extends GenericProperties.FloatRangeProperty {
        Jump(Properties p, int n) throws Invalid {
            super(read(p, n, "jump", "jumpStrength", "jumpHeight"));
        }

        @Nullable
        @Override
        protected Float getValue(CETSubject subject) {
            return subject.entity() instanceof AbstractHorse horse ? (float) horse.getHorseJumpStrength() : null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"jump", "jumpStrength", "jumpHeight"};
        }
    }

    public static final class Light extends GenericProperties.IntegerArrayProperty {
        Light(Properties p, int n) throws Invalid {
            super(GenericProperties.readIntegers(p, n, "light"));
        }

        @Override
        protected int getValue(CETSubject subject) {
            World world = subject.world();
            return world == null ? -1 : world.getLightFromNeighbors(subject.blockPos());
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"light"};
        }
    }

    public static final class LlamaInventory extends GenericProperties.IntegerArrayProperty {
        LlamaInventory(Properties p, int n) throws Invalid {
            super(GenericProperties.readIntegers(p, n, "llamaInventory"));
        }

        @Override
        protected int getValue(CETSubject subject) {
            return subject.entity() instanceof EntityLlama llama ? llama.getStrength() : Integer.MIN_VALUE;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"llamaInventory"};
        }
    }

    public static final class MaxHealth extends GenericProperties.FloatRangeProperty {
        MaxHealth(Properties p, int n) throws Invalid {
            super(read(p, n, "maxHealth", "max_health"));
        }

        @Nullable
        @Override
        protected Float getValue(CETSubject subject) {
            EntityLivingBase living = subject.living();
            return living == null ? null : living.getMaxHealth();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"maxHealth", "max_health"};
        }
    }

    public static final class Moving extends GenericProperties.BooleanProperty {
        Moving(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "moving", "is_moving"));
        }

        @Override
        protected Boolean getValue(CETSubject subject) {
            return subject.horizontalVelocity() != 0;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"moving", "is_moving"};
        }
    }

    public static final class PandaGene extends GenericProperties.StringArrayOrRegexProperty {
        PandaGene(Properties p, int n) throws Invalid {
            super(read(p, n, "hiddenGene", "gene"));
        }

        @Override
        protected boolean forceLowerCase() {
            return true;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"hiddenGene", "gene"};
        }
    }

    public static final class PlayerCreated extends GenericProperties.BooleanProperty {
        PlayerCreated(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "playerCreated", "player_created"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            return subject.entity() instanceof EntityIronGolem golem ? golem.isPlayerCreated() : null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"playerCreated", "player_created"};
        }
    }

    public static final class ScreamingGoat extends GenericProperties.BooleanProperty {
        ScreamingGoat(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "screamingGoat", "screaming_goat"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"screamingGoat", "screaming_goat"};
        }
    }

    public static final class Spawner extends GenericProperties.BooleanProperty {
        Spawner(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "isSpawner", "spawner"));
        }

        @Override
        protected Boolean getValue(CETSubject subject) {
            return subject.isSpawner();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"isSpawner", "spawner"};
        }
    }

    public static final class Speed extends GenericProperties.FloatRangeProperty {
        Speed(Properties p, int n) throws Invalid {
            super(read(p, n, "speed", "maxSpeed", "speeds"));
        }

        @Nullable
        @Override
        protected Float getValue(CETSubject subject) {
            EntityLivingBase living = subject.living();
            if (living == null) return null;
            IAttributeInstance speed = living.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED);
            return speed == null ? null : (float) speed.getAttributeValue();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"speed", "maxSpeed", "speeds"};
        }
    }

    public static final class Teams extends GenericProperties.StringArrayOrRegexProperty {
        Teams(Properties p, int n) throws Invalid {
            super(read(p, n, "teams", "team"));
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            Team team = subject.team();
            return team == null ? null : team.getName();
        }

        @Override
        protected boolean forceLowerCase() {
            return false;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"teams", "team"};
        }
    }

    public static final class Teammate extends GenericProperties.BooleanProperty {
        Teammate(Properties p, int n) throws Invalid {
            super(GenericProperties.readBoolean(p, n, "isTeammate", "teammate"));
        }

        @Nullable
        @Override
        protected Boolean getValue(CETSubject subject) {
            Entity entity = subject.entity();
            Entity player = Minecraft.getMinecraft().player;
            return entity != null && player != null ? entity.isOnSameTeam(player) : null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"isTeammate", "teammate"};
        }
    }

    public static final class Temperature extends GenericProperties.FloatRangeProperty {
        Temperature(Properties p, int n) throws Invalid {
            super(read(p, n, "temperature"));
        }

        @Nullable
        @Override
        protected Float getValue(CETSubject subject) {
            World world = subject.world();
            return world == null ? null : world.getBiome(subject.blockPos()).getTemperature(subject.blockPos());
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"temperature"};
        }
    }

    public static final class Variant extends GenericProperties.StringArrayOrRegexProperty {
        Variant(Properties p, int n) throws Invalid {
            super(read(p, n, "variant", "variants"));
        }

        @Override
        protected boolean forceLowerCase() {
            return false;
        }

        @Nullable
        @Override
        protected String getValue(CETSubject subject) {
            Entity entity = subject.entity();
            if (entity != null) {
                ResourceLocation id = EntityList.getKey(entity);
                return id == null ? null : id.getPath();
            }
            TileEntity tile = subject.tile();
            if (tile == null) return null;
            if (tile instanceof TileEntityShulkerBox box && box.getColor() != null) return colorName(box.getColor());
            if (tile instanceof TileEntityBed bed) return colorName(bed.getColor());
            String suffix = tile instanceof TileEntitySkull skull ? "_direction_" + skull.getSkullRotation() : "";
            ResourceLocation id = TileEntity.getKey(tile.getClass());
            return (id == null ? null : id.getPath()) + suffix;
        }

        private static String colorName(EnumDyeColor color) {
            return color == EnumDyeColor.SILVER ? "light_gray" : color.getName();
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"variant", "variants"};
        }
    }

    public static final class BlockSpawned extends OptiFineProperties.BlocksProperty {
        BlockSpawned(Properties p, int n) throws Invalid {
            super(p, n, "blockSpawned");
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"blockSpawned"};
        }
    }

    public static final class BlockSearch extends OptiFineProperties.BlocksProperty {
        private final String id;
        private final boolean upward;
        private final boolean solid;

        BlockSearch(Properties p, int n, String id, boolean upward, boolean solid) throws Invalid {
            super(p, n, id);
            this.id = id;
            this.upward = upward;
            this.solid = solid;
        }

        @Nullable
        @Override
        protected IBlockState[] testingBlocks(CETSubject subject) {
            if (subject.isSpawner()) return new IBlockState[]{Blocks.MOB_SPAWNER.getDefaultState()};
            World world = subject.world();
            if (world == null) return null;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(subject.blockPos());
            if (upward && world.canSeeSky(pos)) return null;
            while (pos.getY() >= 0 && pos.getY() < 256) {
                IBlockState state = world.getBlockState(pos);
                boolean stop = solid ? state.isOpaqueCube() : !state.getBlock().isAir(state, world, pos);
                if (stop) return new IBlockState[]{state};
                if (upward && solid && !state.getBlock().isAir(state, world, pos) && world.canSeeSky(pos.up())) return null;
                pos.move(upward ? EnumFacing.UP : EnumFacing.DOWN);
            }
            return null;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{id};
        }
    }

    public static final class BiomeTag extends CETProperty {
        private final String input;
        private final List<String> tags = new ArrayList<>();
        private final boolean print;

        BiomeTag(Properties p, int n) throws Invalid {
            input = read(p, n, "biomeTag", "biomeTags");
            print = input.startsWith("print:");
            for (String token : input.replaceFirst("^print:", "").split("\\s+")) {
                String tag = token.contains(":") ? token.substring(token.indexOf(':') + 1) : token;
                tag = tag.replaceFirst("^is_", "").toLowerCase(Locale.ROOT);
                if (!tag.isEmpty()) tags.add(tag);
            }
        }

        @Override
        protected boolean testInternal(CETSubject subject) {
            World world = subject.world();
            if (world == null) {
                if (print) CETUtils.log("BiomeTagProperty: " + input + " failed to read entity");
                return false;
            }
            Biome biome = world.getBiome(subject.blockPos());
            for (BiomeDictionary.Type type : BiomeDictionary.getTypes(biome)) {
                String name = type.getName().toLowerCase(Locale.ROOT);
                if (print) CETUtils.log("BiomeTagProperty: " + input + " found tag: " + name);
                if (tags.contains(name)) return true;
            }
            return false;
        }

        @Override
        public String[] getPropertyIds() {
            return new String[]{"biomeTag", "biomeTags"};
        }
    }

    static OptiFineProperties.Nbt vehicleNbt(Properties p, int n) throws CETProperty.Invalid {
        return new OptiFineProperties.Nbt(p, n, "nbtVehicle", subject -> {
            Entity entity = subject.entity();
            return entity != null && entity.getRidingEntity() != null ? CETNbt.of(entity.getRidingEntity()) : null;
        });
    }
}
