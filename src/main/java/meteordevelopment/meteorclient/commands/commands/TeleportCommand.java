package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static meteordevelopment.meteorclient.MeteorClient.mc;

public class TeleportCommand extends Command {
    private static final double INITIAL_MAX_RANGE = 10.0;
    private static final double PATHFINDING_MAX_RANGE = 300.0;
    private static final int MAX_TELEPORTS_PER_TICK = 10;
    private static final int PATHFINDING_TIMEOUT_TICKS = 200;
    private static final double WAYPOINT_REACHED_THRESHOLD_SQ = 0.25;
    private static final double HORIZONTAL_STEP_DISTANCE = 6.0;
    private static final double AGGRESSIVE_VCLIP_SEGMENT_MAX = 100.0;
    private static final int MAX_VCLIP_SEGMENTS_PER_DIRECTION = 3;
    private static final double MAX_SAFE_FALL_PER_SEGMENT = 20.0;
    private static final int MAX_VAULT_ATTEMPTS = 3;
    private static final int VAULT_CHECK_HEIGHT_INCREMENT = 5;
    private static final int MAX_VAULT_CHECK_Y = 255;

    private PlayerEntity currentTargetForPath = null;
    private boolean isPathfindingToTarget = false;
    private List<BlockPos> currentWaypoints = null;
    private int currentWaypointIndex = 0;
    private boolean isTeleportingAlongPath = false;
    private int pathfindingTickCounter = 0;
    private int teleportsThisTick = 0;

    private BlockPos pathfindingStartPos = null;
    private BlockPos pathfindingGoalPos = null;

