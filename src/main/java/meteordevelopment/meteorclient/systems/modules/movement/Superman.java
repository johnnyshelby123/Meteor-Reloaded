/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
 import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
 import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
 import meteordevelopment.meteorclient.mixininterface.IPlayerMoveC2SPacket;
 import meteordevelopment.meteorclient.events.packets.PacketEvent;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.util.math.MathHelper;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.BoolSetting;
 import meteordevelopment.meteorclient.settings.DoubleSetting;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.settings.Setting;
 import meteordevelopment.meteorclient.settings.SettingGroup;
 import meteordevelopment.meteorclient.systems.modules.Module;
 
 public class Superman extends Module {
     private final SettingGroup sgGeneral;
     private final SettingGroup sgFallDamage;
     
     private final Setting<Double> speed;
     private final Setting<Boolean> preventFallDamage;
     private final Setting<Boolean> alwaysPreventFallDamage;
     
     private boolean sentElytraPacket;
     
     public Superman() {
         super(Categories.Movement, "superman", "Fly in the direction you're looking like Superman.");
         
         this.sgGeneral = this.settings.getDefaultGroup();
         this.sgFallDamage = this.settings.createGroup("Fall Damage");
         
         this.speed = this.sgGeneral.add(new DoubleSetting.Builder()
             .name("speed")
             .description("Your flying speed.")
             .defaultValue(1.0)
             .min(0.1)
             .max(35.0)
             .sliderMin(0.1)
             .sliderMax(35.0)
             .build());
             
         this.preventFallDamage = this.sgFallDamage.add(new BoolSetting.Builder()
             .name("prevent-fall-damage")
             .description("Prevents fall damage when using Superman.")
             .defaultValue(true)
             .build());
             
         this.alwaysPreventFallDamage = this.sgFallDamage.add(new BoolSetting.Builder()
             .name("always-prevent")
             .description("Always prevents fall damage, even when nose diving.")
             .defaultValue(true)
             .visible(preventFallDamage::get)
             .build());
             
         this.sentElytraPacket = false;
     }
     
     @Override
     public void onActivate() {
         this.startElytraPose();
     }
     
     @Override
     public void onDeactivate() {
         this.sentElytraPacket = false;
     }
     
     @EventHandler
     private void onTick(final TickEvent.Pre event) {
         if (!this.mc.player.isAlive()) {
             return;
         }
         
         if (!this.sentElytraPacket || this.mc.player.age % 40 == 0) {
             this.startElytraPose();
         }
         
         final boolean isMoving = this.mc.options.forwardKey.isPressed() || 
                                this.mc.options.backKey.isPressed() || 
                                this.mc.options.leftKey.isPressed() || 
                                this.mc.options.rightKey.isPressed();
                                
         if (!isMoving) {
             this.mc.player.setVelocity(0.0, 0.0, 0.0);
             return;
         }
         
         final float yaw = this.mc.player.getYaw();
         final float pitch = this.mc.player.getPitch();
         final float yawRadians = yaw * 0.017453292f;
         final float pitchRadians = pitch * 0.017453292f;
         
         double motionX = -MathHelper.sin(yawRadians) * MathHelper.cos(pitchRadians);
         double motionY = -MathHelper.sin(pitchRadians);
         double motionZ = MathHelper.cos(yawRadians) * MathHelper.cos(pitchRadians);
         
         final double length = Math.sqrt(motionX * motionX + motionY * motionY + motionZ * motionZ);
         motionX /= length;
         motionY /= length;
         motionZ /= length;
         
         final double effectiveSpeed = this.speed.get();
         motionX *= effectiveSpeed;
         motionY *= effectiveSpeed;
         motionZ *= effectiveSpeed;
         
         this.mc.player.setVelocity(motionX, motionY, motionZ);
     }
     
     @EventHandler
     private void onSendPacket(final PacketEvent.Send event) {
         if (!this.preventFallDamage.get()) {
             return;
         }
         
         if (!(event.packet instanceof PlayerMoveC2SPacket)) {
             return;
         }
         
         if (((IPlayerMoveC2SPacket)event.packet).meteor$getTag() == 1337) {
             return;
         }
         
         if (this.alwaysPreventFallDamage.get() || this.mc.player.getVelocity().y < -0.5) {
             ((PlayerMoveC2SPacketAccessor)event.packet).setOnGround(true);
         }
     }
     
     private void startElytraPose() {
         this.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
         this.sentElytraPacket = true;
     }
     
     @Override
     public String getInfoString() {
         return String.format("%.1f", this.speed.get());
     }
 }
 