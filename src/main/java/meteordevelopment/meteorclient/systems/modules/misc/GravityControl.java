/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.misc;

 import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.util.math.Vec3d;
 
 public class GravityControl extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
     private final Setting<Double> gravityMultiplier = sgGeneral.add(new DoubleSetting.Builder()
         .name("gravity-multiplier")
         .description("Gravity strength. 1.0 = normal, 0.2 = 20% of normal, 4.0 = 4x stronger.")
         .defaultValue(1.0)
         .min(0.2)
         .max(4.0)
         .sliderRange(0.2, 4.0)
         .build()
     );
 
     private static final double VANILLA_GRAVITY = 0.08;
 
     public GravityControl() {
         super(Categories.Misc, "gravity-control", "Modify your gravity.");
     }
 
     @EventHandler
     private void onPlayerMove(PlayerMoveEvent event) {
         if (!mc.player.isAlive()) return;
 
         Vec3d vel = mc.player.getVelocity();
         
         // Get the current Y velocity
         double newY = vel.y;
         
         // Cancel vanilla gravity by adding it back (since it's already been subtracted by vanilla code)
         newY += VANILLA_GRAVITY;
         
         // Apply our custom gravity based on the multiplier
         // This ensures that 0.2 truly means 20% of vanilla gravity
         newY -= VANILLA_GRAVITY * gravityMultiplier.get();
         
         // Set the new velocity without any artificial caps
         mc.player.setVelocity(vel.x, newY, vel.z);
     }
 
     @Override
     public String getInfoString() {
         return String.format("%.1fx", gravityMultiplier.get());
     }
 }
 