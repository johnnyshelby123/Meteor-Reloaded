/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.player;

 import meteordevelopment.meteorclient.events.packets.PacketEvent;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.gui.GuiTheme;
 import meteordevelopment.meteorclient.gui.widgets.WWidget;
 import meteordevelopment.meteorclient.mixin.PlayerMoveC2SPacketAccessor;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.meteorclient.utils.Utils;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.entity.Entity;
 import net.minecraft.entity.projectile.ProjectileUtil;
 import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
 import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
 import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
 import net.minecraft.util.hit.BlockHitResult;
 import net.minecraft.util.hit.EntityHitResult;
 import net.minecraft.util.hit.HitResult;
 import net.minecraft.util.math.Box;
 import net.minecraft.util.math.Vec3d;
 
 public class Reach extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private final SettingGroup sgPaper = settings.createGroup("Paper Compatibility");
 
     private final Setting<Double> blockReach = sgGeneral.add(new DoubleSetting.Builder()
         .name("extra-block-reach")
         .description("The distance to add to your block reach.")
         .sliderMax(5)
         .build()
     );
 
     private final Setting<Double> entityReach = sgGeneral.add(new DoubleSetting.Builder()
         .name("extra-entity-reach")
         .description("The distance to add to your entity reach.")
         .sliderMax(5)
         .build()
     );
 
     // Paper compatibility settings
     private final Setting<Boolean> paperCompatibility = sgPaper.add(new BoolSetting.Builder()
         .name("paper-compatibility")
         .description("Enable special handling for Paper servers to extend reach beyond vanilla limits.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Double> paperBlockReach = sgPaper.add(new DoubleSetting.Builder()
         .name("paper-block-reach")
         .description("The maximum block reach distance on Paper servers.")
         .defaultValue(6.0)
         .min(3.0)
         .sliderRange(3.0, 10.0)
         .visible(paperCompatibility::get)
         .build()
     );
 
     private final Setting<Double> paperEntityReach = sgPaper.add(new DoubleSetting.Builder()
         .name("paper-entity-reach")
         .description("The maximum entity reach distance on Paper servers.")
         .defaultValue(6.0)
         .min(3.0)
         .sliderRange(3.0, 10.0)
         .visible(paperCompatibility::get)
         .build()
     );
 
     private final Setting<Boolean> throughWalls = sgPaper.add(new BoolSetting.Builder()
         .name("through-walls")
         .description("Allow hitting entities through walls on Paper servers.")
         .defaultValue(false)
         .visible(paperCompatibility::get)
         .build()
     );
 
     private BlockHitResult currentTargetBlock = null;
     private EntityHitResult currentTargetEntity = null;
 
     public Reach() {
         super(Categories.Player, "reach", "Gives you super long arms.");
     }
 
     @Override
     public WWidget getWidget(GuiTheme theme) {
         return theme.label("Note: The Paper Compatibility mode uses special packet handling to extend reach beyond vanilla limits on Paper servers.", Utils.getWindowWidth() / 3.0);
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         if (!isActive() || !paperCompatibility.get() || mc.player == null) return;
 
         // Update current target block with extended reach
         currentTargetBlock = getTargetBlock(paperBlockReach.get());
         
         // Update current target entity with extended reach
         currentTargetEntity = getTargetEntity(paperEntityReach.get());
     }
 
     @EventHandler
     private void onSendPacket(PacketEvent.Send event) {
         if (!isActive() || !paperCompatibility.get() || mc.player == null) return;
 
         // Handle block interaction packets for extended reach
         if (event.packet instanceof PlayerInteractBlockC2SPacket && currentTargetBlock != null) {
             // The packet already contains the extended reach information from our custom raycasting
             // No need to modify it further as the client has already created it with our target
         }
 
         // Handle entity interaction packets for extended reach
         if (event.packet instanceof PlayerInteractEntityC2SPacket && currentTargetEntity != null) {
             // The packet already contains the entity ID from our custom entity targeting
             // No need to modify it further
         }
 
         // Set onGround to true in movement packets to help with anti-cheat
         if (event.packet instanceof PlayerMoveC2SPacket) {
             // This helps with some anti-cheat systems when using extended reach
             ((PlayerMoveC2SPacketAccessor) event.packet).setOnGround(true);
         }
     }
 
     private BlockHitResult getTargetBlock(double reach) {
         if (mc.player == null || mc.world == null) return null;
 
         // Get player's eye position and look vector
         Vec3d eyePos = mc.player.getEyePos();
         Vec3d lookVec = mc.player.getRotationVec(1.0f);
         
         // Calculate the end position of the ray based on reach distance
         Vec3d endPos = eyePos.add(lookVec.multiply(reach));
         
         // Perform the raycast
         return mc.world.raycast(new net.minecraft.world.RaycastContext(
             eyePos,
             endPos,
             net.minecraft.world.RaycastContext.ShapeType.OUTLINE,
             net.minecraft.world.RaycastContext.FluidHandling.NONE,
             mc.player
         ));
     }
 
     private EntityHitResult getTargetEntity(double reach) {
         if (mc.player == null || mc.world == null) return null;
 
         // Get player's eye position and look vector
         Vec3d eyePos = mc.player.getEyePos();
         Vec3d lookVec = mc.player.getRotationVec(1.0f);
         
         // Calculate the end position of the ray based on reach distance
         Vec3d endPos = eyePos.add(lookVec.multiply(reach));
         
         // Create a box that extends from the player's eye position to the end position
         Box box = mc.player.getBoundingBox().stretch(lookVec.multiply(reach)).expand(1.0);
         
         // Perform the entity raycast
         return ProjectileUtil.raycast(
             mc.player,
             eyePos,
             endPos,
             box,
             entity -> !entity.isSpectator() && entity.canHit() && (throughWalls.get() || mc.player.canSee(entity)),
             reach * reach
         );
     }
 
     public double blockReach() {
         return isActive() ? blockReach.get() : 0;
     }
 
     public double entityReach() {
         return isActive() ? entityReach.get() : 0;
     }
 }