    public TeleportCommand() {
        super("tp", "Teleports you to a player with pathfinding and vertical clipping.", "teleport");
        MeteorClient.EVENT_BUS.subscribe(this);
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("player", StringArgumentType.greedyString()).executes(context -> {
            if (mc.player == null || mc.world == null) {
                ChatUtils.error("Player or world not available.");
                return 0;
            }

            String playerName = StringArgumentType.getString(context, "player");
            PlayerEntity targetPlayer = mc.world.getPlayers().stream()
                .filter(p -> p.getGameProfile().getName().equalsIgnoreCase(playerName))
                .findFirst().orElse(null);

            if (targetPlayer == null) {
                ChatUtils.error("Player '" + playerName + "' not found.");
                resetState();
                return 0;
            }

            if (targetPlayer == mc.player) {
                ChatUtils.error("You cannot teleport to yourself.");
                resetState();
                return 0;
            }

            resetState();
            this.currentTargetForPath = targetPlayer;
            this.pathfindingStartPos = mc.player.getBlockPos();
            this.pathfindingGoalPos = targetPlayer.getBlockPos();
            double distanceToTarget = mc.player.getPos().distanceTo(targetPlayer.getPos());

            boolean directPathClear = isDirectPathClear(mc.player.getPos(), targetPlayer.getPos());

            if (distanceToTarget <= INITIAL_MAX_RANGE && directPathClear) {
                this.currentWaypoints = new ArrayList<>();
                this.currentWaypoints.add(this.pathfindingGoalPos);
                this.isTeleportingAlongPath = true;
                this.currentWaypointIndex = 0;
            } else if (distanceToTarget <= PATHFINDING_MAX_RANGE) {
                if (distanceToTarget <= INITIAL_MAX_RANGE && !directPathClear) {
                    ChatUtils.info("Direct path obstructed. Attempting A*...");
                }
                this.isPathfindingToTarget = true;
                this.pathfindingTickCounter = 0;
                new Thread(() -> {
                    List<BlockPos> path = findPathCustom(this.pathfindingStartPos, this.pathfindingGoalPos);
                    mc.execute(() -> {
                        if (!this.isPathfindingToTarget && !(this.currentWaypoints != null && !this.currentWaypoints.isEmpty())) return;

                        if (path != null && !path.isEmpty()) {
                            this.currentWaypoints = path;
                            if (this.currentWaypoints.isEmpty() || !this.currentWaypoints.get(this.currentWaypoints.size() - 1).equals(this.pathfindingGoalPos)) {
                                this.currentWaypoints.add(this.pathfindingGoalPos);
                            }
                            this.isTeleportingAlongPath = true;
                            this.currentWaypointIndex = 0;
                        } else {
                            // A* failed, try specific fallbacks before general aggressive VClip
                            if (currentTargetForPath != null && isTargetEnclosedWithOpenTop(currentTargetForPath.getBlockPos())) {
                                ChatUtils.info("Target enclosed with open top. Attempting direct VClip...");
                                List<BlockPos> directVClipPath = createDirectDownwardVClipPath(mc.player.getBlockPos(), currentTargetForPath.getBlockPos());
                                if (directVClipPath != null && !directVClipPath.isEmpty()) {
                                    this.currentWaypoints = directVClipPath;
                                    this.isTeleportingAlongPath = true;
                                    this.currentWaypointIndex = 0;
                                    ChatUtils.info("Initiating direct VClip sequence for enclosed target.");
                                } else {
                                    ChatUtils.info("Direct VClip for enclosed target failed. Proceeding to general fallback...");
                                    tryAggressiveVClipFallback(this.pathfindingStartPos, this.pathfindingGoalPos);
                                }
                            } else {
                                ChatUtils.info("A* failed. Trying general VClip fallback...");
                                tryAggressiveVClipFallback(this.pathfindingStartPos, this.pathfindingGoalPos);
                            }
                        }
                        isPathfindingToTarget = false;
                    });
                }).start();
            } else {
                ChatUtils.error("Target is too far (%.1f blocks). Max range for pathfinding is %.1f blocks.", distanceToTarget, PATHFINDING_MAX_RANGE);
                resetState();
                return 0;
            }
            return SINGLE_SUCCESS;
        }));
    }

    private boolean isDirectPathClear(Vec3d start, Vec3d end) {
        if (mc.world == null) return false;
        RaycastContext context = new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player);
        Vec3d hitPos = mc.world.raycast(context).getPos();
        return hitPos.equals(end) || hitPos.squaredDistanceTo(end) < 0.01;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }
        teleportsThisTick = 0;

        if (isPathfindingToTarget) {
            pathfindingTickCounter++;
            if (pathfindingTickCounter > PATHFINDING_TIMEOUT_TICKS) {
                ChatUtils.info("A* timed out. Trying VClip fallback...");
                // Check for enclosed target before general fallback on timeout too
                 if (currentTargetForPath != null && isTargetEnclosedWithOpenTop(currentTargetForPath.getBlockPos())) {
                    ChatUtils.info("Target enclosed with open top. Attempting direct VClip after A* timeout...");
                    List<BlockPos> directVClipPath = createDirectDownwardVClipPath(mc.player.getBlockPos(), currentTargetForPath.getBlockPos());
                    if (directVClipPath != null && !directVClipPath.isEmpty()) {
                        this.currentWaypoints = directVClipPath;
                        this.isTeleportingAlongPath = true;
                        this.currentWaypointIndex = 0;
                        ChatUtils.info("Initiating direct VClip sequence for enclosed target.");
                    } else {
                        ChatUtils.info("Direct VClip for enclosed target failed. Proceeding to general fallback...");
                        tryAggressiveVClipFallback(this.pathfindingStartPos, this.pathfindingGoalPos);
                    }
                } else {
                    tryAggressiveVClipFallback(this.pathfindingStartPos, this.pathfindingGoalPos);
                }
                isPathfindingToTarget = false;
            }
        }

        if (isTeleportingAlongPath) {
            processWaypointTeleportation();
        }
    }

    private boolean isTargetEnclosedWithOpenTop(BlockPos targetPlayerPos) {
        if (mc.world == null) return false;

        if (!isSafeForVClipSegment(targetPlayerPos)) { // Needs 2 blocks clear at target pos feet & head
            return false;
        }

        Direction[] horizontal = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (Direction dir : horizontal) {
            // Check for solid blocks at feet level and head level around the target
            if (mc.world.getBlockState(targetPlayerPos.offset(dir)).getCollisionShape(mc.world, targetPlayerPos.offset(dir)).isEmpty() ||
                mc.world.getBlockState(targetPlayerPos.up(1).offset(dir)).getCollisionShape(mc.world, targetPlayerPos.up(1).offset(dir)).isEmpty()) {
                return false; // Not enclosed horizontally at both levels
            }
        }
        // Optional: Check if a few blocks directly above target are clear for descent, though processWaypoint will handle safety.
        // For now, horizontal enclosure + safe standing spot is the main criteria.
        return true;
    }

    private List<BlockPos> createDirectDownwardVClipPath(BlockPos playerCurrentBlockPos, BlockPos targetPlayerBlockPos) {
        List<BlockPos> path = new ArrayList<>();
        // Intermediate point: Target's XZ, at player's current Y. This aligns XZ before the final drop.
        BlockPos intermediatePos = new BlockPos(targetPlayerBlockPos.getX(), playerCurrentBlockPos.getY(), targetPlayerBlockPos.getZ());

        // Only add intermediate if XZ actually differs, to avoid a static teleport point if already aligned.
        if (Math.abs(playerCurrentBlockPos.getX() - intermediatePos.getX()) > 0 || Math.abs(playerCurrentBlockPos.getZ() - intermediatePos.getZ()) > 0) {
            path.add(intermediatePos);
        }
        path.add(targetPlayerBlockPos); // Final destination for the downward VClip
        return path;
    }

    private void tryAggressiveVClipFallback(BlockPos start, BlockPos goal) {
        if (mc.player == null || mc.world == null || currentTargetForPath == null) {
            ChatUtils.error("State missing for VClip fallback.");
            resetState();
            return;
        }
        BlockPos currentPosition = mc.player.getBlockPos();
        BlockPos effectiveGoal = currentTargetForPath.getBlockPos();

        List<BlockPos> fallbackPath = createAggressiveVClipPath(currentPosition, effectiveGoal, 0);
        if (fallbackPath != null && !fallbackPath.isEmpty()) {
            this.currentWaypoints = fallbackPath;
            this.isTeleportingAlongPath = true;
            this.currentWaypointIndex = 0;
        } else {
            ChatUtils.error("Aggressive VClip fallback failed.");
            resetState();
        }
    }

    private List<BlockPos> createAggressiveVClipPath(BlockPos start, BlockPos goal, int vaultAttempt) {
        List<BlockPos> path = new ArrayList<>();
        if (mc.world == null || mc.player == null || vaultAttempt > MAX_VAULT_ATTEMPTS) return null;

        BlockPos currentPos = start;
        double playerY = mc.player.getY();

        double targetY = goal.getY();
        double deltaY = targetY - playerY;
        if (Math.abs(deltaY) > 0.5) {
            path.addAll(generateVerticalPathSegments(currentPos, playerY, targetY));
            if (!path.isEmpty()) currentPos = path.get(path.size() - 1);
        }
        
        BlockPos horizontalTargetPos = new BlockPos(goal.getX(), currentPos.getY(), goal.getZ());
        if (!currentPos.withY(0).equals(horizontalTargetPos.withY(0))) {
            if (isDirectPathClear(Vec3d.ofCenter(currentPos), Vec3d.ofCenter(horizontalTargetPos))) {
                path.add(horizontalTargetPos);
                currentPos = horizontalTargetPos;
            } else if (vaultAttempt < MAX_VAULT_ATTEMPTS) {
                ChatUtils.info("Horizontal path blocked at Y=%d. Attempting vault (attempt %d)...", currentPos.getY(), vaultAttempt + 1);
                int vaultHeight = currentPos.getY() + VAULT_CHECK_HEIGHT_INCREMENT;
                boolean vaulted = false;
                while(vaultHeight <= MAX_VAULT_CHECK_Y) {
                    BlockPos tempVaultStart = new BlockPos(currentPos.getX(), vaultHeight, currentPos.getZ());
                    BlockPos tempVaultEnd = new BlockPos(horizontalTargetPos.getX(), vaultHeight, horizontalTargetPos.getZ());
                    if(isSafeForVClipSegment(tempVaultStart) && isDirectPathClear(Vec3d.ofCenter(tempVaultStart), Vec3d.ofCenter(tempVaultEnd))){
                        path.addAll(generateVerticalPathSegments(currentPos, currentPos.getY(), vaultHeight));
                        path.add(tempVaultEnd);
                        path.addAll(generateVerticalPathSegments(tempVaultEnd, vaultHeight, goal.getY()));
                        currentPos = path.isEmpty() ? currentPos : path.get(path.size()-1);
                        vaulted = true;
                        break;
                    }
                    vaultHeight += VAULT_CHECK_HEIGHT_INCREMENT;
                }
                if (!vaulted) {
                    ChatUtils.info("Vault attempt failed.");
                    return null;
                }
            } else {
                 ChatUtils.error("Horizontal path blocked, max vault attempts reached.");
                return null;
            }
        }

        if (Math.abs(currentPos.getY() - goal.getY()) > 0.5 || currentPos.getX() != goal.getX() || currentPos.getZ() != goal.getZ()) {
            List<BlockPos> finalVerticalAdjustPath = generateVerticalPathSegments(currentPos, currentPos.getY(), goal.getY());
            path.addAll(finalVerticalAdjustPath);
            if (!finalVerticalAdjustPath.isEmpty()) currentPos = finalVerticalAdjustPath.get(finalVerticalAdjustPath.size()-1);
        }
        
        if (!path.isEmpty() && !path.get(path.size()-1).equals(goal)) {
            path.add(goal);
        }

        return path.isEmpty() && start.equals(goal) ? new ArrayList<>() : (path.isEmpty() ? null : path);
    }

    private List<BlockPos> generateVerticalPathSegments(BlockPos fromPos, double currentY, double toY) {
        List<BlockPos> verticalPath = new ArrayList<>();
        double yDiff = toY - currentY;
        if (Math.abs(yDiff) < 0.5) return verticalPath;

        boolean goingUp = yDiff > 0;
        double remainingY = Math.abs(yDiff);
        int segments = 0;
        BlockPos segmentBasePos = fromPos.withY(MathHelper.floor(currentY));

        while (remainingY > 0.1 && segments < MAX_VCLIP_SEGMENTS_PER_DIRECTION * (goingUp ? 1 : 2)) {
            double stepY = Math.min(remainingY, goingUp ? AGGRESSIVE_VCLIP_SEGMENT_MAX : MAX_SAFE_FALL_PER_SEGMENT);
            currentY += (goingUp ? stepY : -stepY);
            BlockPos nextSegmentPos = new BlockPos(segmentBasePos.getX(), MathHelper.floor(currentY), segmentBasePos.getZ());
            verticalPath.add(nextSegmentPos);
            remainingY -= stepY;
            segments++;
        }
        return verticalPath;
    }

    private boolean isSafeForVClipSegment(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty() &&
               mc.world.getBlockState(pos.up(1)).getCollisionShape(mc.world, pos.up(1)).isEmpty();
    }

    private List<BlockPos> findPathCustom(BlockPos start, BlockPos goal) {
        PriorityQueue<Node> openSet = new PriorityQueue<>(Comparator.comparingDouble(n -> n.fScore));
        Set<BlockPos> closedSet = new HashSet<>();
        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        openSet.add(startNode);
        int iterations = 0;
        int maxIterations = 10000;
        while (!openSet.isEmpty() && iterations < maxIterations) {
            iterations++;
            Node current = openSet.poll();
            if (current.pos.equals(goal) || current.pos.isWithinDistance(goal, 1.5)) {
                return reconstructPath(current);
            }
            closedSet.add(current.pos);
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = current.pos.offset(direction);
                if (closedSet.contains(neighborPos) || !isSafeToTeleport(neighborPos)) continue;
                double tentativeGScore = current.gScore + distanceBetween(current.pos, neighborPos);
                Node neighborNode = new Node(neighborPos, current, tentativeGScore, heuristic(neighborPos, goal));
                boolean inOpenSet = false;
                for (Node nodeInOpen : new ArrayList<>(openSet)) {
                    if (nodeInOpen.pos.equals(neighborPos)) {
                        inOpenSet = true;
                        if (tentativeGScore < nodeInOpen.gScore) {
                            openSet.remove(nodeInOpen);
                            openSet.add(neighborNode);
                        }
                        break;
                    }
                }
                if (!inOpenSet) openSet.add(neighborNode);
            }
        }
        return null;
    }

    private double distanceBetween(BlockPos a, BlockPos b) { return Math.sqrt(a.getSquaredDistance(b)); }
    private double heuristic(BlockPos a, BlockPos b) { return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ()); }

    private List<BlockPos> reconstructPath(Node goalNode) {
        List<BlockPos> path = new ArrayList<>();
        Node current = goalNode;
        while (current != null) {
            path.add(current.pos);
            current = current.parent;
        }
        Collections.reverse(path);
        return path;
    }

    private static class Node {
        BlockPos pos; Node parent; double gScore; double fScore;
        Node(BlockPos pos, Node parent, double gScore, double heuristic) {
            this.pos = pos; this.parent = parent; this.gScore = gScore; this.fScore = gScore + heuristic;
        }
    }

    private void processWaypointTeleportation() {
        if (mc.player == null || mc.world == null || currentWaypoints == null || currentWaypoints.isEmpty() || currentTargetForPath == null) {
            resetState();
            return;
        }

        double distToTargetNow = mc.player.getPos().distanceTo(currentTargetForPath.getPos());
        if (distToTargetNow <= INITIAL_MAX_RANGE && isDirectPathClear(mc.player.getPos(), currentTargetForPath.getPos())) {
            if (currentWaypoints.size() > 1 || !currentWaypoints.get(0).equals(currentTargetForPath.getBlockPos())) {
                this.currentWaypoints.clear();
                this.currentWaypoints.add(currentTargetForPath.getBlockPos());
                this.currentWaypointIndex = 0;
            }
        }

        while (teleportsThisTick < MAX_TELEPORTS_PER_TICK && currentWaypointIndex < currentWaypoints.size()) {
            BlockPos nextWaypointBlock = currentWaypoints.get(currentWaypointIndex);
            Vec3d currentPositionVec = mc.player.getPos();
            Vec3d targetWaypointVec = Vec3d.ofCenter(nextWaypointBlock);

            if (currentWaypointIndex == currentWaypoints.size() - 1) {
                targetWaypointVec = new Vec3d(targetWaypointVec.x, currentTargetForPath.getY(), targetWaypointVec.z);
            }

            Vec3d delta = targetWaypointVec.subtract(currentPositionVec);

            if (delta.lengthSquared() < WAYPOINT_REACHED_THRESHOLD_SQ) {
                mc.player.setPosition(targetWaypointVec.x, targetWaypointVec.y, targetWaypointVec.z);
                currentWaypointIndex++;
                if (currentWaypointIndex >= currentWaypoints.size()) {
                    ChatUtils.info("Teleported to %s (%.1f blocks).", currentTargetForPath.getGameProfile().getName(), (double) pathfindingStartPos.getManhattanDistance(currentTargetForPath.getBlockPos()));
                    resetState();
                    return;
                }
                continue;
            }

            Vec3d teleportToPos;
            boolean isFinalSegmentToPlayer = (currentWaypointIndex == currentWaypoints.size() - 1);

            if (Math.abs(delta.x) < 0.5 && Math.abs(delta.z) < 0.5 && Math.abs(delta.y) > 0.1) {
                double vStep = Math.min(isFinalSegmentToPlayer ? Math.abs(delta.y) : AGGRESSIVE_VCLIP_SEGMENT_MAX, Math.abs(delta.y)) * Math.signum(delta.y);
                if (!isFinalSegmentToPlayer && delta.y < 0) { 
                    vStep = Math.min(MAX_SAFE_FALL_PER_SEGMENT, Math.abs(delta.y)) * Math.signum(delta.y);
                }
                teleportToPos = new Vec3d(currentPositionVec.x, currentPositionVec.y + vStep, currentPositionVec.z);
            } else {
                Vec3d stepVec = delta.normalize().multiply(Math.min(HORIZONTAL_STEP_DISTANCE, delta.length()));
                teleportToPos = currentPositionVec.add(stepVec);
            }

            BlockPos nextTeleportBlockPos = BlockPos.ofFloored(teleportToPos.x, teleportToPos.y, teleportToPos.z);
            boolean useLenientSafety = (currentWaypoints.size() <= (MAX_VCLIP_SEGMENTS_PER_DIRECTION * 2 + 1 + MAX_VAULT_ATTEMPTS * 2 + 1) && currentWaypoints.contains(pathfindingGoalPos)); 
            boolean safe = useLenientSafety ? isSafeForVClipSegment(nextTeleportBlockPos) : isSafeToTeleport(nextTeleportBlockPos);

            if (!safe && !isFinalSegmentToPlayer) {
                ChatUtils.error("Path blocked at %s. Stopping at current location.", nextTeleportBlockPos.toShortString());
                resetState();
                return;
            }

            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(teleportToPos.x, teleportToPos.y, teleportToPos.z, true, mc.player.horizontalCollision));
            mc.player.setPosition(teleportToPos.x, teleportToPos.y, teleportToPos.z);
            teleportsThisTick++;
        }

        if (currentWaypointIndex >= currentWaypoints.size() && isTeleportingAlongPath) {
            resetState();
        }
    }

    private boolean isSafeToTeleport(BlockPos pos) {
        if (mc.world == null) return false;
        return mc.world.getBlockState(pos).getCollisionShape(mc.world, pos).isEmpty() &&
               mc.world.getBlockState(pos.up(1)).getCollisionShape(mc.world, pos.up(1)).isEmpty() &&
               mc.world.getBlockState(pos.up(2)).getCollisionShape(mc.world, pos.up(2)).isEmpty();
    }

    private void resetState() {
        this.currentTargetForPath = null;
        this.isPathfindingToTarget = false;
        this.currentWaypoints = null;
        this.currentWaypointIndex = 0;
        this.isTeleportingAlongPath = false;
        this.pathfindingTickCounter = 0;
        this.teleportsThisTick = 0;
        this.pathfindingStartPos = null;
        this.pathfindingGoalPos = null;
    }
}

