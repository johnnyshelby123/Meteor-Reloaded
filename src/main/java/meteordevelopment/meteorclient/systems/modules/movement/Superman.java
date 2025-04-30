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
 import net.minecraft.util.math.Vec3d;

 public class Superman extends Module {
     private final SettingGroup sgGeneral;
     private final SettingGroup sgFallDamage;

     private final Setting<Double> speed;
     private final Setting<Boolean> preventFallDamage;
     private final Setting<Boolean> alwaysPreventFallDamage;

     private boolean sentElytraPacket;

     public Superman() {
         super(Categories.Movement, "superman", "Fly like Superman.");

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
         if (mc.player == null || !mc.player.isAlive()) {
             return;
         }

         if (!this.sentElytraPacket || mc.player.age % 40 == 0) {
             this.startElytraPose();
         }

         // Calculate movement direction based on pressed keys and look direction
         Vec3d moveInput = Vec3d.ZERO;
         float yaw = mc.player.getYaw();
         float pitch = mc.player.getPitch(); // Get pitch in degrees

         // Forward (W) - Use pitch and yaw
         if (mc.options.forwardKey.isPressed()) {
             moveInput = moveInput.add(Vec3d.fromPolar(pitch, yaw));
         }
         // Backward (S) - Use pitch and yaw, negated
         if (mc.options.backKey.isPressed()) {
             moveInput = moveInput.add(Vec3d.fromPolar(pitch, yaw).negate());
         }
         // Left (A) - Use only yaw
         if (mc.options.leftKey.isPressed()) {
             moveInput = moveInput.add(Vec3d.fromPolar(0, yaw - 90));
         }
         // Right (D) - Use only yaw
         if (mc.options.rightKey.isPressed()) {
             moveInput = moveInput.add(Vec3d.fromPolar(0, yaw + 90));
         }

         // If no keys are pressed, stop movement
         if (moveInput.equals(Vec3d.ZERO)) {
             mc.player.setVelocity(0.0, 0.0, 0.0);
             return;
         }

         // Normalize the combined input vector and apply speed
         Vec3d velocity = moveInput.normalize().multiply(this.speed.get());

         mc.player.setVelocity(velocity.x, velocity.y, velocity.z);
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

         if (this.alwaysPreventFallDamage.get() || (mc.player != null && mc.player.getVelocity().y < -0.5)) {
             ((PlayerMoveC2SPacketAccessor)event.packet).setOnGround(true);
         }
     }

     private void startElytraPose() {
         if (mc.player != null && mc.player.networkHandler != null) {
             mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
             this.sentElytraPacket = true;
         }
     }

     @Override
     public String getInfoString() {
         return String.format("%.1f", this.speed.get());
     }
 }
