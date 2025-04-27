/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import meteordevelopment.orbit.EventHandler;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.BoolSetting;
 import meteordevelopment.meteorclient.settings.DoubleSetting;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import net.minecraft.util.math.Vec3d;
 import meteordevelopment.meteorclient.settings.Setting;
 import meteordevelopment.meteorclient.settings.SettingGroup;
 import meteordevelopment.meteorclient.systems.modules.Module;
 
 public class BouncyBoots extends Module {
     private final SettingGroup sgGeneral;
     
     private final Setting<Double> bounceStrength;
     private final Setting<Double> horizontalBoost;
     private final Setting<Boolean> preserveOriginalJump;
     private final Setting<Boolean> bounceOnWalls;
     private final Setting<Double> wallBounceStrength;
     
     private boolean wasOnGroundPreTick;
     private Vec3d lastVelocityPreTick;
     private boolean wasCollidingHorizontallyPostTick;
     
     public BouncyBoots() {
         super(Categories.Movement, "bouncy-boots", "Makes you bounce when landing on any block.");
         
         this.sgGeneral = this.settings.getDefaultGroup();
         
         this.bounceStrength = this.sgGeneral.add(new DoubleSetting.Builder()
             .name("bounce-strength")
             .description("How much bounce to apply when landing on any block, as a percentage of fall height.")
             .defaultValue(0.8)
             .min(0.1)
             .max(2.0)
             .sliderRange(0.1, 2.0)
             .build()
         );
         
         this.horizontalBoost = this.sgGeneral.add(new DoubleSetting.Builder()
             .name("horizontal-boost")
             .description("How much to maintain horizontal momentum when bouncing.")
             .defaultValue(0.9)
             .min(0.0)
             .max(1.0)
             .sliderRange(0.0, 1.0)
             .build()
         );
         
         this.preserveOriginalJump = this.sgGeneral.add(new BoolSetting.Builder()
             .name("preserve-original-jump")
             .description("Tries to allow normal jumping without bouncing (experimental).")
             .defaultValue(true)
             .build()
         );
         
         this.bounceOnWalls = this.sgGeneral.add(new BoolSetting.Builder()
             .name("bounce-on-walls")
             .description("Bounce horizontally when hitting walls.")
             .defaultValue(true)
             .build()
         );
         
         this.wallBounceStrength = this.sgGeneral.add(new DoubleSetting.Builder()
             .name("wall-bounce-strength")
             .description("How much bounce to apply when hitting walls.")
             .defaultValue(0.6)
             .min(0.1)
             .max(1.5)
             .sliderRange(0.1, 1.5)
             .visible(bounceOnWalls::get)
             .build()
         );
         
         this.wasOnGroundPreTick = false;
         this.lastVelocityPreTick = Vec3d.ZERO;
         this.wasCollidingHorizontallyPostTick = false;
     }
     
     @Override
     public void onActivate() {
         if (this.mc.player != null) {
             this.wasOnGroundPreTick = this.mc.player.isOnGround();
             this.lastVelocityPreTick = this.mc.player.getVelocity();
             this.wasCollidingHorizontallyPostTick = this.mc.player.horizontalCollision;
         } else {
             this.wasOnGroundPreTick = false;
             this.lastVelocityPreTick = Vec3d.ZERO;
             this.wasCollidingHorizontallyPostTick = false;
         }
     }
     
     @EventHandler
     private void onTickPre(final TickEvent.Pre event) {
         if (this.mc.player == null) {
             return;
         }
         
         this.wasOnGroundPreTick = this.mc.player.isOnGround();
         this.lastVelocityPreTick = this.mc.player.getVelocity();
     }
     
     @EventHandler
     private void onTickPost(final TickEvent.Post event) {
         if (this.mc.player == null) {
             return;
         }
         
         // Handle vertical bouncing when landing
         if (!this.wasOnGroundPreTick && this.mc.player.isOnGround()) {
             final boolean tryingToJump = this.mc.options.jumpKey.isPressed();
             
             if (!tryingToJump || !this.preserveOriginalJump.get()) {
                 final double bounceY = Math.abs(this.lastVelocityPreTick.y) * this.bounceStrength.get();
                 final double bounceX = this.lastVelocityPreTick.x * this.horizontalBoost.get();
                 final double bounceZ = this.lastVelocityPreTick.z * this.horizontalBoost.get();
                 
                 if (this.lastVelocityPreTick.y < -0.1) {
                     this.mc.player.setVelocity(bounceX, bounceY, bounceZ);
                 }
             }
         }
         
         // Handle horizontal bouncing on walls
         if (this.bounceOnWalls.get() && !this.wasCollidingHorizontallyPostTick && this.mc.player.horizontalCollision) {
             final Vec3d currentVelocity = this.mc.player.getVelocity();
             double newX = currentVelocity.x;
             double newZ = currentVelocity.z;
             boolean bounced = false;
             
             if (Math.abs(currentVelocity.x) > 0.05 && this.mc.player.horizontalCollision) {
                 newX = -currentVelocity.x * this.wallBounceStrength.get();
                 bounced = true;
             }
             
             if (Math.abs(currentVelocity.z) > 0.05 && this.mc.player.horizontalCollision) {
                 newZ = -currentVelocity.z * this.wallBounceStrength.get();
                 bounced = true;
             }
             
             if (bounced) {
                 this.mc.player.setVelocity(newX, currentVelocity.y, newZ);
             }
         }
         
         this.wasCollidingHorizontallyPostTick = this.mc.player.horizontalCollision;
     }
     
     @Override
     public String getInfoString() {
         return String.format("%.1fx", this.bounceStrength.get());
     }
 }
 