package com.slize.datarium.client.cet;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.scoreboard.Team;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBed;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorldNameable;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.UUID;

public final class CETSubject {
    public static final UUID GENERIC_UUID = UUID.nameUUIDFromBytes("GENERIC".getBytes());
    public static final long SPAWNER_MARKER = (12345L << 32) + 12345L;

    @Nullable private final Entity entity;
    @Nullable private final TileEntity tile;
    private final UUID uuid;
    @Nullable private final World world;
    private final BlockPos pos;
    private String entityKey;

    private CETSubject(@Nullable Entity entity, @Nullable TileEntity tile, UUID uuid, @Nullable World world, BlockPos pos) {
        this.entity = entity;
        this.tile = tile;
        this.uuid = uuid;
        this.world = world;
        this.pos = pos;
    }

    public static CETSubject of(Entity entity) {
        return new CETSubject(entity, null, entity.getUniqueID(), entity.world, new BlockPos(entity));
    }

    public static CETSubject of(TileEntity tile) {
        BlockPos pos = tile.hasWorld() ? tilePos(tile) : BlockPos.ORIGIN;
        long most = tile.hasWorld() ? tile.getWorld().getBlockState(tile.getPos()).getBlock().hashCode() : Long.MAX_VALUE;
        return new CETSubject(null, tile, new UUID(most, pos.toLong()), tile.getWorld(), pos);
    }

    public static CETSubject virtual(UUID uuid, @Nullable World world, BlockPos pos) {
        return new CETSubject(null, null, uuid, world, pos);
    }

    private static BlockPos tilePos(TileEntity tile) {
        if (tile instanceof TileEntityBed bed && bed.isHeadPiece()) {
            IBlockState state = tile.getWorld().getBlockState(tile.getPos());
            if (state.getBlock() instanceof BlockBed) {
                return tile.getPos().offset(state.getValue(BlockBed.FACING).getOpposite());
            }
            return tile.getPos().offset(EnumFacing.NORTH);
        }
        return tile.getPos();
    }

    @Nullable
    public Entity entity() {
        return entity;
    }

    @Nullable
    public EntityLivingBase living() {
        return entity instanceof EntityLivingBase living ? living : null;
    }

    @Nullable
    public TileEntity tile() {
        return tile;
    }

    public UUID uuid() {
        return uuid;
    }

    public boolean isBlockEntity() {
        return tile != null;
    }

    public boolean isGeneric() {
        return uuid.equals(GENERIC_UUID) || (pos.equals(BlockPos.ORIGIN) && uuid.getLeastSignificantBits() != SPAWNER_MARKER);
    }

    public boolean isSpawner() {
        return uuid.getLeastSignificantBits() == SPAWNER_MARKER;
    }

    @Nullable
    public World world() {
        return world;
    }

    public BlockPos blockPos() {
        return pos;
    }

    public int blockY() {
        return entity != null ? (int) Math.floor(entity.posY) : pos.getY();
    }

    public int optifineId() {
        if (tile != null) {
            int hash = CETUtils.optifineHashing(37);
            hash = CETUtils.optifineHashing(hash + pos.getX());
            hash = CETUtils.optifineHashing(hash + pos.getZ());
            return CETUtils.optifineHashing(hash + pos.getY());
        }
        return (int) (uuid.getLeastSignificantBits() & 0x7FFFFFFFL);
    }

    public int optifineVehicleId() {
        if (entity != null && entity.getRidingEntity() != null) {
            return (int) (entity.getRidingEntity().getUniqueID().getLeastSignificantBits() & 0x7FFFFFFFL);
        }
        return optifineId();
    }

    @Nullable
    public NBTTagCompound nbt() {
        return CETNbt.of(entity != null ? entity : tile);
    }

    @Nullable
    public String customName() {
        if (entity instanceof EntityPlayer player) return player.getName();
        if (entity != null) return entity.hasCustomName() ? entity.getCustomNameTag() : null;
        if (tile instanceof IWorldNameable nameable && nameable.hasCustomName()) return nameable.getName();
        return null;
    }

    @Nullable
    public Team team() {
        return entity != null ? entity.getTeam() : null;
    }

    public Iterable<ItemStack> itemsEquipped() {
        return entity != null ? entity.getEquipmentAndArmor() : Collections.emptyList();
    }

    public Iterable<ItemStack> handItems() {
        return entity != null ? entity.getHeldEquipment() : Collections.emptyList();
    }

    public Iterable<ItemStack> armorItems() {
        return entity != null ? entity.getArmorInventoryList() : Collections.emptyList();
    }

    public double horizontalVelocity() {
        if (entity == null) return 0;
        return Math.sqrt(entity.motionX * entity.motionX + entity.motionZ * entity.motionZ);
    }

    public float distanceTo(Entity other) {
        if (entity != null) return entity.getDistance(other);
        double dx = pos.getX() + 0.5 - other.posX;
        double dy = pos.getY() + 0.5 - other.posY;
        double dz = pos.getZ() + 0.5 - other.posZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean isPlayer() {
        return entity instanceof EntityPlayer;
    }

    public boolean isClientPlayer() {
        return entity != null && entity == Minecraft.getMinecraft().player;
    }

    public String entityKey() {
        if (entityKey == null) {
            if (entity != null) {
                entityKey = keyOf(entity);
            } else {
                ResourceLocation id = tile != null ? TileEntity.getKey(tile.getClass()) : null;
                entityKey = id == null ? "" : id.toString();
            }
        }
        return entityKey;
    }

    public static String keyOf(Entity entity) {
        ResourceLocation id = EntityList.getKey(entity);
        return id == null ? "" : id.toString();
    }

    public boolean canRenderBright() {
        return tile == null;
    }
}
