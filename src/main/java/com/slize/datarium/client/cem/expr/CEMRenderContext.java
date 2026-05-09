package com.slize.datarium.client.cem.expr;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import java.util.HashMap;
import java.util.Map;

public class CEMRenderContext {
    private final Map<String, Double> variables;
    private final Map<String, Double> partValues;
    private final Map<String, Boolean> boolVariables;

    private EntityLivingBase entity;
    private float limbSwing;
    private float limbSwingAmount;
    private float ageInTicks;
    private float netHeadYaw;
    private float headPitch;
    private float partialTicks;
    private long entityId;

    private long frameCounter;
    private long lastFrameTime;

    public CEMRenderContext() {
        this.variables = new HashMap<>();
        this.partValues = new HashMap<>();
        this.boolVariables = new HashMap<>();
        this.frameCounter = 0;
        this.lastFrameTime = System.currentTimeMillis();
    }

    public void setup(EntityLivingBase entity, float limbSwing, float limbSwingAmount,
                      float ageInTicks, float netHeadYaw, float headPitch, float partialTicks) {
        this.entity = entity;
        this.limbSwing = limbSwing;
        this.limbSwingAmount = limbSwingAmount;
        this.ageInTicks = ageInTicks;
        this.netHeadYaw = netHeadYaw;
        this.headPitch = headPitch;
        this.partialTicks = partialTicks;
        this.entityId = entity.getEntityId();

        long now = System.currentTimeMillis();
        if (now - lastFrameTime > 10) {
            frameCounter++;
            lastFrameTime = now;
        }

        variables.clear();
        partValues.clear();
        boolVariables.clear();
    }

    public void setVariable(String name, double value) {
        variables.put(name, value);
    }

    public void setBoolVariable(String name, boolean value) {
        boolVariables.put(name, value);
    }

    public void setPartValue(String partId, String property, double value) {
        partValues.put(partId + "." + property, value);
    }

    public double getVariable(String name) {
        // Boolean variables (varb.xxx)
        if (name.startsWith("varb.")) {
            String boolName = name.substring(5);
            Boolean val = boolVariables.get(boolName);
            if (val != null) return val ? 1 : 0;
            // Check if it's a comparison result stored as regular var
            Double d = variables.get(boolName);
            return d != null ? d : 0;
        }

        // Custom variables (var.xxx)
        if (name.startsWith("var.")) {
            String varName = name.substring(4);
            Double val = variables.get(varName);
            return val != null ? val : 0;
        }

        // Part properties (part.property)
        if (name.contains(".")) {
            Double partVal = partValues.get(name);
            if (partVal != null) {
                return partVal;
            }
        }

        // Built-in variables
        return switch (name) {
            case "pi" -> Math.PI;
            case "true" -> 1;
            case "false", "is_aggressive", "is_sitting" -> 0;

            // Head
            case "head_yaw" -> netHeadYaw;
            case "head_pitch" -> headPitch;

            // Limbs
            case "limb_swing" -> limbSwing;
            case "limb_speed" -> limbSwingAmount;

            // Time
            case "age" -> ageInTicks;
            case "time" -> ageInTicks;
            case "frame_time" -> partialTicks / 20.0;
            case "frame_counter" -> frameCounter;

            // Entity state
            case "is_child" -> entity != null && entity.isChild() ? 1 : 0;
            case "is_riding", "is_ridden" -> entity != null && entity.isRiding() ? 1 : 0;
            case "is_sneaking" -> entity != null && entity.isSneaking() ? 1 : 0;
            case "is_sprinting" -> entity != null && entity.isSprinting() ? 1 : 0;
            case "is_wet" -> entity != null && entity.isWet() ? 1 : 0;
            case "is_in_water" -> entity != null && entity.isInWater() ? 1 : 0;
            case "is_in_lava" -> entity != null && entity.isInLava() ? 1 : 0;
            case "is_on_ground" -> entity != null && entity.onGround ? 1 : 0;
            case "is_alive" -> entity != null && entity.isEntityAlive() ? 1 : 0;
            case "is_burning" -> entity != null && entity.isBurning() ? 1 : 0;
            case "is_invisible" -> entity != null && entity.isInvisible() ? 1 : 0;
            case "is_glowing" -> entity != null && entity.isGlowing() ? 1 : 0;
            case "is_hurt" -> entity != null && entity.hurtTime > 0 ? 1 : 0;

            case "hurt_time" -> entity != null ? entity.hurtTime : 0;
            case "death_time" -> entity != null ? entity.deathTime : 0;

            // Health
            case "health" -> entity != null ? entity.getHealth() : 0;
            case "max_health" -> entity != null ? entity.getMaxHealth() : 0;

            // Position
            case "pos_x" -> entity != null ? entity.posX : 0;
            case "pos_y" -> entity != null ? entity.posY : 0;
            case "pos_z" -> entity != null ? entity.posZ : 0;

            // Player position
            case "player_pos_x" -> getPlayerX();
            case "player_pos_y" -> getPlayerY();
            case "player_pos_z" -> getPlayerZ();

            // Rotation
            case "rot_x" -> entity != null ? Math.toRadians(entity.rotationPitch) : 0;
            case "rot_y" -> entity != null ? Math.toRadians(entity.rotationYaw) : 0;

            // Movement
            case "move_forward" -> entity != null ? entity.moveForward : 0;
            case "move_strafing" -> entity != null ? entity.moveStrafing : 0;

            // Dimension
            case "dimension" -> entity != null ? entity.dimension : 0;

            // Entity ID
            case "id" -> entityId;

            // Swing progress
            case "swing_progress" -> entity != null ? entity.swingProgress : 0;

            default -> variables.getOrDefault(name, 0.0);
        };
    }

    private double getPlayerX() {
        Entity player = Minecraft.getMinecraft().player;
        return player != null ? player.posX : 0;
    }

    private double getPlayerY() {
        Entity player = Minecraft.getMinecraft().player;
        return player != null ? player.posY : 0;
    }

    private double getPlayerZ() {
        Entity player = Minecraft.getMinecraft().player;
        return player != null ? player.posZ : 0;
    }

    public long getEntityId() {
        return entityId;
    }

    public EntityLivingBase getEntity() {
        return entity;
    }

}