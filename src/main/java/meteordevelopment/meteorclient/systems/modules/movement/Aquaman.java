/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import meteordevelopment.orbit.EventHandler;
 import meteordevelopment.meteorclient.mixininterface.IVec3d;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import net.minecraft.block.Blocks;
 import net.minecraft.util.math.BlockPos;
 import net.minecraft.util.math.Vec3d;
 import meteordevelopment.meteorclient.settings.BoolSetting;
 import meteordevelopment.meteorclient.settings.DoubleSetting;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.settings.Setting;
 import meteordevelopment.meteorclient.settings.SettingGroup;
 import meteordevelopment.meteorclient.systems.modules.Module;
 
 public class Aquaman extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private final SettingGroup sgSurfaceSwimming = settings.createGroup("Surface Swimming");
 
     private final Setting<Double> swimSpeed = sgGeneral.add(new DoubleSetting.Builder()
         .name("swim-speed")
         .description("How fast you move in water.")
         .defaultValue(1.5)
         .min(0.1)
         .max(10.0)
         .sliderRange(0.1, 10.0)
         .build()
     );
 
     private final Setting<Boolean> onlyWhenSwimming = sgGeneral.add(new BoolSetting.Builder()
         .name("only-when-swimming")
         .description("Only apply speed boost when in swimming pose.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Boolean> constantSpeed = sgGeneral.add(new BoolSetting.Builder()
         .name("constant-speed")
         .description("Maintains a constant speed instead of accelerating.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Boolean> enableSurfaceSwimming = sgSurfaceSwimming.add(new BoolSetting.Builder()
         .name("enable-surface-swimming")
         .description("Positions you at the water surface when moving upward.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Double> surfaceOffset = sgSurfaceSwimming.add(new DoubleSetting.Builder()
         .name("surface-offset")
         .description("Fine-tune your position relative to the water surface.")
         .defaultValue(0.2)
         .min(0.0)
         .max(0.5)
         .sliderRange(0.0, 0.5)
         .visible(enableSurfaceSwimming::get)
         .build()
     );
 
     private final Setting<Boolean> autoSurfaceWhenUp = sgSurfaceSwimming.add(new BoolSetting.Builder()
         .name("auto-surface-when-up")
         .description("Automatically position at surface when pressing jump key.")
         .defaultValue(true)
         .visible(enableSurfaceSwimming::get)
         .build()
     );
 
     public Aquaman() {
         super(Categories.Movement, "aquaman", "Enhances your swimming speed like Aquaman.");
     }
 
     private double findWaterSurfaceLevel() {
         BlockPos playerPos = mc.player.getBlockPos();
         for (int maxCheckDistance = 5, y = 0; y <= maxCheckDistance; ++y) {
             BlockPos checkPos = playerPos.up(y);
             boolean isWater = mc.world.getBlockState(checkPos).getBlock() == Blocks.WATER;
             boolean isAir = mc.world.getBlockState(checkPos.up()).isAir();
             if (isWater && isAir) {
                 return checkPos.getY() + 1.0 - surfaceOffset.get();
             }
         }
         return mc.player.getY();
     }
 
     @EventHandler
     private void onTick(TickEvent.Post event) {
         if (!mc.player.isAlive()) return;
 
         // Only apply in water
         if (!mc.player.isTouchingWater()) return;
 
         // Check if player is in swimming pose if that setting is enabled
         if (onlyWhenSwimming.get() && !mc.player.isSwimming()) return;
 
         // Only apply when player is actually trying to move
         boolean isMoving = mc.options.forwardKey.isPressed() || 
                           mc.options.backKey.isPressed() || 
                           mc.options.leftKey.isPressed() || 
                           mc.options.rightKey.isPressed();
                           
         if (!isMoving) {
             // If not moving, maintain position but don't apply movement
             if (constantSpeed.get()) {
                 mc.player.setVelocity(0, 0, 0);
             }
             return;
         }
 
         // Calculate movement direction based on player input and look direction
         double moveX = 0;
         double moveY = 0;
         double moveZ = 0;
 
         // Forward/backward movement
         if (mc.options.forwardKey.isPressed()) {
             moveX += -Math.sin(Math.toRadians(mc.player.getYaw()));
             moveZ += Math.cos(Math.toRadians(mc.player.getYaw()));
         }
         if (mc.options.backKey.isPressed()) {
             moveX += Math.sin(Math.toRadians(mc.player.getYaw()));
             moveZ += -Math.cos(Math.toRadians(mc.player.getYaw()));
         }
 
         // Strafe movement
         if (mc.options.leftKey.isPressed()) {
             moveX += Math.cos(Math.toRadians(mc.player.getYaw()));
             moveZ += Math.sin(Math.toRadians(mc.player.getYaw()));
         }
         if (mc.options.rightKey.isPressed()) {
             moveX += -Math.cos(Math.toRadians(mc.player.getYaw()));
             moveZ += -Math.sin(Math.toRadians(mc.player.getYaw()));
         }
 
         // Handle vertical movement with surface swimming logic
         boolean movingUp = mc.options.jumpKey.isPressed();
         boolean movingDown = mc.options.sneakKey.isPressed();
 
         if (enableSurfaceSwimming.get() && movingUp && autoSurfaceWhenUp.get()) {
             double surfaceY = findWaterSurfaceLevel();
             if (mc.player.getY() < surfaceY) {
                 double distanceToSurface = surfaceY - mc.player.getY();
                 if (distanceToSurface > 0.5) {
                     moveY = 1.0;
                 } else {
                     moveY = distanceToSurface * 2.0;
                 }
             } else if (mc.player.getY() > surfaceY + 0.1) {
                 moveY = -0.1;
             } else {
                 moveY = 0.0;
                 if (constantSpeed.get()) {
                     mc.player.setPos(mc.player.getX(), surfaceY, mc.player.getZ());
                 }
             }
         } else {
             if (movingUp) {
                 moveY += 0.5;
             }
             if (movingDown) {
                 moveY -= 0.5;
             }
         }
 
         // Normalize the movement vector if it's not zero
         double length = Math.sqrt(moveX * moveX + moveY * moveY + moveZ * moveZ);
         if (length > 0) {
             moveX /= length;
             moveY /= length;
             moveZ /= length;
         }
 
         // Apply speed multiplier
         double speed = swimSpeed.get();
         moveX *= speed;
         moveY *= speed;
         moveZ *= speed;
 
         // Apply the movement
         if (constantSpeed.get()) {
             // Set velocity directly for constant speed
             mc.player.setVelocity(moveX, moveY, moveZ);
         } else {
             // Get current velocity for acceleration-based movement
             Vec3d velocity = mc.player.getVelocity();
             
             // Apply a smooth acceleration
             double factor = 0.3;
             double newX = velocity.x + (moveX - velocity.x) * factor;
             double newY = velocity.y + (moveY - velocity.y) * factor;
             double newZ = velocity.z + (moveZ - velocity.z) * factor;
             
             // Apply the new velocity
             ((IVec3d) mc.player.getVelocity()).meteor$set(newX, newY, newZ);
         }
     }
 
     @Override
     public String getInfoString() {
         return String.format("%.1fx", swimSpeed.get()) + (enableSurfaceSwimming.get() ? " Surface" : "");
     }
 } 