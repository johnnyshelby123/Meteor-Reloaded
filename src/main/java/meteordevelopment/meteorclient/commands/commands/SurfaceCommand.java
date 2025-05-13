package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.block.BlockState;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandSource;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class SurfaceCommand extends Command {
    private static final int MAX_SEARCH_RANGE = 256;
    private static final int REQUIRED_AIR_BLOCKS_ABOVE_SURFACE = 3;

    public SurfaceCommand() {
        super("surface", "Teleports you to the first solid block above with 3 air blocks above it.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            ClientPlayerEntity player = mc.player;
            if (player == null) {
                ChatUtils.error("Player not available.");
                return 0;
            }
            World world = mc.world;
            if (world == null) {
                ChatUtils.error("World not available.");
                return 0;
            }

            BlockPos playerPos = player.getBlockPos();
            BlockPos.Mutable currentSearchPos = new BlockPos.Mutable();
            BlockPos targetSurfaceBlock = null;

            int worldTopY = world.getDimension().logicalHeight();

            for (int yOffset = 1; yOffset <= MAX_SEARCH_RANGE; yOffset++) {
                int potentialSurfaceY = playerPos.getY() + yOffset;
                currentSearchPos.set(playerPos.getX(), potentialSurfaceY, playerPos.getZ());

                if (potentialSurfaceY >= (worldTopY - REQUIRED_AIR_BLOCKS_ABOVE_SURFACE)) {
                    break; 
                }

                BlockState blockState = world.getBlockState(currentSearchPos);

                if (!blockState.getCollisionShape(world, currentSearchPos).isEmpty()) {
                    boolean hasEnoughClearance = true;
                    for (int i = 1; i <= REQUIRED_AIR_BLOCKS_ABOVE_SURFACE; i++) {
                        BlockPos clearancePos = currentSearchPos.up(i);
                        if (clearancePos.getY() >= worldTopY) {
                            hasEnoughClearance = false;
                            break;
                        }
                        if (!world.getBlockState(clearancePos).getCollisionShape(world, clearancePos).isEmpty()) {
                            hasEnoughClearance = false;
                            break;
                        }
                    }

                    if (hasEnoughClearance) {
                        targetSurfaceBlock = currentSearchPos.toImmutable();
                        break;
                    }
                }
            }

            if (targetSurfaceBlock != null) {
                double targetX = targetSurfaceBlock.getX() + 0.5;
                double targetY = targetSurfaceBlock.getY() + 1.0;
                double targetZ = targetSurfaceBlock.getZ() + 0.5;

                if (mc.player.networkHandler != null) {
                    mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(targetX, targetY, targetZ, true, player.horizontalCollision));
                    player.setPosition(targetX, targetY, targetZ);
                    ChatUtils.info("Teleported to surface at X: %.1f, Y: %.1f, Z: %.1f", targetX, targetY, targetZ);
                } else {
                    ChatUtils.error("Network handler not available.");
                    return 0;
                }
            } else {
                ChatUtils.error("No suitable surface found above you within %d blocks with %d air blocks above it.", MAX_SEARCH_RANGE, REQUIRED_AIR_BLOCKS_ABOVE_SURFACE);
            }

            return SINGLE_SUCCESS;
        });
    }
}

