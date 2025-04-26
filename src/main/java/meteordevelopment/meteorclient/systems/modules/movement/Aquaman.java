/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.meteorclient.mixininterface.IVec3d;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.util.math.Vec3d;
 
 public class Aquaman extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
     private final Setting<Double> swimSpeed = sgGeneral.add(new DoubleSetting.Builder()
         .name("swim-speed")
         .description("How fast you move in water.")
         .defaultValue(1.5)
         .min(0.1)
         .max(5.0)
         .sliderRange(0.1, 5.0)
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
 
     public Aquaman() {
         super(Categories.Movement, "aquaman", "Enhances your swimming speed like Aquaman.");
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
 
         // Vertical movement
         if (mc.options.jumpKey.isPressed()) {
             moveY += 0.5;
         }
         if (mc.options.sneakKey.isPressed()) {
             moveY -= 0.5;
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
         return String.format("%.1fx", swimSpeed.get());
     }
 }
 