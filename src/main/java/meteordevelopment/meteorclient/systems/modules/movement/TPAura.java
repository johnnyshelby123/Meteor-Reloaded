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
 import net.minecraft.entity.player.PlayerEntity;
 import net.minecraft.util.math.Vec3d;
 import net.minecraft.util.math.BlockPos;

 // Import necessary classes for Speed logic (adjust as needed)
 import meteordevelopment.meteorclient.systems.modules.Modules;
 import meteordevelopment.meteorclient.systems.modules.movement.speed.Speed;
 import meteordevelopment.meteorclient.utils.player.PlayerUtils;
 import net.minecraft.util.math.MathHelper;
import net.minecraft.nbt.NbtCompound; // Added for explicit override

import java.util.ArrayList;
 import java.util.Comparator;
 import java.util.List;
 import java.util.stream.Collectors;

 public class TPAura extends Module {
     // Define the movement mode enum
     public enum MovementMode {
         SetPos, // Existing teleport mode
         Speed   // New mode using Speed logic
     }

     private final SettingGroup sgGeneral = settings.getDefaultGroup();
     private final SettingGroup sgShape = settings.createGroup("Shape");
     private final SettingGroup sgTargeting = settings.createGroup("Targeting");
 
     // General settings
     private final Setting<MovementMode> movementMode = sgGeneral.add(new EnumSetting.Builder<MovementMode>()
         .name("movement-mode")
         .description("The method used for moving around the target.")
         .defaultValue(MovementMode.SetPos)
         .build()
     );

     private final Setting<Double> minRadius = sgGeneral.add(new DoubleSetting.Builder()
         .name("min-radius")
         .description("The minimum distance to maintain from the target player.")
         .defaultValue(2)
         .min(1)
         .max(8)
         .sliderMax(8)
         .build()
     );
 
     private final Setting<Double> maxRadius = sgGeneral.add(new DoubleSetting.Builder()
         .name("max-radius")
         .description("The maximum distance to maintain from the target player.")
         .defaultValue(4)
         .min(1)
         .max(10)
         .sliderMax(10)
         .build()
     );
 
     private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
         .name("speed")
         .description("The speed at which you move around the target player.")
         .defaultValue(5)
         .min(0.1)
         .max(50)
         .sliderMax(50)
         .build()
     );
     
     private final Setting<Integer> moveDelay = sgGeneral.add(new IntSetting.Builder()
         .name("move-delay")
         .description("Delay between teleports in ticks (lower = faster but may cause more lag).")
         .defaultValue(1)
         .min(1)
         .max(10)
         .sliderMax(10)
         .build()
     );

     private final Setting<Double> verticalVariation = sgGeneral.add(new DoubleSetting.Builder()
         .name("vertical-variation")
         .description("Maximum vertical variation for 3D movement (in blocks).")
         .defaultValue(2.0)
         .min(0)
         .max(5)
         .sliderMax(5)
         .build()
     );
 
     // Shape settings
     private final Setting<ShapeMode> shapeMode = sgShape.add(new EnumSetting.Builder<ShapeMode>()
         .name("shape-mode")
         .description("The pattern to follow when teleporting around the target.")
         .defaultValue(ShapeMode.Circle)
         .build()
     );
 
     // Targeting settings
     private final Setting<Integer> maxPlayers = sgTargeting.add(new IntSetting.Builder()
         .name("max-players")
         .description("Maximum number of players to target at once.")
         .defaultValue(1)
         .min(1)
         .max(5)
         .sliderMax(5)
         .build()
     );
 
     private final Setting<Integer> maxDistance = sgTargeting.add(new IntSetting.Builder()
         .name("max-distance")
         .description("The maximum distance to target players.")
         .defaultValue(30)
         .min(5)
         .max(100)
         .sliderMax(100)
         .build()
     );
 
     private final Setting<Integer> targetSwitchInterval = sgTargeting.add(new IntSetting.Builder()
         .name("target-switch-interval")
         .description("Ticks between switching active target (when multiple targets are selected).")
         .defaultValue(10)
         .min(1)
         .max(100)
         .sliderMax(100)
         .visible(() -> maxPlayers.get() > 1)
         .build()
     );
 
     private double angle = 0;
     private List<PlayerEntity> targets = new ArrayList<>();
     private int currentTargetIndex = 0;
     private int switchTicks = 0;
     private int currentShapePoint = 0;
     private double currentRadius = 3;
     private boolean radiusIncreasing = true;
     private int tickCounter = 0;
     private Vec3d lastTargetPos = null;
     private Vec3d targetVelocity = new Vec3d(0, 0, 0);
     private boolean twoPointFront = true; // For 2Point mode
 
     public TPAura() {
         super(Categories.Combat, "tp-aura", "Teleports around target players in various patterns for enhanced PvP.");
     }
 
     @Override
     public void onActivate() {
         targets.clear();
         currentTargetIndex = 0;
         switchTicks = 0;
         angle = 0;
         currentShapePoint = 0;
         currentRadius = minRadius.get();
         radiusIncreasing = true;
         tickCounter = 0;
         lastTargetPos = null;
         targetVelocity = new Vec3d(0, 0, 0);
         twoPointFront = true;
     }
 
     @EventHandler
     private void onTick(TickEvent.Post event) {
         // Tick counter for delay
         tickCounter++;
         if (tickCounter < moveDelay.get()) return;
         tickCounter = 0;
 
         // Find target players if needed
         if (targets.isEmpty()) {
             findTargets();
         } else {
             // Remove invalid targets
             targets.removeIf(player -> !player.isAlive() || mc.player.distanceTo(player) > maxDistance.get());
             
             // If all targets are gone, find new ones
             if (targets.isEmpty()) {
                 findTargets();
             }
         }
 
         if (targets.isEmpty()) return;
 
         // Handle target switching for multiple targets
         if (targets.size() > 1) {
             switchTicks++;
             if (switchTicks >= targetSwitchInterval.get()) {
                 switchTicks = 0;
                 currentTargetIndex = (currentTargetIndex + 1) % targets.size();
             }
         } else {
             currentTargetIndex = 0;
         }
 
         // Get current target
         PlayerEntity currentTarget = targets.get(currentTargetIndex);
         
         // Update target velocity for predictive movement
         if (lastTargetPos != null) {
             Vec3d currentPos = currentTarget.getPos();
             targetVelocity = new Vec3d(
                 currentPos.x - lastTargetPos.x,
                 currentPos.y - lastTargetPos.y,
                 currentPos.z - lastTargetPos.z
             );
         }
         lastTargetPos = currentTarget.getPos();
         
         // Update radius (oscillate between min and max)
         if (radiusIncreasing) {
             currentRadius += 0.15;
             if (currentRadius >= maxRadius.get()) {
                 radiusIncreasing = false;
             }
         } else {
             currentRadius -= 0.15;
             if (currentRadius <= minRadius.get()) {
                 radiusIncreasing = true;
             }
         }
         
         // Calculate position based on target and selected shape
         Vec3d shapePos = calculateShapePosition(currentTarget, currentRadius);
         double x = shapePos.x;
         double y = shapePos.y;
         double z = shapePos.z;
         
         // Apply predictive movement
         if (!targetVelocity.equals(Vec3d.ZERO)) {
             // Predict where the target will be in a few ticks
             double predictionFactor = 2.0; // How many ticks ahead to predict
             x += targetVelocity.x * predictionFactor;
             z += targetVelocity.z * predictionFactor;
         }
         
         // Update angle for next tick
         angle += speed.get() * 0.1;
         if (angle > Math.PI * 2) angle -= Math.PI * 2;
         
         // Find safe position based on the calculated shape position (smart positioning removed)
         Vec3d safePos = findSafePosition(new Vec3d(x, y, z), currentTarget.getPos(), currentRadius);
         
         // Randomly decide whether to avoid edges
         if (Math.random() < 0.7) { // 70% chance to avoid edges
             safePos = avoidEdgePosition(safePos);
         }
         
         // Try to move to the new position based on the selected mode
         if (movementMode.get() == MovementMode.SetPos) {
             // Original SetPos logic
             try {
                 mc.player.setPosition(safePos.x, safePos.y, safePos.z);
             } catch (Exception e) {
                 // Skip this move and continue
                 // error("Failed to set position: %s", e.getMessage()); // Optional debug
             }
         } else if (movementMode.get() == MovementMode.Speed) {
             // New Speed logic: Orbital movement towards the safe position
             Vec3d playerPos = mc.player.getPos();
             Vec3d targetPos = currentTarget.getPos(); // Target's current position

             // Vector from target to player
             Vec3d playerRelTarget = playerPos.subtract(targetPos);
             // Vector from target to desired safe position
             Vec3d safePosRelTarget = safePos.subtract(targetPos);

             // Calculate the angle between the player's current position relative to the target
             // and the desired safe position relative to the target.
             double currentAngle = Math.atan2(playerRelTarget.z, playerRelTarget.x);
             double targetAngle = Math.atan2(safePosRelTarget.z, safePosRelTarget.x);

             // Calculate the shortest angle difference (delta angle)
             double deltaAngle = MathHelper.wrapDegrees(Math.toDegrees(targetAngle) - Math.toDegrees(currentAngle));

             // Determine the direction of rotation (clockwise or counter-clockwise)
             double rotationDirection = Math.signum(deltaAngle);
             if (Math.abs(deltaAngle) < 1) rotationDirection = 0; // Avoid jitter if already close to target angle

             // Calculate the tangential velocity direction (perpendicular to the vector from target to player)
             double tangentialAngle = currentAngle + Math.toRadians(90 * rotationDirection);
             Vec3d tangentialDir = new Vec3d(Math.cos(tangentialAngle), 0, Math.sin(tangentialAngle)).normalize();

             // Calculate the radial velocity direction (towards or away from the target to adjust radius)
             double desiredDist = safePosRelTarget.horizontalLength();
             double currentDist = playerRelTarget.horizontalLength();
             Vec3d radialDir = new Vec3d(playerRelTarget.x, 0, playerRelTarget.z).normalize(); // Horizontal direction from target to player
             // Adjust speed based on distance difference, move faster radially if further away from desired radius
             double radialSpeedFactor = MathHelper.clamp((desiredDist - currentDist) * 0.8, -1.0, 1.0);

             // Combine tangential and radial movement
             // Give more weight to tangential movement for orbiting, less to radial adjustment
             double tangentialWeight = 0.9;
             double radialWeight = 0.4; // Increased slightly for better radius correction

             Vec3d combinedDir = tangentialDir.multiply(tangentialWeight).add(radialDir.multiply(radialSpeedFactor * radialWeight));

             // Normalize the combined direction if it's not zero
             if (combinedDir.lengthSquared() > 1e-6) {
                 combinedDir = combinedDir.normalize();
             } else if (tangentialDir.lengthSquared() > 1e-6) {
                 // If radial adjustment cancels tangential, just use tangential
                 combinedDir = tangentialDir;
             } else {
                 // If somehow both are zero, don't move horizontally
                 combinedDir = Vec3d.ZERO;
             }

             // Apply speed
             double speedValue = speed.get() / 20.0; // blocks/tick
             double newVx = combinedDir.x * speedValue;
             double newVz = combinedDir.z * speedValue;

             // Keep current vertical velocity (let gravity handle Y)
             mc.player.setVelocity(newVx, mc.player.getVelocity().y, newVz);
         }
     }
 
     private Vec3d calculateShapePosition(PlayerEntity target, double radius) {
         double x, y, z;
         double verticalOffset = (Math.random() * 2 - 1) * verticalVariation.get();
         
         // Fixed number of points for each shape
         int shapePoints;
         switch (shapeMode.get()) {
             case Circle: shapePoints = 8; break;
             case Triangle: shapePoints = 3; break;
             case Square: shapePoints = 4; break;
             case Pentagon: shapePoints = 5; break;
             case Hexagon: shapePoints = 6; break;
             case Zigzag: shapePoints = 8; break;
             default: shapePoints = 8; break;
         }
         
         switch (shapeMode.get()) {
             case Circle:
                 // Move to next point in circle
                 double circleAngle = (2 * Math.PI * currentShapePoint / shapePoints) + angle;
                 x = target.getX() + radius * Math.cos(circleAngle);
                 z = target.getZ() + radius * Math.sin(circleAngle);
                 y = target.getY() + verticalOffset;
                 break;
                 
             case Triangle:
                 // Move to next point in triangle
                 double triangleAngle = (2 * Math.PI * (currentShapePoint % 3) / 3) + angle;
                 x = target.getX() + radius * Math.cos(triangleAngle);
                 z = target.getZ() + radius * Math.sin(triangleAngle);
                 y = target.getY() + verticalOffset;
                 break;
                 
             case Square:
                 // Move to next point in square
                 double squareAngle = (2 * Math.PI * (currentShapePoint % 4) / 4) + angle;
                 x = target.getX() + radius * Math.cos(squareAngle);
                 z = target.getZ() + radius * Math.sin(squareAngle);
                 y = target.getY() + verticalOffset;
                 break;
                 
             case Pentagon:
                 // Move to next point in pentagon
                 double pentagonAngle = (2 * Math.PI * (currentShapePoint % 5) / 5) + angle;
                 x = target.getX() + radius * Math.cos(pentagonAngle);
                 z = target.getZ() + radius * Math.sin(pentagonAngle);
                 y = target.getY() + verticalOffset;
                 break;
                 
             case Hexagon:
                 // Move to next point in hexagon
                 double hexagonAngle = (2 * Math.PI * (currentShapePoint % 6) / 6) + angle;
                 x = target.getX() + radius * Math.cos(hexagonAngle);
                 z = target.getZ() + radius * Math.sin(hexagonAngle);
                 y = target.getY() + verticalOffset;
                 break;
                 
             case Behind:
                 // Stay behind the player with some variation
                 float yaw = target.getYaw();
                 double radYaw = Math.toRadians(yaw + 180);
                 x = target.getX() - Math.sin(radYaw) * radius;
                 z = target.getZ() + Math.cos(radYaw) * radius;
                 y = target.getY() + verticalOffset;
                 break;
                 
             case Zigzag:
                 // Zigzag pattern around the target
                 double zigzagAngle = angle;
                 double zigzagAmplitude = radius * 0.5;
                 double zigzagPhase = currentShapePoint * (Math.PI / (shapePoints / 2.0));
                 double zigzagOffset = Math.sin(zigzagPhase) * zigzagAmplitude;
                 
                 x = target.getX() + (radius + zigzagOffset) * Math.cos(zigzagAngle);
                 z = target.getZ() + (radius + zigzagOffset) * Math.sin(zigzagAngle);
                 y = target.getY() + verticalOffset;
                 break;
                 
             case TwoPoint:
                 // Toggle between front and back of player
                 float targetYaw = target.getYaw();
                 double twoPointAngle;
                 
                 if (twoPointFront) {
                     // In front of player
                     twoPointAngle = Math.toRadians(targetYaw);
                 } else {
                     // Behind player
                     twoPointAngle = Math.toRadians(targetYaw + 180);
                 }
                 
                 x = target.getX() - Math.sin(twoPointAngle) * radius;
                 z = target.getZ() + Math.cos(twoPointAngle) * radius;
                 y = target.getY() + verticalOffset;
                 
                 // Toggle for next time
                 twoPointFront = !twoPointFront;
                 break;
                 
             case Random:
             default:
                 // Random position within radius
                 double randomAngle = Math.random() * Math.PI * 2;
                 x = target.getX() + radius * Math.cos(randomAngle);
                 z = target.getZ() + radius * Math.sin(randomAngle);
                 y = target.getY() + verticalOffset;
                 break;
         }
         
         // Update shape point for next tick
         currentShapePoint = (currentShapePoint + 1) % shapePoints;
         
         return new Vec3d(x, y, z);
     }
 
     private void findTargets() {
         // Find new targets
         List<PlayerEntity> newTargets = mc.world.getPlayers()
             .stream()
             .filter(player -> player != mc.player)
             .filter(player -> !player.isSpectator())
             .filter(player -> player.isAlive())
             .filter(player -> mc.player.distanceTo(player) <= maxDistance.get())
             .sorted(Comparator.comparingDouble(mc.player::distanceTo))
             .limit(maxPlayers.get())
             .collect(Collectors.toList());
         
         targets = new ArrayList<>(newTargets);
         
         // Reset target index
         currentTargetIndex = 0;
         switchTicks = 0;
         
         // Notify user
         if (!targets.isEmpty()) {
             if (targets.size() == 1) {
                 info("Targeting " + targets.get(0).getName().getString());
             } else {
                 info("Targeting " + targets.size() + " players");
             }
         } else {
             error("No valid targets found!");
             toggle();
         }
     }
 
     private Vec3d findSafePosition(Vec3d proposed, Vec3d targetPos, double desiredRadius) {
         // Check if proposed position is safe and respects minRadius
         double distToTarget = proposed.distanceTo(targetPos);
         if (isSafePosition(proposed.x, proposed.y, proposed.z) && distToTarget >= minRadius.get()) {
             return proposed;
         }

         // If not safe or too close, search for a safe position *around* the desired radius
         // Start searching from minRadius outwards
         double searchRadiusStep = 0.25; // Smaller steps for finer control
         double maxSearchRadius = maxRadius.get() + 1.0; // Search slightly beyond max radius if needed

         for (double r = minRadius.get(); r <= maxSearchRadius; r += searchRadiusStep) {
             // Check more angles around the target
             for (double theta = 0; theta < Math.PI * 2; theta += Math.PI / 12) { // Increased angle resolution
                 // Check vertically around the proposed Y, prioritizing it
                 for (double yOffset = 0; yOffset <= verticalVariation.get(); yOffset += 0.5) {
                     // Check both above and below the proposed Y
                     double checkY1 = proposed.y + yOffset;
                     double checkY2 = proposed.y - yOffset;

                     double x = targetPos.x + r * Math.cos(theta);
                     double z = targetPos.z + r * Math.sin(theta);

                     // Check position above
                     if (isSafePosition(x, checkY1, z)) {
                         return new Vec3d(x, checkY1, z);
                     }
                     // Check position below (if different from above)
                     if (yOffset != 0 && isSafePosition(x, checkY2, z)) {
                         return new Vec3d(x, checkY2, z);
                     }
                 }
             }
         }

         // Fallback: If no safe position found respecting minRadius, try the original proposed Y at a safe XZ slightly outside minRadius
         double fallbackRadius = minRadius.get() + 0.1;
         double fallbackAngle = Math.atan2(proposed.z - targetPos.z, proposed.x - targetPos.x);
         double fallbackX = targetPos.x + fallbackRadius * Math.cos(fallbackAngle);
         double fallbackZ = targetPos.z + fallbackRadius * Math.sin(fallbackAngle);
         if (isSafePosition(fallbackX, proposed.y, fallbackZ)) {
             return new Vec3d(fallbackX, proposed.y, fallbackZ);
         }

         // Ultimate fallback: return original proposed position if nothing else works
         // This might still violate minRadius, but it's better than getting stuck
         warning("Could not find safe position respecting minRadius, using original proposed position.");
         return proposed;
     }
 
     private Vec3d avoidEdgePosition(Vec3d pos) {
         // Check if there's a block to stand on
         BlockPos blockPos = new BlockPos((int)Math.floor(pos.x), (int)Math.floor(pos.y) - 1, (int)Math.floor(pos.z));
         if (mc.world.getBlockState(blockPos).isAir()) {
             // No block below, try to find a safe position nearby
             for (int xOffset = -1; xOffset <= 1; xOffset++) {
                 for (int zOffset = -1; zOffset <= 1; zOffset++) {
                     if (xOffset == 0 && zOffset == 0) continue;
                     
                     BlockPos checkPos = new BlockPos(
                         (int)Math.floor(pos.x) + xOffset,
                         (int)Math.floor(pos.y) - 1,
                         (int)Math.floor(pos.z) + zOffset
                     );
                     
                     if (!mc.world.getBlockState(checkPos).isAir()) {
                         // Found a safe block to stand on
                         return new Vec3d(
                             Math.floor(pos.x) + xOffset + 0.5,
                             pos.y,
                             Math.floor(pos.z) + zOffset + 0.5
                         );
                     }
                 }
             }
         }
         
         return pos; // Return original if no edge detected or no safe alternative found
     }
 
     private boolean isWithinRadius(double x1, double y1, double z1, double x2, double y2, double z2, double radius) {
         double dx = x1 - x2;
         double dy = y1 - y2;
         double dz = z1 - z2;
         return Math.sqrt(dx * dx + dy * dy + dz * dz) <= radius;
     }
 
     private double findSafeY(double x, double z, double targetY) {
         // Start from target's Y position and look for a safe spot
         double y = targetY;
         
         // Check if current position is safe
         if (isSafePosition(x, y, z)) return y;
         
         // Look up to 5 blocks up for a safe position
         for (int i = 1; i <= 5; i++) {
             if (isSafePosition(x, y + i, z)) return y + i;
         }
         
         // Look up to 5 blocks down for a safe position
         for (int i = 1; i <= 5; i++) {
             if (isSafePosition(x, y - i, z)) return y - i;
         }
         
         // If no safe position found, return original Y
         return y;
     }
 
     private boolean isSafePosition(double x, double y, double z) {
         // Enhanced safety check - ensure there's enough space for the player
         return mc.world.getBlockState(new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z))).isAir() &&
                mc.world.getBlockState(new BlockPos((int)Math.floor(x), (int)Math.floor(y + 1), (int)Math.floor(z))).isAir() &&
                // Check surrounding blocks to ensure we're not too close to walls
                mc.world.getBlockState(new BlockPos((int)Math.floor(x) + 1, (int)Math.floor(y), (int)Math.floor(z))).isAir() &&
                mc.world.getBlockState(new BlockPos((int)Math.floor(x) - 1, (int)Math.floor(y), (int)Math.floor(z))).isAir() &&
                mc.world.getBlockState(new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z) + 1)).isAir() &&
                mc.world.getBlockState(new BlockPos((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z) - 1)).isAir();
     }
 
     @Override
     public String getInfoString() {
         if (targets.isEmpty()) return null;
         if (targets.size() == 1) {
             return targets.get(0).getName().getString();
         } else {
             return targets.size() + " players";
         }
     }
     
     public enum ShapeMode {
         Circle,
         Triangle,
         Square,
         Pentagon,
         Hexagon,
         Behind,
         Zigzag,
         TwoPoint,
         Random
     }

     // Explicitly override serialization methods to resolve potential inheritance conflicts
     @Override
     public NbtCompound toTag() {
         return super.toTag();
     }

     @Override
     public Module fromTag(NbtCompound tag) {
         return super.fromTag(tag);
     }
 }
