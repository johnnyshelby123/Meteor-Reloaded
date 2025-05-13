package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.entity.player.PlayerMoveEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixininterface.IVec3d;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.movement.Anchor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
// Removed: import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;

import static meteordevelopment.meteorclient.MeteorClient.mc;

public class StayBehind extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> targetRange = sgGeneral.add(new DoubleSetting.Builder()
        .name("target-range")
        .description("The maximum distance to target a player.")
        .defaultValue(15)
        .min(1)
        .sliderMax(50)
        .build()
    );

    private final Setting<Double> behindDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("behind-distance")
        .description("How far behind the target to stay.")
        .defaultValue(1.0)
        .min(0.5)
        .sliderMax(5)
        .build()
    );

    private final Setting<Double> movementSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("movement-speed")
        .description("How fast to move towards the target position (blocks/tick).")
        .defaultValue(0.5)
        .min(0.1)
        .sliderMax(5)
        .build()
    );

    private final Setting<Boolean> rotate = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate")
        .description("Whether to rotate to face the target (their back).")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> updateDelay = sgGeneral.add(new IntSetting.Builder()
        .name("update-delay")
        .description("The delay in ticks between target position updates (0 for every tick).")
        .defaultValue(0)
        .min(0)
        .sliderMax(20)
        .build()
    );

    private PlayerEntity currentTarget = null;
    private int ticksSinceLastUpdate = 0;
    private Vec3d desiredPositionBehindTarget = null;

    public StayBehind() {
        super(Categories.Combat, "stay-behind", "Continuously moves to stay directly behind a targeted player, facing their back, using velocity.");
    }

    @Override
    public void onActivate() {
        currentTarget = null;
        ticksSinceLastUpdate = 0;
        desiredPositionBehindTarget = null;
    }

    @Override
    public void onDeactivate() {
        currentTarget = null;
        desiredPositionBehindTarget = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;

        if (ticksSinceLastUpdate < updateDelay.get()) {
            ticksSinceLastUpdate++;
            return;
        }
        ticksSinceLastUpdate = 0;

        if (currentTarget == null || !currentTarget.isAlive() || currentTarget.isRemoved() || mc.player.distanceTo(currentTarget) > targetRange.get()) {
            findTarget();
            if (currentTarget == null) {
                desiredPositionBehindTarget = null; 
                return;
            }
        }
        calculateDesiredPositionAndRotation();
    }

    private void findTarget() {
        this.currentTarget = mc.world.getPlayers().stream()
            .filter(player -> player != mc.player && player.isAlive() && !player.isRemoved() && mc.player.distanceTo(player) <= targetRange.get())
            .min(Comparator.comparingDouble(player -> mc.player.distanceTo(player)))
            .orElse(null);

        if (this.currentTarget != null && this.isActive()) {
            // ChatUtils.info("StayBehind: Targeting " + this.currentTarget.getName().getString()); // Optional: for debugging
        }
    }

    private void calculateDesiredPositionAndRotation() {
        if (currentTarget == null || mc.player == null) {
            desiredPositionBehindTarget = null;
            return;
        }

        double distanceToStay = behindDistance.get();
        float targetsCurrentYaw = currentTarget.getYaw();
        Vec3d targetPos = currentTarget.getPos();
        Vec3d targetLookVec = Vec3d.fromPolar(0, targetsCurrentYaw);

        Vec3d calculatedPos = targetPos.subtract(targetLookVec.multiply(distanceToStay));
        this.desiredPositionBehindTarget = new Vec3d(calculatedPos.x, currentTarget.getY(), calculatedPos.z);

        if (rotate.get()) {
            float newPlayerYaw = targetsCurrentYaw; 
            mc.player.setYaw(newPlayerYaw);
            mc.player.setPitch(mc.player.getPitch()); 
        }
    }

    @EventHandler
    private void onPlayerMove(PlayerMoveEvent event) {
        if (mc.player == null || mc.world == null || currentTarget == null || desiredPositionBehindTarget == null) {
            return;
        }

        if (event.type != MovementType.SELF) return;

        Vec3d playerPos = mc.player.getPos();
        Vec3d diff = desiredPositionBehindTarget.subtract(playerPos);
        double actualSpeed = movementSpeed.get(); 

        Vec3d velocity;
        if (diff.lengthSquared() > 1.0E-6) { // Check if not zero to avoid normalizing zero vector and ensure there is a difference
            if (diff.lengthSquared() < actualSpeed * actualSpeed && diff.lengthSquared() > 0) {
                velocity = diff; // If closer than one step, move exactly to the point
            } else {
                velocity = diff.normalize().multiply(actualSpeed);
            }
        } else {
            velocity = Vec3d.ZERO; // Already at or very close to the target position
        }
        
        Anchor anchor = Modules.get().get(Anchor.class);
        if (anchor.isActive() && anchor.controlMovement) {
            ((IVec3d) event.movement).meteor$set(anchor.deltaX, event.movement.y, anchor.deltaZ);
        } else {
            ((IVec3d) event.movement).meteor$set(velocity.x, velocity.y, velocity.z);
        }
    }
}