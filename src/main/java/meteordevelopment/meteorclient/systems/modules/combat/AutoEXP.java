/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.combat;

 import meteordevelopment.meteorclient.events.world.TickEvent;
 import meteordevelopment.meteorclient.settings.*;
 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 import meteordevelopment.meteorclient.utils.Utils;
 import meteordevelopment.meteorclient.utils.player.FindItemResult;
 import meteordevelopment.meteorclient.utils.player.InvUtils;
 import meteordevelopment.meteorclient.utils.player.Rotations;
 import meteordevelopment.meteorclient.utils.player.SlotUtils;
 import meteordevelopment.orbit.EventHandler;
 import net.minecraft.component.type.AttributeModifierSlot;
 import net.minecraft.enchantment.Enchantments;
 import net.minecraft.entity.EquipmentSlot;
 import net.minecraft.item.ItemStack;
 import net.minecraft.item.Items;
 import net.minecraft.util.Hand;
 
 public class AutoEXP extends Module {
     private final SettingGroup sgGeneral = settings.getDefaultGroup();
 
     private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
         .name("mode")
         .description("Which items to repair.")
         .defaultValue(Mode.Both)
         .build()
     );
 
     private final Setting<Boolean> replenish = sgGeneral.add(new BoolSetting.Builder()
         .name("replenish")
         .description("Automatically replenishes exp into a selected hotbar slot.")
         .defaultValue(true)
         .build()
     );
 
     private final Setting<Integer> replenishSlot = sgGeneral.add(new IntSetting.Builder()
         .name("replenish-slot")
         .description("The hotbar slot (1-9) to replenish exp into.")
         .visible(replenish::get)
         .defaultValue(6)
         .range(1, 9)
         .sliderRange(1, 9)
         .build()
     );
 
     private final Setting<Integer> minThreshold = sgGeneral.add(new IntSetting.Builder()
         .name("min-threshold")
         .description("The minimum durability percentage that an item needs to fall to, to be repaired.")
         .defaultValue(30)
         .range(1, 100)
         .sliderRange(1, 100)
         .build()
     );
 
     private final Setting<Integer> maxThreshold = sgGeneral.add(new IntSetting.Builder()
         .name("max-threshold")
         .description("The maximum durability percentage to repair items to.")
         .defaultValue(80)
         .range(1, 100)
         .sliderRange(1, 100)
         .build()
     );
 
     private final Setting<Integer> rotationPriority = sgGeneral.add(new IntSetting.Builder()
         .name("rotation-priority")
         .description("Priority for server-side rotation.")
         .defaultValue(-100)
         .build()
     );
 
     private final Setting<Integer> throwsPerCycle = sgGeneral.add(new IntSetting.Builder()
         .name("throws-per-cycle")
         .description("How many XP bottles to throw for an item before re-evaluating.")
         .defaultValue(1)
         .min(1).sliderMax(10)
         .build()
     );
 
     private final Setting<Integer> throwDelay = sgGeneral.add(new IntSetting.Builder()
         .name("throw-delay")
         .description("Delay in ticks between throwing XP bottles.")
         .defaultValue(2)
         .min(0).sliderMax(20)
         .build()
     );
 
     private int currentItemSlotToRepair = -1;
     private int throwsThisCycle = 0;
     private int throwCooldown = 0;
 
     public AutoEXP() {
         super(Categories.Combat, "auto-exp", "Automatically repairs your armor and tools with server-side rotations.");
     }
 
     @Override
     public void onActivate() {
         currentItemSlotToRepair = -1;
         throwsThisCycle = 0;
         throwCooldown = 0;
     }
 
     @EventHandler
     private void onTick(TickEvent.Pre event) {
         if (mc.player == null || mc.world == null) return;
 
         if (throwCooldown > 0) {
             throwCooldown--;
             return;
         }
 
         if (currentItemSlotToRepair == -1 || 
             !needsRepair(mc.player.getInventory().getStack(currentItemSlotToRepair), maxThreshold.get()) || 
             throwsThisCycle >= throwsPerCycle.get()) {
             
             currentItemSlotToRepair = findNextItemToRepair();
             throwsThisCycle = 0;
             if (currentItemSlotToRepair == -1) {
                 return; 
             }
         }
 
         FindItemResult exp = InvUtils.find(Items.EXPERIENCE_BOTTLE);
         if (!exp.found()) return;
 
         int xpHotbarSlotToUse = -1; 
         Hand handToUse = null;
         boolean doSwap = false;
         int currentSelectedSlot = -1; 
 
         for (int i = 0; i < 9; i++) {
             if (mc.player.getInventory().getStack(i) == mc.player.getMainHandStack()) {
                 currentSelectedSlot = i;
                 break;
             }
         }
         if (currentSelectedSlot == -1 && mc.player.getMainHandStack().isEmpty()) { 
              for (int i = 0; i < 9; i++) {
                 if (mc.player.getInventory().getStack(i).isEmpty()) {
                     currentSelectedSlot = i; 
                     break;
                 }
             }
             if (currentSelectedSlot == -1) currentSelectedSlot = 0; 
         }
 
         if (exp.isOffhand()) {
             handToUse = Hand.OFF_HAND;
         } else if (exp.isHotbar()) {
             xpHotbarSlotToUse = exp.slot();
             handToUse = Hand.MAIN_HAND;
             if (currentSelectedSlot != xpHotbarSlotToUse) {
                 doSwap = true;
             }
         } else if (replenish.get()) { 
             xpHotbarSlotToUse = replenishSlot.get() - 1;
             InvUtils.move().from(exp.slot()).toHotbar(xpHotbarSlotToUse);
             handToUse = Hand.MAIN_HAND;
             if (currentSelectedSlot != xpHotbarSlotToUse) {
                 doSwap = true;
             }
         } else {
             return; 
         }
 
         if (handToUse == null) return;
 
         final boolean finalDoSwap = doSwap;
         final int finalXPHotbarSlotToUse = xpHotbarSlotToUse; 
         final Hand finalHandToUse = handToUse;
 
         Rotations.rotate(mc.player.getYaw(), 90, rotationPriority.get(), () -> {
             boolean swapped = false;
             if (finalDoSwap && finalHandToUse == Hand.MAIN_HAND) {
                 InvUtils.swap(finalXPHotbarSlotToUse, true);
                 swapped = true;
             }
 
             mc.interactionManager.interactItem(mc.player, finalHandToUse);
             throwsThisCycle++;
             throwCooldown = throwDelay.get();
 
             if (swapped) {
                 InvUtils.swapBack();
             }
         });
     }
 
     private int findNextItemToRepair() {
         if (mode.get() == Mode.Armor || mode.get() == Mode.Both) {
             for (EquipmentSlot eqSlot : AttributeModifierSlot.ARMOR) { 
                 ItemStack stack = mc.player.getEquippedStack(eqSlot);
                 if (needsRepair(stack, minThreshold.get())) {
                     return SlotUtils.ARMOR_START + eqSlot.getEntitySlotId();
                 }
             }
         }
 
         if (mode.get() == Mode.Hands || mode.get() == Mode.Both) {
             ItemStack mainHandStack = mc.player.getMainHandStack();
             if (needsRepair(mainHandStack, minThreshold.get())) {
                 for (int i = 0; i < 9; i++) { // Iterate hotbar slots 0-8
                     if (mc.player.getInventory().getStack(i) == mainHandStack) {
                         return i; // Return the actual hotbar slot index of the main hand item
                     }
                 }
             }
             ItemStack offHandStack = mc.player.getOffHandStack();
             if (needsRepair(offHandStack, minThreshold.get())) {
                 return SlotUtils.OFFHAND;
             }
         }
         return -1;
     }
 
     private boolean needsRepair(ItemStack itemStack, double threshold) {
         if (itemStack.isEmpty() || !Utils.hasEnchantments(itemStack, Enchantments.MENDING)) return false;
         return (itemStack.getMaxDamage() - itemStack.getDamage()) / (double) itemStack.getMaxDamage() * 100 <= threshold;
     }
 
     public enum Mode {
         Armor,
         Hands,
         Both
     }
 }
 
 