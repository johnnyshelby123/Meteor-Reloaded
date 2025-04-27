/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.player;

 import net.minecraft.entity.effect.StatusEffectInstance;
 import net.minecraft.entity.effect.StatusEffects;
 import net.minecraft.entity.Entity;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.events.entity.player.SendMovementPacketsEvent;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.network.packet.Packet;
 import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
 import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
 import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
 import meteordevelopment.meteorclient.events.packets.PacketEvent;
 import meteordevelopment.meteorclient.settings.DoubleSetting;
 import meteordevelopment.meteorclient.settings.BoolSetting;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.settings.Setting;
 import meteordevelopment.meteorclient.settings.SettingGroup;
 import meteordevelopment.meteorclient.systems.modules.Module;
 
 public class AntiHunger extends Module {
     private final SettingGroup sgGeneral;
     private final SettingGroup sgIntensity;
     
     private final Setting<Boolean> sprint;
     private final Setting<Boolean> onGround;
     private final Setting<Double> hungerIntensity;
     private final Setting<Boolean> enableHungerControl;
     private final Setting<Boolean> showHungerEffects;
     
     private boolean lastOnGround;
     private boolean ignorePacket;
     private int hungerTimer;
     
     public AntiHunger() {
         super(Categories.Player, "anti-hunger", "Controls hunger consumption rate. Positive values reduce hunger, negative values increase it.");
         
         this.sgGeneral = this.settings.getDefaultGroup();
         this.sgIntensity = this.settings.createGroup("Hunger Intensity");
         
         this.sprint = this.sgGeneral.add(new BoolSetting.Builder()
             .name("sprint")
             .description("Spoofs sprinting packets.")
             .defaultValue(true)
             .build()
         );
         
         this.onGround = this.sgGeneral.add(new BoolSetting.Builder()
             .name("on-ground")
             .description("Spoofs the onGround flag.")
             .defaultValue(true)
             .build()
         );
         
         this.hungerIntensity = this.sgIntensity.add(new DoubleSetting.Builder()
             .name("hunger-intensity")
             .description("Controls hunger rate. Positive values reduce hunger, negative values increase it.")
             .defaultValue(1.0)
             .range(-5.0, 5.0)
             .sliderRange(-5.0, 5.0)
             .build()
         );
         
         this.enableHungerControl = this.sgIntensity.add(new BoolSetting.Builder()
             .name("enable-hunger-control")
             .description("Enables the hunger intensity control feature.")
             .defaultValue(true)
             .build()
         );
         
         this.showHungerEffects = this.sgIntensity.add(new BoolSetting.Builder()
             .name("show-hunger-effects")
             .description("Shows hunger/saturation effects visually.")
             .defaultValue(false)
             .build()
         );
         
         this.hungerTimer = 0;
     }
     
     @Override
     public void onActivate() {
         this.lastOnGround = this.mc.player.isOnGround();
         this.hungerTimer = 0;
     }
     
     @EventHandler
     private void onSendPacket(final PacketEvent.Send event) {
         if (!this.enableHungerControl.get() || this.hungerIntensity.get() <= 0.0) {
             return;
         }
         
         if (this.ignorePacket && event.packet instanceof PlayerMoveC2SPacket) {
             this.ignorePacket = false;
             return;
         }
         
         if (this.mc.player.isUsingItem() || this.mc.player.isTouchingWater() || this.mc.player.isRiding()) {
             return;
         }
         
         if (event.packet instanceof ClientCommandC2SPacket packet) {
             if (this.sprint.get() && packet.getMode() == ClientCommandC2SPacket.Mode.START_SPRINTING) {
                 event.cancel();
             }
         }
         
         if (event.packet instanceof PlayerMoveC2SPacket packet) {
             if (this.onGround.get() && this.mc.player.isOnGround() && this.mc.player.fallDistance <= 0.0 && !this.mc.options.jumpKey.isPressed()) {
                 ((PlayerMoveC2SPacketAccessor)packet).setOnGround(false);
             }
         }
     }
     
     @EventHandler
     private void onTick(final SendMovementPacketsEvent.Pre event) {
         if (this.enableHungerControl.get() && this.hungerIntensity.get() > 0.0 && this.mc.player.isOnGround() && !this.lastOnGround && this.onGround.get()) {
             this.ignorePacket = true;
         }
         
         this.lastOnGround = this.mc.player.isOnGround();
     }
     
     @EventHandler
     private void onTickEvent(final TickEvent.Post event) {
         if (!this.enableHungerControl.get() || this.mc.player == null) {
             return;
         }
         
         if (this.hungerIntensity.get() < 0.0) {
             ++this.hungerTimer;
             final int threshold = (int)Math.max(5.0, 20.0 / Math.abs(this.hungerIntensity.get()));
             
             if (this.hungerTimer >= threshold) {
                 this.hungerTimer = 0;
                 
                 if (this.mc.player.isAlive()) {
                     if (this.mc.player.isSprinting()) {
                         this.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.STOP_SPRINTING));
                         this.mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(this.mc.player, ClientCommandC2SPacket.Mode.START_SPRINTING));
                     }
                     
                     if (this.hungerIntensity.get() <= -3.0 && this.showHungerEffects.get()) {
                         final int amplifier = (int)Math.min(2.0, Math.abs(this.hungerIntensity.get()) - 3.0);
                         this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 40, amplifier, false, false, false));
                     }
                 }
             }
         }
         else if (this.hungerIntensity.get() >= 3.0 && this.showHungerEffects.get()) {
             ++this.hungerTimer;
             
             if (this.hungerTimer >= 100) {
                 this.hungerTimer = 0;
                 final int amplifier = (int)Math.min(1.0, this.hungerIntensity.get() - 3.0);
                 this.mc.player.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 60, amplifier, false, false, false));
             }
         }
     }
     
     @Override
     public String getInfoString() {
         if (!this.enableHungerControl.get()) {
             return "Disabled";
         }
         return String.format("%.1f", this.hungerIntensity.get());
     }
 }
 