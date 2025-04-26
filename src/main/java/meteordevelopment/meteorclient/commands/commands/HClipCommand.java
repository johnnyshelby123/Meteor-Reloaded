/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.commands.commands;

 import com.mojang.brigadier.arguments.DoubleArgumentType;
 import com.mojang.brigadier.builder.LiteralArgumentBuilder;
 import meteordevelopment.meteorclient.commands.Command;
 import net.minecraft.command.CommandSource;
 import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
 import net.minecraft.network.packet.c2s.play.VehicleMoveC2SPacket;
 import net.minecraft.util.math.Vec3d;
 
 public class HClipCommand extends Command {
     public HClipCommand() {
         super("hclip", "Lets you clip through blocks horizontally.");
     }
 
     @Override
     public void build(LiteralArgumentBuilder<CommandSource> builder) {
         builder.then(argument("blocks", DoubleArgumentType.doubleArg()).executes(context -> {
             double blocks = context.getArgument("blocks", Double.class);
             Vec3d forward = Vec3d.fromPolar(0, mc.player.getYaw()).normalize();
             
             // Calculate the total distance to move
             double distanceX = forward.x * blocks;
             double distanceZ = forward.z * blocks;
             
             // Implementation similar to VClip's "PaperClip" approach
             // Allows clipping through blocks horizontally using packets
             int packetsRequired = (int) Math.ceil(Math.abs(blocks / 10));
             
             if (packetsRequired > 20) {
                 // Limit to 20 packets to avoid kicks
                 packetsRequired = 1;
             }
 
             if (mc.player.hasVehicle()) {
                 // Vehicle version
                 // For each 10 blocks, send a vehicle move packet with no delta
                 for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                     mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
                 }
                 // Now send the final vehicle move packet
                 mc.player.getVehicle().setPosition(
                     mc.player.getVehicle().getX() + distanceX,
                     mc.player.getVehicle().getY(),
                     mc.player.getVehicle().getZ() + distanceZ
                 );
                 mc.player.networkHandler.sendPacket(VehicleMoveC2SPacket.fromVehicle(mc.player.getVehicle()));
             } else {
                 // No vehicle version
                 // For each 10 blocks, send a player move packet with no delta
                 for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
                     mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true, mc.player.horizontalCollision));
                 }
                 // Now send the final player move packet
                 mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                     mc.player.getX() + distanceX,
                     mc.player.getY(),
                     mc.player.getZ() + distanceZ,
                     true,
                     mc.player.horizontalCollision
                 ));
                 mc.player.setPosition(mc.player.getX() + distanceX, mc.player.getY(), mc.player.getZ() + distanceZ);
             }
 
             return SINGLE_SUCCESS;
         }));
     }
 }
 