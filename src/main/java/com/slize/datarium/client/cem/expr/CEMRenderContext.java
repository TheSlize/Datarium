package com.slize.datarium.client.cem.expr;

import com.slize.datarium.DatariumMain;
import com.slize.datarium.client.cem.*;
import com.slize.datarium.mixin.accessors.AccessorEntityLivingBase;
import com.slize.datarium.mixin.accessors.AccessorEntityPigZombie;
import jakarta.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.AbstractHorse;
import net.minecraft.entity.passive.EntityTameable;
import net.minecraft.entity.passive.EntityWolf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHandSide;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class CEMRenderContext {
    private static final double[] EMPTY_DOUBLES = new double[0];
    private static final boolean[] EMPTY_BOOLS = new boolean[0];

    private double[] varSlots = EMPTY_DOUBLES;
    private boolean[] varbIsSet = EMPTY_BOOLS;
    private boolean[] varbValue = EMPTY_BOOLS;
    private boolean[] varIsSet = EMPTY_BOOLS;

    private final Map<String, Double> partValues;

    @Nullable private EntityLivingBase entity;
    @Nullable private Entity anyEntity;
    @Nullable private TileEntity tile;
    private float limbSwing;
    private float limbSwingAmount;
    private float ageInTicks;
    private float netHeadYaw;
    private float headPitch;
    private float partialTicks;
    private long entityId = -1;

    private long frameCounter;
    private float frameTime;
    private int ruleIndex;
    private boolean inHand;
    private boolean onHead;
    private boolean inItemFrame;
    private boolean onShoulder;

    private final double[] renderValues = new double[CEMRenderVar.values().length];

    private static final int WRAP = 27720;
    private static final Map<Long, Integer> HIGHEST_ANGER = new ConcurrentHashMap<>();

    public CEMRenderContext() {
        this.partValues = new HashMap<>();
        this.frameCounter = 0;
        this.frameTime = 0.05F;
        Arrays.fill(renderValues, Double.NaN);
    }

    /**
     * @param frameTime seconds since this context's last actual update. Throttled callers must pass
     *                  the accumulated delta.
     */
    public void setup(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                      float ageInTicks, float netHeadYaw, float headPitch, float partialTicks, float frameTime) {
        begin(entity.getEntityId(), entity, null, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks, frameTime);
    }

    public void setupShoulder(EntityLivingBase player, boolean left, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch, float partialTicks, float frameTime) {
        begin(left ? 0 : 1, player, null, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, partialTicks, frameTime);
        this.onShoulder = true;
    }

    public void setupGeneric(long id, @Nullable Entity anyEntity, @Nullable TileEntity tile, float limbSwing,
                             float limbSwingAmount, float ageInTicks, float partialTicks, float frameTime) {
        begin(id, anyEntity, tile, limbSwing, limbSwingAmount, ageInTicks, 0, 0, partialTicks, frameTime);
    }

    private void begin(long id, @Nullable Entity anyEntity, @Nullable TileEntity tile, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch, float partialTicks, float frameTime) {
        if (id != this.entityId) {
            Arrays.fill(varSlots, 0);
            Arrays.fill(varIsSet, false);
            Arrays.fill(varbIsSet, false);
        }

        this.anyEntity = anyEntity;
        this.entity = anyEntity instanceof EntityLivingBase living ? living : null;
        this.tile = tile;
        this.inHand = false;
        this.onHead = false;
        this.inItemFrame = false;
        this.onShoulder = false;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
        this.partialTicks = partialTicks;
        this.entityId = id;

        this.frameCounter = CEMManager.getFrameCounter();
        this.frameTime = frameTime;

        partValues.clear();
        Arrays.fill(renderValues, Double.NaN);
    }

    public void setRuleIndex(int ruleIndex) {
        this.ruleIndex = ruleIndex;
    }

    public void setRenderFlags(boolean inHand, boolean onHead, boolean inItemFrame) {
        this.inHand = inHand;
        this.onHead = onHead;
        this.inItemFrame = inItemFrame;
    }

    public void setRenderValue(CEMRenderVar var, double value) {
        renderValues[var.ordinal()] = value;
    }

    public double getRenderValue(CEMRenderVar var) {
        return renderValues[var.ordinal()];
    }

    /** {@code slot} comes from {@link CEMVarSlots#slotFor}. */
    public void setVariable(int slot, double value) {
        if (slot >= varSlots.length) {
            varSlots = Arrays.copyOf(varSlots, slot + 1);
            varIsSet = Arrays.copyOf(varIsSet, slot + 1);
        }
        varSlots[slot] = value;
        varIsSet[slot] = true;
    }

    /** {@code slot} comes from {@link CEMVarSlots#slotFor}, shared with "var." of the same short name. */
    public void setBoolVariable(int slot, boolean value) {
        if (slot >= varbIsSet.length) {
            varbIsSet = Arrays.copyOf(varbIsSet, slot + 1);
            varbValue = Arrays.copyOf(varbValue, slot + 1);
        }
        varbIsSet[slot] = true;
        varbValue[slot] = value;
    }

    /** {@code fullKey} is the "partId.property" animation key. */
    public void setPartValueByFullKey(String fullKey, double value) {
        partValues.put(fullKey, value);
    }

    double variable(int slot) {
        return slot < varSlots.length ? varSlots[slot] : 0;
    }

    double boolVariable(int slot) {
        if (slot < varbIsSet.length && varbIsSet[slot]) return varbValue[slot] ? 1 : 0;
        return slot < varSlots.length ? varSlots[slot] : 0;
    }

    double resolve(CEMVarRef ref) {
        if (CEMProfiler.enabled) CEMProfiler.hitVar(ref);
        return switch (ref.kind) {
            case CUSTOM_VAR -> variable(ref.slot);
            case CUSTOM_VARB -> boolVariable(ref.slot);
            case GLOBAL_VAR -> CEMGlobalVars.get(ref.slot);
            case GLOBAL_VARB -> CEMGlobalVars.getBool(ref.slot);
            case RENDER -> {
                double v = renderValues[ref.render.ordinal()];
                yield Double.isNaN(v) ? 0 : v;
            }
            case PART -> {
                Double partVal = partValues.get(ref.name);
                if (partVal != null) yield partVal;
                double live = getLivePartValue(ref.partId, ref.property);
                yield Double.isNaN(live) ? 0 : live;
            }
            case BUILTIN -> resolveBuiltin(ref.builtin);
            case API -> CEMApiRegistry.variable(ref.slot);
            case UNKNOWN -> unknown(ref);
        };
    }

    double resolveBuiltin(CEMBuiltinVar b) {
        return switch (b) {
            case IS_IN_GROUND -> 0;

            case HEAD_YAW -> netHeadYaw;
            case HEAD_PITCH -> headPitch;

            case LIMB_SWING -> limbSwing;
            case LIMB_SPEED -> limbSwingAmount;

            case AGE -> ageInTicks >= WRAP ? ageInTicks % WRAP : ageInTicks;
            case FRAME_TIME -> frameTime;
            case FRAME_COUNTER -> frameCounter;

            case IS_CHILD -> entity != null && entity.isChild() ? 1 : 0;
            case IS_RIDING -> anyEntity != null && anyEntity.isRiding() ? 1 : 0;
            case IS_RIDDEN -> anyEntity != null && !anyEntity.getPassengers().isEmpty() ? 1 : 0;
            case IS_SNEAKING -> anyEntity != null && anyEntity.isSneaking() ? 1 : 0;
            case IS_SPRINTING -> anyEntity != null && anyEntity.isSprinting() ? 1 : 0;
            case IS_WET -> anyEntity != null && anyEntity.isWet() ? 1 : 0;
            case IS_IN_WATER -> anyEntity != null && anyEntity.isInWater() ? 1 : 0;
            case IS_IN_LAVA -> anyEntity != null && anyEntity.isInLava() ? 1 : 0;
            case IS_ON_GROUND -> anyEntity != null && anyEntity.onGround ? 1 : 0;
            case IS_ALIVE -> anyEntity != null ? (anyEntity.isEntityAlive() ? 1 : 0) : (tile != null ? 1 : 0);
            case IS_BURNING -> anyEntity != null && anyEntity.isBurning() ? 1 : 0;
            case IS_INVISIBLE -> anyEntity != null && anyEntity.isInvisible() ? 1 : 0;
            case IS_GLOWING -> anyEntity != null && anyEntity.isGlowing() ? 1 : 0;
            case IS_HURT -> entity != null && entity.hurtTime > 0 ? 1 : 0;
            case IS_AGGRESSIVE -> isAggressive() ? 1 : 0;
            case IS_SITTING -> entity instanceof EntityTameable tameable && tameable.isSitting() ? 1 : 0;

            case HURT_TIME -> entity != null && entity.hurtTime > 0 ? entity.hurtTime - partialTicks : 0;
            case DEATH_TIME -> entity != null && entity.deathTime > 0 ? entity.deathTime + partialTicks : 0;

            case HEALTH -> entity != null ? entity.getHealth() : 1;
            case MAX_HEALTH -> entity != null ? entity.getMaxHealth() : 1;

            case POS_X -> posX();
            case POS_Y -> posY();
            case POS_Z -> posZ();

            case PLAYER_POS_X -> getPlayerX();
            case PLAYER_POS_Y -> getPlayerY();
            case PLAYER_POS_Z -> getPlayerZ();

            case ROT_X -> anyEntity != null ? Math.toRadians(lerpAngle(anyEntity.prevRotationPitch, anyEntity.rotationPitch)) : 0;
            case ROT_Y -> {
                if (entity != null) yield Math.toRadians(CEMRenderHooks.isRenderingInGui()
                        ? entity.renderYawOffset : lerpAngle(entity.prevRenderYawOffset, entity.renderYawOffset));
                yield anyEntity != null ? Math.toRadians(lerpAngle(anyEntity.prevRotationYaw, anyEntity.rotationYaw)) : 0;
            }
            case PLAYER_ROT_X -> {
                Entity player = Minecraft.getMinecraft().player;
                yield player != null ? Math.toRadians(lerpAngle(player.prevRotationPitch, player.rotationPitch)) : 0;
            }
            case PLAYER_ROT_Y -> {
                Entity player = Minecraft.getMinecraft().player;
                yield player != null ? Math.toRadians(lerpAngle(player.prevRotationYaw, player.rotationYaw)) : 0;
            }

            case MOVE_FORWARD -> moveDirection(true);
            case MOVE_STRAFING -> moveDirection(false);

            case DIMENSION -> {
                World world = world();
                yield anyEntity != null ? anyEntity.dimension : world != null ? world.provider.getDimension() : 0;
            }
            case ID -> entityId;
            case SWING_PROGRESS -> entity != null ? entity.getSwingProgress(partialTicks) : 0;

            case IS_CLIMBING -> entity != null && entity.isOnLadder() ? 1 : 0;
            case IS_GLIDING -> entity != null && entity.isElytraFlying() ? 1 : 0;
            case IS_USING_ITEM -> entity != null && entity.isHandActive() ? 1 : 0;
            case IS_BLOCKING -> entity != null && entity.isActiveItemStackBlocking() ? 1 : 0;
            case IS_JUMPING -> entity != null
                    && ((AccessorEntityLivingBase) entity).datarium$isJumping() ? 1 : 0;
            case IS_RIGHT_HANDED -> entity == null
                    || entity.getPrimaryHand() == EnumHandSide.RIGHT ? 1 : 0;
            case IS_SWINGING_RIGHT_ARM -> isSwinging(EnumHandSide.RIGHT);
            case IS_SWINGING_LEFT_ARM -> isSwinging(EnumHandSide.LEFT);
            case IS_HOLDING_ITEM_RIGHT -> isHolding(EnumHandSide.RIGHT);
            case IS_HOLDING_ITEM_LEFT -> isHolding(EnumHandSide.LEFT);
            case IS_FLYING -> entity instanceof EntityPlayer player
                    && player.capabilities.isFlying ? 1 : 0;
            case IS_CRAWLING -> CEMAquaAcrobatics.isCrawling(entity) ? 1 : 0;
            case IS_SWIMMING -> CEMAquaAcrobatics.isSwimming(entity) ? 1 : 0;
            case TIME -> worldTime();
            case IS_PAUSED -> Minecraft.getMinecraft().isGamePaused() ? 1 : 0;
            case IS_IN_GUI -> CEMRenderHooks.isRenderingInGui() ? 1 : 0;
            case IS_FIRST_PERSON_HAND -> CEMFirstPerson.isActive() ? 1 : 0;
            case FLUID_DEPTH_UP -> fluidDepth(true);
            case FLUID_DEPTH_DOWN -> fluidDepth(false);
            case FLUID_DEPTH -> {
                double up = fluidDepth(true);
                yield up == 0 ? 0 : up + fluidDepth(false) - 1;
            }
            case DAY_TIME -> {
                World world = world();
                yield world != null ? world.getWorldTime() % 24000L + partialTicks : partialTicks;
            }
            case DAY_COUNT -> {
                World world = world();
                yield world != null ? world.getWorldTime() / 24000L : 0;
            }
            case RULE_INDEX -> ruleIndex;
            case ANGER_TIME -> angerTime();
            case ANGER_TIME_START -> entity instanceof EntityPigZombie ? HIGHEST_ANGER.getOrDefault(entityId, 0) : 0;
            case DISTANCE -> distanceToPlayer();
            case HEIGHT_ABOVE_GROUND -> heightAboveGround();
            case IS_TAMED -> entity instanceof EntityTameable tameable && tameable.isTamed()
                    || entity instanceof AbstractHorse horse && horse.isTame() ? 1 : 0;
            case IS_IN_HAND -> inHand ? 1 : 0;
            case IS_ON_HEAD -> onHead ? 1 : 0;
            case IS_IN_ITEM_FRAME -> inItemFrame ? 1 : 0;
            case IS_ON_SHOULDER -> onShoulder ? 1 : 0;
            case IS_HOVERED -> isHovered() ? 1 : 0;
            case IS_PLAYER_FIRST_PERSON -> Minecraft.getMinecraft().gameSettings.thirdPersonView == 0 ? 1 : 0;
            case IS_PLAYER_THIRD_PERSON -> Minecraft.getMinecraft().gameSettings.thirdPersonView == 1 ? 1 : 0;
            case IS_PLAYER_THIRD_PERSON_REVERSED -> Minecraft.getMinecraft().gameSettings.thirdPersonView == 2 ? 1 : 0;
        };
    }

    private double lerpAngle(float prev, float current) {
        float delta = current - prev;
        while (delta < -180.0F) delta += 360.0F;
        while (delta >= 180.0F) delta -= 360.0F;
        return prev + delta * partialTicks;
    }

    private double lerp(double prev, double current) {
        return prev + (current - prev) * partialTicks;
    }

    @Nullable
    private World world() {
        if (anyEntity != null) return anyEntity.world;
        if (tile != null && tile.getWorld() != null) return tile.getWorld();
        return Minecraft.getMinecraft().world;
    }

    private double posX() {
        if (anyEntity != null) return lerp(anyEntity.prevPosX, anyEntity.posX);
        return tile != null ? tile.getPos().getX() + 0.5 : 0;
    }

    private double posY() {
        if (anyEntity != null) return lerp(anyEntity.prevPosY, anyEntity.posY);
        return tile != null ? tile.getPos().getY() : 0;
    }

    private double posZ() {
        if (anyEntity != null) return lerp(anyEntity.prevPosZ, anyEntity.posZ);
        return tile != null ? tile.getPos().getZ() + 0.5 : 0;
    }

    @Nullable
    private BlockPos blockPos() {
        if (anyEntity != null) return new BlockPos(anyEntity);
        return tile != null ? tile.getPos() : null;
    }

    private boolean isHovered() {
        Minecraft mc = Minecraft.getMinecraft();
        if (anyEntity != null) return mc.pointedEntity == anyEntity;
        if (tile == null || mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != RayTraceResult.Type.BLOCK) return false;
        return tile.getPos().equals(mc.objectMouseOver.getBlockPos());
    }

    private double angerTime() {
        if (!(entity instanceof EntityPigZombie pigman)) return 0;
        int anger = ((AccessorEntityPigZombie) pigman).datarium$getAngerLevel();
        if (anger <= 0) {
            HIGHEST_ANGER.remove(entityId);
            return 0;
        }
        HIGHEST_ANGER.merge(entityId, anger, Math::max);
        return anger - partialTicks;
    }

    private double distanceToPlayer() {
        Entity player = Minecraft.getMinecraft().player;
        if ((anyEntity == null && tile == null) || player == null) return 0;
        double dx = posX() - lerp(player.prevPosX, player.posX);
        double dy = posY() - lerp(player.prevPosY, player.posY);
        double dz = posZ() - lerp(player.prevPosZ, player.posZ);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private double heightAboveGround() {
        World world = world();
        BlockPos start = blockPos();
        if (world == null || start == null) return 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(tile != null ? start.down() : start);
        while (pos.getY() > 0 && !world.getBlockState(pos).isSideSolid(world, pos, EnumFacing.UP)) {
            pos.move(EnumFacing.DOWN);
        }
        return posY() - pos.getY();
    }

    private double fluidDepth(boolean up) {
        World world = world();
        BlockPos start = blockPos();
        if (world == null || start == null || !world.getBlockState(start).getMaterial().isLiquid()) return 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(start);
        EnumFacing step = up ? EnumFacing.UP : EnumFacing.DOWN;
        while (pos.getY() > 0 && pos.getY() < 256 && world.getBlockState(pos).getMaterial().isLiquid()) {
            pos.move(step);
        }
        return Math.abs(pos.getY() - start.getY());
    }

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private double unknown(CEMVarRef ref) {
        if (ref.slot >= 0 && ref.slot < varIsSet.length && varIsSet[ref.slot]) return varSlots[ref.slot];
        String name = ref.name;
        if (REPORTED.add(name)) {
            DatariumMain.LOGGER.warn("[CEM] unsupported variable '{}' (model {}) - returning 0",
                    name, CEMRenderHooks.getActiveModelName());
        }
        return 0.0;
    }

    private double moveDirection(boolean forward) {
        if (anyEntity == null || CEMRenderHooks.isRenderingInGui()) return 0;
        double dx = anyEntity.posX - anyEntity.prevPosX;
        double dz = anyEntity.posZ - anyEntity.prevPosZ;
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-4) return 0;
        double yaw = Math.toRadians(entity != null
                ? lerpAngle(entity.prevRotationYawHead, entity.rotationYawHead)
                : lerpAngle(anyEntity.prevRotationYaw, anyEntity.rotationYaw));
        double lookX = -Math.sin(yaw);
        double lookZ = Math.cos(yaw);
        return forward ? (dx * lookX + dz * lookZ) / length : (dz * lookX - dx * lookZ) / length;
    }

    private double worldTime() {
        World world = world();
        return world != null ? world.getTotalWorldTime() % WRAP + partialTicks : partialTicks;
    }

    private boolean isAggressive() {
        if (entity instanceof EntityBlaze blaze && blaze.isCharged()) return true;
        if (entity instanceof EntitySpellcasterIllager caster && caster.isSpellcasting()) return true;
        if (entity instanceof AbstractIllager illager) {
            return illager.getArmPose() != AbstractIllager.IllagerArmPose.CROSSED;
        }
        if (entity instanceof AbstractSkeleton skeleton) return skeleton.isSwingingArms();
        if (entity instanceof EntityPigZombie pigman) return pigman.isAngry();
        if (entity instanceof EntityZombie zombie) return zombie.isArmsRaised();
        if (entity instanceof EntityEnderman enderman) return enderman.isScreaming();
        if (entity instanceof EntityPolarBear bear) return bear.isStanding();
        if (entity instanceof EntityWolf wolf) return wolf.isAngry();
        if (entity instanceof EntityGuardian guardian) return guardian.hasTargetedEntity();
        if (entity instanceof EntityVex vex) return vex.isCharging();
        return entity instanceof EntityLiving living && living.getAttackTarget() != null;
    }

    private boolean isMainHand(EnumHandSide side) {
        return entity == null || (side == EnumHandSide.RIGHT) == (entity.getPrimaryHand() == EnumHandSide.RIGHT);
    }

    private double isHolding(EnumHandSide side) {
        if (entity == null) return 0;
        ItemStack held = isMainHand(side) ? entity.getHeldItemMainhand() : entity.getHeldItemOffhand();
        return held.isEmpty() ? 0 : 1;
    }

    private double isSwinging(EnumHandSide side) {
        if (entity == null) return 0;
        boolean main = isMainHand(side);
        if (entity.isHandActive() && (entity.getActiveHand() == EnumHand.MAIN_HAND) == main) return 1;
        if (!entity.isSwingInProgress) return 0;
        return (entity.swingingHand == EnumHand.MAIN_HAND) == main ? 1 : 0;
    }

    private double getLivePartValue(String partId, String property) {
        CEMModelWrapper secondary = CEMRenderHooks.getActiveSecondaryWrapper();
        if (secondary != null) {
            CEMModelRenderer part = secondary.getPartRenderer(partId);
            if (part != null) return part.getProperty(property);
        }

        CEMModelWrapper wrapper = CEMRenderHooks.getActiveWrapper();
        if (wrapper == null) return Double.NaN;

        CEMModelRenderer part = wrapper.getPartRenderer(partId);
        if (part == null) return Double.NaN;

        return part.getProperty(property);
    }

    private double getPlayerX() {
        Entity player = Minecraft.getMinecraft().player;
        return player != null ? lerp(player.prevPosX, player.posX) : 0;
    }

    private double getPlayerY() {
        Entity player = Minecraft.getMinecraft().player;
        return player != null ? lerp(player.prevPosY, player.posY) : 0;
    }

    private double getPlayerZ() {
        Entity player = Minecraft.getMinecraft().player;
        return player != null ? lerp(player.prevPosZ, player.posZ) : 0;
    }

    public Map<String, Double> dumpValues() {
        Map<String, Double> out = new TreeMap<>();
        for (CEMBuiltinVar builtin : CEMBuiltinVar.values()) {
            try {
                out.put(builtin.name().toLowerCase(Locale.ROOT), resolveBuiltin(builtin));
            } catch (Exception ignored) {
            }
        }
        for (Map.Entry<String, Integer> e : CEMVarSlots.all().entrySet()) {
            int slot = e.getValue();
            if (slot < varIsSet.length && varIsSet[slot]) out.put("var." + e.getKey(), varSlots[slot]);
            if (slot < varbIsSet.length && varbIsSet[slot]) out.put("varb." + e.getKey(), varbValue[slot] ? 1.0 : 0.0);
        }
        for (Map.Entry<String, Integer> e : CEMGlobalVars.all().entrySet()) {
            out.put("global_var." + e.getKey(), CEMGlobalVars.get(e.getValue()));
        }
        for (CEMRenderVar v : CEMRenderVar.values()) {
            if (!Double.isNaN(renderValues[v.ordinal()])) out.put("render." + v.name().toLowerCase(Locale.ROOT), renderValues[v.ordinal()]);
        }
        out.putAll(partValues);
        return out;
    }

    public long getEntityId() {
        return entityId;
    }

    public EntityLivingBase getEntity() {
        return entity;
    }

    @Nullable
    public Object getRenderedObject() {
        return anyEntity != null ? anyEntity : tile;
    }
}