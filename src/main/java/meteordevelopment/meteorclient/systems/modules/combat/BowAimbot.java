/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.combat;

 import net.minecraft.item.ItemStack;
 import meteordevelopment.meteorclient.utils.entity.EntityUtils;
 import net.minecraft.util.math.Vec3d;
 import net.minecraft.item.BowItem;
 import net.minecraft.item.Items;
 import net.minecraft.item.Item;
 import net.minecraft.entity.passive.PassiveEntity;
 import meteordevelopment.meteorclient.systems.friends.Friends;
 import net.minecraft.entity.player.PlayerEntity;
 import net.minecraft.entity.LivingEntity;
 import meteordevelopment.meteorclient.utils.entity.TargetUtils;
 import meteordevelopment.meteorclient.pathing.PathManagers;
 import meteordevelopment.meteorclient.utils.player.InvUtils;
 import net.minecraft.item.ArrowItem;
 import meteordevelopment.meteorclient.events.render.Render3DEvent;
 import meteordevelopment.orbit.EventHandler;
 import meteordevelopment.meteorclient.utils.player.Rotations;
 import meteordevelopment.meteorclient.utils.player.PlayerUtils;
 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.BoolSetting;
 import meteordevelopment.meteorclient.settings.EnumSetting;
 import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
 import meteordevelopment.meteorclient.settings.DoubleSetting;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import net.minecraft.entity.Entity;
 import meteordevelopment.meteorclient.utils.entity.SortPriority;
 import net.minecraft.entity.EntityType;
 import java.util.Set;
 import meteordevelopment.meteorclient.settings.Setting;
 import meteordevelopment.meteorclient.settings.SettingGroup;
 import meteordevelopment.meteorclient.systems.modules.Module;
 
 public class BowAimbot extends Module {
     private final SettingGroup sgGeneral;
     private final SettingGroup sgCulling;
     
     private final Setting<Double> range;
     private final Setting<Set<EntityType<?>>> entities;
     private final Setting<SortPriority> priority;
     private final Setting<Boolean> babies;
     private final Setting<Boolean> nametagged;
     private final Setting<Boolean> pauseOnCombat;
     private final Setting<Boolean> culling;
     private final Setting<Boolean> keepTarget;
     private final Setting<Boolean> predictMovement;
     private final Setting<Double> trackSpeed;
     
     private boolean wasPathing;
     private Entity target;
     private Entity cullingTarget;
     private boolean priming;
     private long lastTrackTime;
     
     public BowAimbot() {
         super(Categories.Combat, "bow-aimbot", "Automatically aims your bow for you.");
         
         this.sgGeneral = this.settings.getDefaultGroup();
         this.sgCulling = this.settings.createGroup("Culling");
         
         this.range = this.sgGeneral.add(new DoubleSetting.Builder()
             .name("range")
             .description("The maximum range the entity can be to aim at it.")
             .defaultValue(20.0)
             .range(0.0, 100.0)
             .sliderMax(100.0)
             .build()
         );
         
         this.entities = this.sgGeneral.add(new EntityTypeListSetting.Builder()
             .name("entities")
             .description("Entities to attack.")
             .onlyAttackable()
             .build()
         );
         
         this.priority = this.sgGeneral.add(new EnumSetting.Builder<SortPriority>()
             .name("priority")
             .description("What type of entities to target.")
             .defaultValue(SortPriority.LowestHealth)
             .build()
         );
         
         this.babies = this.sgGeneral.add(new BoolSetting.Builder()
             .name("babies")
             .description("Whether or not to attack baby variants of the entity.")
             .defaultValue(true)
             .build()
         );
         
         this.nametagged = this.sgGeneral.add(new BoolSetting.Builder()
             .name("nametagged")
             .description("Whether or not to attack mobs with a name tag.")
             .defaultValue(false)
             .build()
         );
         
         this.pauseOnCombat = this.sgGeneral.add(new BoolSetting.Builder()
             .name("pause-on-combat")
             .description("Freezes Baritone temporarily until you released the bow.")
             .defaultValue(false)
             .build()
         );
         
         this.culling = this.sgCulling.add(new BoolSetting.Builder()
             .name("culling")
             .description("Locks onto the target and rotates to face them when priming the bow.")
             .defaultValue(true)
             .build()
         );
         
         this.keepTarget = this.sgCulling.add(new BoolSetting.Builder()
             .name("keep-target")
             .description("Keeps the same target until the bow is fired or released.")
             .defaultValue(true)
             .visible(culling::get)
             .build()
         );
         
         this.predictMovement = this.sgCulling.add(new BoolSetting.Builder()
             .name("predict-movement")
             .description("Predicts target movement when aiming.")
             .defaultValue(true)
             .visible(culling::get)
             .build()
         );
         
         this.trackSpeed = this.sgCulling.add(new DoubleSetting.Builder()
             .name("track-speed")
             .description("How quickly to track the target when culling is enabled.")
             .defaultValue(2.0)
             .min(0.1)
             .sliderMax(5.0)
             .visible(culling::get)
             .build()
         );
     }
     
     @Override
     public void onDeactivate() {
         this.target = null;
         this.cullingTarget = null;
         this.wasPathing = false;
         this.priming = false;
     }
     
     @EventHandler
     private void onTick(final TickEvent.Pre event) {
         this.priming = (this.mc.options.useKey.isPressed() && this.itemInHand());
         
         if (this.culling.get() && PlayerUtils.isAlive()) {
             if (this.cullingTarget == null || (!this.keepTarget.get() && !this.priming)) {
                 this.cullingTarget = this.findTarget();
             }
             
             if (this.priming && this.cullingTarget != null) {
                 final long currentTime = System.currentTimeMillis();
                 if (currentTime - this.lastTrackTime > 1000.0 / this.trackSpeed.get()) {
                     this.lastTrackTime = currentTime;
                     final float yaw = (float)Rotations.getYaw(this.cullingTarget);
                     final float pitch = (float)Rotations.getPitch(this.cullingTarget);
                     Rotations.rotate(yaw, pitch);
                 }
             }
             
             if (this.cullingTarget != null && !this.isValidTarget(this.cullingTarget)) {
                 this.cullingTarget = null;
             }
         }
     }
     
     @EventHandler
     private void onRender(final Render3DEvent event) {
         if (!PlayerUtils.isAlive() || !this.itemInHand()) {
             return;
         }
         
         if (!this.mc.player.getInventory().containsAny(stack -> stack.getItem() instanceof ArrowItem) && 
             !InvUtils.find(itemStack -> itemStack.getItem() instanceof ArrowItem).found()) {
             return;
         }
         
         if (this.culling.get() && this.cullingTarget != null && this.priming) {
             this.target = this.cullingTarget;
         } else {
             this.target = this.findTarget();
         }
         
         if (this.target == null) {
             if (this.wasPathing) {
                 PathManagers.get().resume();
                 this.wasPathing = false;
             }
             return;
         }
         
         if (this.mc.options.useKey.isPressed() && this.itemInHand()) {
             if (this.pauseOnCombat.get() && PathManagers.get().isPathing() && !this.wasPathing) {
                 PathManagers.get().pause();
                 this.wasPathing = true;
             }
             
             if (!this.culling.get() || this.target != this.cullingTarget) {
                 this.aim(event.tickDelta);
             }
         }
     }
     
     private Entity findTarget() {
         return TargetUtils.get(entity -> this.isValidTarget(entity), this.priority.get());
     }
     
     private boolean isValidTarget(final Entity entity) {
         if (entity == this.mc.player || entity == this.mc.cameraEntity) {
             return false;
         }
         
         if ((entity instanceof LivingEntity && ((LivingEntity)entity).isDead()) || !entity.isAlive()) {
             return false;
         }
         
         if (!PlayerUtils.isWithin(entity, this.range.get())) {
             return false;
         }
         
         if (!this.entities.get().contains(entity.getType())) {
             return false;
         }
         
         if (!this.nametagged.get() && entity.hasCustomName()) {
             return false;
         }
         
         if (!PlayerUtils.canSeeEntity(entity)) {
             return false;
         }
         
         if (entity instanceof PlayerEntity) {
             if (((PlayerEntity)entity).isCreative()) {
                 return false;
             }
             
             if (!Friends.get().shouldAttack((PlayerEntity)entity)) {
                 return false;
             }
         }
         
         return !(entity instanceof PassiveEntity) || this.babies.get() || !((PassiveEntity)entity).isBaby();
     }
     
     private boolean itemInHand() {
         return InvUtils.testInMainHand(Items.BOW, Items.CROSSBOW);
     }
     
     private void aim(final float tickDelta) {
         final float velocity = BowItem.getPullProgress(this.mc.player.getItemUseTime());
         Vec3d pos = this.target.getLerpedPos(tickDelta);
         
         if (this.culling.get() && this.predictMovement.get()) {
             final double velocityX = this.target.getX() - this.target.lastRenderX;
             final double velocityY = this.target.getY() - this.target.lastRenderY;
             final double velocityZ = this.target.getZ() - this.target.lastRenderZ;
             final Vec3d velocity3d = new Vec3d(velocityX, velocityY, velocityZ);
             pos = pos.add(velocity3d.multiply(velocity * 5.0f));
         }
         
         final double relativeX = pos.x - this.mc.player.getX();
         final double relativeY = pos.y + this.target.getStandingEyeHeight() / 2.0f - this.mc.player.getEyeY();
         final double relativeZ = pos.z - this.mc.player.getZ();
         
         final double hDistance = Math.sqrt(relativeX * relativeX + relativeZ * relativeZ);
         final double hDistanceSq = hDistance * hDistance;
         
         final float g = 0.006f;
         final float velocitySq = velocity * velocity;
         
         final float pitch = (float)(-Math.toDegrees(Math.atan((velocitySq - Math.sqrt(velocitySq * velocitySq - g * (g * hDistanceSq + 2.0 * relativeY * velocitySq))) / (g * hDistance))));
         
         if (Float.isNaN(pitch)) {
             Rotations.rotate((float)Rotations.getYaw(this.target), (float)Rotations.getPitch(this.target));
         } else {
             Rotations.rotate((float)Rotations.getYaw(new Vec3d(pos.x, pos.y, pos.z)), pitch);
         }
     }
     
     @Override
     public String getInfoString() {
         if (this.culling.get() && this.cullingTarget != null) {
             return "Culling: " + EntityUtils.getName(this.cullingTarget);
         }
         return EntityUtils.getName(this.target);
     }
 }
 