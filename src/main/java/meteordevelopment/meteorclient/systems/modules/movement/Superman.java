/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import meteordevelopment.meteorclient.events.packets.PacketEvent;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
 import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
 import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket.Mode;
 import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
 import net.minecraft.util.math.MathHelper;
 
 public class Superman extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private final SettingGroup sgFallDamage = settings.createGroup("Fall Damage");
 
     private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
         .name("speed")
         .description("Your flying speed.")
         .defaultValue(1.0)
         .min(0.1)
         .max(10.0)
         .sliderMin(0.1)
         .sliderMax(5.0)
         .build()
     );
 
     private final Setting<Boolean> preventFallDamage = sgFallDamage.add(new BoolSetting.Builder()
         .name("prevent-fall-damage")
         .description("Prevents fall damage when using Superman.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Boolean> alwaysPreventFallDamage = sgFallDamage.add(new BoolSetting.Builder()
         .name("always-prevent")
         .description("Always prevents fall damage, even when nose diving.")
         .defaultValue(true)
         .visible(preventFallDamage::get)
         .build()
     );
 
     private boolean sentElytraPacket = false;
 
     public Superman() {
         super(Categories.Movement, "superman", "Fly in the direction you're looking like Superman.");
     }
 
     @Override
     public void onActivate() {
         startElytraPose();
     }
 
     @Override
     public void onDeactivate() {
         sentElytraPacket = false;
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         if (!mc.player.isAlive()) return;
 
         // Maintain elytra flying pose with less frequent updates to reduce packet spam
         if (!sentElytraPacket || mc.player.age % 40 == 0) {
             startElytraPose();
         }
 
         // Check if any movement keys are pressed
         boolean isMoving = mc.options.forwardKey.isPressed() || 
                           mc.options.backKey.isPressed() || 
                           mc.options.leftKey.isPressed() || 
                           mc.options.rightKey.isPressed();
         
         // Only apply movement if keys are pressed
         if (!isMoving) {
             // If no movement keys are pressed, maintain position but don't apply movement
             mc.player.setVelocity(0, 0, 0);
             return;
         }
 
         // Get player's look vector
         float yaw = mc.player.getYaw();
         float pitch = mc.player.getPitch();
         
         // Convert to radians
         float yawRadians = yaw * 0.017453292F;
         float pitchRadians = pitch * 0.017453292F;
         
         // Calculate direction vector
         double motionX = -MathHelper.sin(yawRadians) * MathHelper.cos(pitchRadians);
         double motionY = -MathHelper.sin(pitchRadians);
         double motionZ = MathHelper.cos(yawRadians) * MathHelper.cos(pitchRadians);
         
         // Normalize the vector
         double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
         motionX /= length;
         motionY /= length;
         motionZ /= length;
         
         // Apply speed
         motionX *= speed.get();
         motionY *= speed.get();
         motionZ *= speed.get();
         
         // Set velocity directly without collision check
         mc.player.setVelocity(motionX, motionY, motionZ);
     }
 
     @EventHandler
     private void onSendPacket(PacketEvent.Send event) {
         // Only apply if fall damage prevention is enabled
         if (!preventFallDamage.get()) return;
         
         // Check if the packet is a movement packet
         if (!(event.packet instanceof PlayerMoveC2SPacket)) return;
         
         // Skip packets that are already tagged by other modules
         if (((IPlayerMoveC2SPacket) event.packet).meteor$getTag() == 1337) return;
         
         // If always prevent is enabled, or if player is falling fast
         if (alwaysPreventFallDamage.get() || mc.player.getVelocity().y < -0.5) {
             // Set onGround to true to prevent fall damage
             ((PlayerMoveC2SPacketAccessor) event.packet).setOnGround(true);
         }
     }
 
     private void startElytraPose() {
         mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.START_FALL_FLYING));
         sentElytraPacket = true;
     }
 
     @Override
     public String getInfoString() {
         return String.format("%.1f", speed.get());
     }
 }
 