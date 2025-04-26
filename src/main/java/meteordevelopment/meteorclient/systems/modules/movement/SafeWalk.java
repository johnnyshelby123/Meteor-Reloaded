/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.movement;

 import meteordevelopment.meteorclient.events.entity.player.ClipAtLedgeEvent;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.block.BlockState;
 import net.minecraft.util.math.BlockPos;
 import net.minecraft.util.math.Box;
 import net.minecraft.util.math.Vec3d;
 import net.minecraft.util.shape.VoxelShape;
 
 public class SafeWalk extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private final SettingGroup sgParkour = settings.createGroup("Parkour");
 
     // General settings
     private final Setting<Boolean> ledgeClip = sgGeneral.add(new BoolSetting.Builder()
         .name("ledge-clip")
         .description("Prevents you from walking off blocks.")
         .defaultValue(true)
         .build()
     );
 
     // Parkour settings
     private final Setting<Boolean> parkourHelper = sgParkour.add(new BoolSetting.Builder()
         .name("parkour-helper")
         .description("Helps with parkour by automatically moving you to the top of blocks.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Double> upwardBoost = sgParkour.add(new DoubleSetting.Builder()
         .name("upward-boost")
         .description("How much upward boost to apply when hitting the side of a block.")
         .defaultValue(0.42)
         .min(0.1)
         .max(1.0)
         .sliderMin(0.1)
         .sliderMax(1.0)
         .visible(() -> parkourHelper.get())
         .build()
     );
 
     private final Setting<Double> forwardBoost = sgParkour.add(new DoubleSetting.Builder()
         .name("forward-boost")
         .description("How much forward boost to apply when hitting the side of a block.")
         .defaultValue(0.3)
         .min(0.0)
         .max(0.5)
         .sliderMin(0.0)
         .sliderMax(0.5)
         .visible(() -> parkourHelper.get())
         .build()
     );
 
     private final Setting<Boolean> jumpAssist = sgParkour.add(new BoolSetting.Builder()
         .name("jump-assist")
         .description("Helps you reach blocks when you jump short.")
         .defaultValue(true)
         .visible(() -> parkourHelper.get())
         .build()
     );
 
     private final Setting<Double> jumpAssistRange = sgParkour.add(new DoubleSetting.Builder()
         .name("assist-range")
         .description("How far to check for blocks to assist jumping to.")
         .defaultValue(1.5)
         .min(0.5)
         .max(3.0)
         .sliderMin(0.5)
         .sliderMax(3.0)
         .visible(() -> parkourHelper.get() && jumpAssist.get())
         .build()
     );
 
     private boolean wasColliding = false;
     private int jumpCooldown = 0;
     private boolean wasInAir = false;
     private int airTicks = 0;
     private Vec3d jumpStartPos = null;
 
     public SafeWalk() {
         super(Categories.Movement, "safe-walk", "Prevents you from walking off blocks and helps with parkour.");
     }
 
     @EventHandler
     private void onClipAtLedge(ClipAtLedgeEvent event) {
         if (ledgeClip.get() && !mc.player.isSneaking()) event.setClip(true);
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         if (!parkourHelper.get() || !mc.player.isAlive()) return;
 
         // Decrease jump cooldown
         if (jumpCooldown > 0) jumpCooldown--;
 
         // Get player position and movement
         Vec3d pos = mc.player.getPos();
         Vec3d velocity = mc.player.getVelocity();
         boolean isMoving = Math.abs(velocity.x) > 0.01 || Math.abs(velocity.z) > 0.01;
         
         // Track air state
         boolean isInAir = !mc.player.isOnGround();
         
         // If player just jumped, record position
         if (!wasInAir && isInAir && velocity.y > 0) {
             jumpStartPos = pos;
             airTicks = 0;
         }
         
         // If player is in air, increment counter
         if (isInAir) {
             airTicks++;
         } else {
             // Reset when landing
             airTicks = 0;
             jumpStartPos = null;
         }
         
         // Jump assist logic - help player reach blocks when they jump short
         if (jumpAssist.get() && isInAir && airTicks > 5 && jumpStartPos != null && velocity.y < 0) {
             // Player is falling after a jump
             BlockPos targetPos = findJumpTargetBlock();
             if (targetPos != null) {
                 // Calculate distance to target
                 double distX = (targetPos.getX() + 0.5) - pos.x;
                 double distZ = (targetPos.getZ() + 0.5) - pos.z;
                 double horizDist = Math.sqrt(distX * distX + distZ * distZ);
                 
                 // If close enough horizontally but falling short
                 if (horizDist < 0.7 && pos.y < (targetPos.getY() + 1) && pos.y > targetPos.getY()) {
                     // Apply boost to help reach the block
                     double boostX = distX * 0.1;
                     double boostZ = distZ * 0.1;
                     double boostY = Math.min(0.2, (targetPos.getY() + 1.05) - pos.y);
                     
                     mc.player.setVelocity(
                         velocity.x + boostX,
                         Math.max(velocity.y, boostY),
                         velocity.z + boostZ
                     );
                 }
             }
         }
 
         // Check for block collisions to help with parkour
         boolean isColliding = isCollidingHorizontally();
         
         // If player just started colliding with a block horizontally
         if (isColliding && !wasColliding && isMoving && !mc.player.isOnGround()) {
             // Get the block the player is colliding with
             BlockPos collidingBlockPos = getCollidingBlockPos();
             
             if (collidingBlockPos != null) {
                 // Check if the block above is air (so we can move up to it)
                 BlockPos abovePos = collidingBlockPos.up();
                 if (mc.world.getBlockState(abovePos).isAir()) {
                     // Apply upward boost to help player get on top of the block
                     double newY = collidingBlockPos.getY() + 1.0;
                     
                     // Only teleport if the new position would be higher than current
                     if (newY > mc.player.getY()) {
                         // Calculate forward direction
                         float yaw = mc.player.getYaw() * 0.017453292F; // Convert to radians
                         double motionX = -Math.sin(yaw) * forwardBoost.get();
                         double motionZ = Math.cos(yaw) * forwardBoost.get();
                         
                         // Apply the boost
                         mc.player.setVelocity(motionX, upwardBoost.get(), motionZ);
                     }
                 }
             }
         }
         
         wasColliding = isColliding;
         wasInAir = isInAir;
     }
 
     private boolean isCollidingHorizontally() {
         Box playerBox = mc.player.getBoundingBox();
         
         // Expand the box slightly to detect near collisions
         Box expandedBox = playerBox.expand(0.05, 0, 0.05);
         
         // Check if the expanded box collides with any blocks
         return mc.world.getBlockCollisions(mc.player, expandedBox).iterator().hasNext();
     }
 
     private BlockPos getCollidingBlockPos() {
         Box playerBox = mc.player.getBoundingBox();
         Vec3d playerPos = mc.player.getPos();
         
         // Expand the box slightly to detect near collisions
         Box expandedBox = playerBox.expand(0.1, 0, 0.1);
         
         // Get the direction the player is moving
         float yaw = mc.player.getYaw() * 0.017453292F; // Convert to radians
         double dirX = -Math.sin(yaw);
         double dirZ = Math.cos(yaw);
         
         // Check blocks in the direction of movement
         for (int y = -1; y <= 1; y++) {
             for (int x = -1; x <= 1; x++) {
                 for (int z = -1; z <= 1; z++) {
                     if (x == 0 && y == 0 && z == 0) continue;
                     
                     // Weight the search toward the direction of movement
                     if ((x * dirX + z * dirZ) < 0) continue;
                     
                     BlockPos checkPos = new BlockPos(
                         (int) Math.floor(playerPos.x) + x,
                         (int) Math.floor(playerPos.y) + y,
                         (int) Math.floor(playerPos.z) + z
                     );
                     
                     BlockState state = mc.world.getBlockState(checkPos);
                     if (state.isAir()) continue;
                     
                     VoxelShape shape = state.getCollisionShape(mc.world, checkPos);
                     if (shape.isEmpty()) continue;
                     
                     Box blockBox = shape.getBoundingBox().offset(checkPos);
                     if (expandedBox.intersects(blockBox)) {
                         return checkPos;
                     }
                 }
             }
         }
         
         return null;
     }
 
     private BlockPos findJumpTargetBlock() {
         if (jumpStartPos == null) return null;
         
         Vec3d playerPos = mc.player.getPos();
         Vec3d playerVel = mc.player.getVelocity();
         
         // Get player movement direction
         float yaw = mc.player.getYaw() * 0.017453292F; // Convert to radians
         double dirX = -Math.sin(yaw);
         double dirZ = Math.cos(yaw);
         
         // Calculate jump vector (from jump start to current position)
         double jumpVecX = playerPos.x - jumpStartPos.x;
         double jumpVecZ = playerPos.z - jumpStartPos.z;
         double jumpDist = Math.sqrt(jumpVecX * jumpVecX + jumpVecZ * jumpVecZ);
         
         // Normalize jump vector
         if (jumpDist > 0.01) {
             jumpVecX /= jumpDist;
             jumpVecZ /= jumpDist;
         } else {
             // Use look direction if jump vector is too small
             jumpVecX = dirX;
             jumpVecZ = dirZ;
         }
         
         // Search for potential target blocks
         BlockPos bestTarget = null;
         double bestScore = Double.MAX_VALUE;
         
         double range = jumpAssistRange.get();
         for (int x = -1; x <= 1; x++) {
             for (int z = -1; z <= 1; z++) {
                 for (int y = -1; y <= 1; y++) {
                     if (x == 0 && z == 0) continue; // Skip current block
                     
                     BlockPos checkPos = new BlockPos(
                         (int) Math.floor(playerPos.x + x * range),
                         (int) Math.floor(playerPos.y + y),
                         (int) Math.floor(playerPos.z + z * range)
                     );
                     
                     // Check if this block is solid and has air above it
                     if (!mc.world.getBlockState(checkPos).isAir() && 
                         mc.world.getBlockState(checkPos.up()).isAir() &&
                         mc.world.getBlockState(checkPos.up(2)).isAir()) {
                         
                         // Calculate center of block
                         double blockX = checkPos.getX() + 0.5;
                         double blockZ = checkPos.getZ() + 0.5;
                         
                         // Calculate vector to block
                         double toBlockX = blockX - playerPos.x;
                         double toBlockZ = blockZ - playerPos.z;
                         double blockDist = Math.sqrt(toBlockX * toBlockX + toBlockZ * toBlockZ);
                         
                         // Skip if too far
                         if (blockDist > range) continue;
                         
                         // Calculate alignment with jump direction (dot product)
                         double alignment = (jumpVecX * toBlockX + jumpVecZ * toBlockZ) / blockDist;
                         
                         // Skip blocks that are behind us
                         if (alignment < 0.3) continue;
                         
                         // Calculate score (lower is better)
                         // Prefer blocks that are in the direction of movement and closer
                         double score = blockDist * (2.0 - alignment);
                         
                         if (score < bestScore) {
                             bestScore = score;
                             bestTarget = checkPos;
                         }
                     }
                 }
             }
         }
         
         return bestTarget;
     }
 
     @Override
     public String getInfoString() {
         if (parkourHelper.get()) {
             return "Parkour";
         }
         return null;
     }
 }
 