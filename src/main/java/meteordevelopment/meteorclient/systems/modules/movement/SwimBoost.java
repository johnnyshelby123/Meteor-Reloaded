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
 
 public class SwimBoost extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
     private final Setting<Double> swimSpeed = sgGeneral.add(new DoubleSetting.Builder()
         .name("swim-speed")
         .description("The speed multiplier for swimming.")
         .defaultValue(1.0)
         .min(0.1)
         .max(5.0)
         .sliderRange(0.1, 5.0)
         .build()
     );
 
     private final Setting<Boolean> workInLava = sgGeneral.add(new BoolSetting.Builder()
         .name("work-in-lava")
         .description("Whether the swim boost should also work in lava.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Boolean> workInWater = sgGeneral.add(new BoolSetting.Builder()
         .name("work-in-water")
         .description("Whether the swim boost should work in water.")
         .defaultValue(true)
         .build()
     );
 
     public SwimBoost() {
         super(Categories.Movement, "swim-boost", "Allows you to swim faster or slower than vanilla.");
     }
 
     @EventHandler
     private void onTick(TickEvent.Post event) {
         if (!mc.player.isAlive()) return;
 
         // Check if player is in water and the setting is enabled
         boolean inWater = mc.player.isTouchingWater() && workInWater.get();
         boolean inLava = mc.player.isInLava() && workInLava.get();
 
         if (!inWater && !inLava) return;
 
         // Only apply when player is actually swimming (moving in the liquid)
         boolean isMoving = mc.options.forwardKey.isPressed() || 
                           mc.options.backKey.isPressed() || 
                           mc.options.leftKey.isPressed() || 
                           mc.options.rightKey.isPressed();
                           
         if (!isMoving) return;
 
         // Get the current velocity
         double velX = mc.player.getVelocity().x;
         double velY = mc.player.getVelocity().y;
         double velZ = mc.player.getVelocity().z;
 
         // Apply the speed multiplier
         double multiplier = swimSpeed.get();
         
         // Apply the multiplier to horizontal movement only
         ((IVec3d) mc.player.getVelocity()).meteor$set(
             velX * multiplier,
             velY,
             velZ * multiplier
         );
     }
 
     @Override
     public String getInfoString() {
         return String.format("%.1fx", swimSpeed.get());
     }
 }
 