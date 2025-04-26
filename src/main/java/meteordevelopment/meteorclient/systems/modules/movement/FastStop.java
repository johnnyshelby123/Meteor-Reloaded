/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.util.math.Vec3d;
 
 public class FastStop extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
     // General settings
     private final Setting<Boolean> workInAir = sgGeneral.add(new BoolSetting.Builder()
         .name("work-in-air")
         .description("Whether to stop movement when in air as well as on ground.")
         .defaultValue(false)
         .build()
     );
 
     private final Setting<Boolean> preserveY = sgGeneral.add(new BoolSetting.Builder()
         .name("preserve-y")
         .description("Preserves vertical momentum when stopping.")
         .defaultValue(true)
         .build()
     );
 
     private boolean wasMoving = false;
     private Vec3d lastVelocity = Vec3d.ZERO;
 
     public FastStop() {
         super(Categories.Movement, "fast-stop", "Instantly stops your movement when you release movement keys.");
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         if (!mc.player.isAlive()) return;
         
         // Skip if not on ground and work-in-air is disabled
         if (!workInAir.get() && !mc.player.isOnGround()) return;
 
         // Check if player is actively moving with keys
         boolean isMoving = mc.options.forwardKey.isPressed() || 
                           mc.options.backKey.isPressed() || 
                           mc.options.leftKey.isPressed() || 
                           mc.options.rightKey.isPressed();
 
         // Store current velocity for reference
         Vec3d currentVelocity = mc.player.getVelocity();
         
         // If player was moving but stopped pressing keys
         if (wasMoving && !isMoving) {
             // Calculate new velocity
             double newX = 0;
             double newZ = 0;
             double newY = preserveY.get() ? currentVelocity.y : 0;
             
             // Apply the new velocity
             mc.player.setVelocity(newX, newY, newZ);
         }
         
         // Update state for next tick
         wasMoving = isMoving;
         lastVelocity = currentVelocity;
     }
 
     @Override
     public String getInfoString() {
         return workInAir.get() ? "Air+Ground" : "Ground";
     }
 }
 