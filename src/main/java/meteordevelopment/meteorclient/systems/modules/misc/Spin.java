/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.misc;

 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.orbit.EventHandler;
 
 public class Spin extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
     private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
         .name("speed")
         .description("The speed at which you rotate.")
         .defaultValue(5)
         .min(0.01)
         .max(50)
         .sliderMin(0.01)
         .sliderMax(50)
         .build()
     );
 
     private float currentYaw = 0;
 
     public Spin() {
         super(Categories.Misc, "spin", "Rotates your character automatically.");
     }
 
     @Override
     public void onActivate() {
         // Store current rotation to start from
         currentYaw = mc.player.getYaw();
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         if (!mc.player.isAlive()) return;
 
         // Calculate rotation change based on speed
         float rotationChange = speed.get().floatValue();
 
         // Update yaw smoothly
         currentYaw = (currentYaw + rotationChange) % 360;
         
         // Apply rotation to player (visible to all)
         mc.player.setYaw(currentYaw);
     }
 
     @Override
     public String getInfoString() {
         return String.format("%.1f", speed.get());
     }
 }
