package meteordevelopment.meteorclient.systems.modules.combat;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.util.Hand;

public class GodMode extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Double> healthThreshold = sgGeneral.add(new DoubleSetting.Builder()
        .name("health-threshold")
        .description("Uses a potion if health is below this value.")
        .defaultValue(10.0)
        .min(1.0)
        .max(20.0)
        .sliderMax(20.0)
        .build()
    );

    private final Setting<Integer> interval = sgGeneral.add(new IntSetting.Builder()
        .name("interval")
        .description("Delay in ticks between potion uses.")
        .defaultValue(60)
        .min(10)
        .max(200)
        .sliderMin(10)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> rotationPriority = sgGeneral.add(new IntSetting.Builder()
        .name("rotation-priority")
        .description("Priority for server-side rotation.")
        .defaultValue(-100)
        .build()
    );

    private final Setting<Boolean> chatFeedback = sgGeneral.add(new BoolSetting.Builder()
        .name("chat-feedback")
        .description("Sends a chat message when a potion is used or not found.")
        .defaultValue(true)
        .build()
    );

    private enum State { IDLE, PREPARING_POTION, ROTATING_AND_THROWING, RESTORING_INV }
    private State currentState = State.IDLE;

    private int potionUseTimer = 0;
    private int operationCooldown = 0;

    private int potionHotbarSlot = -1;
    private boolean didSwapToPotionSlot = false;

    public GodMode() {
        super(Categories.Combat, "god-mode", "Automatically uses health potions from hotbar when low on health.");
    }

    @Override
    public void onActivate() {
        resetState();
        potionUseTimer = 0;
    }

    @Override
    public void onDeactivate() {
        if (mc.player != null && currentState != State.IDLE) {
            if (didSwapToPotionSlot) {
                InvUtils.swapBack();
            }
        }
        resetState();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            if (currentState != State.IDLE) {
                resetState();
            }
            return;
        }

        if (operationCooldown > 0) {
            operationCooldown--;
            return;
        }
        if (potionUseTimer > 0) {
            potionUseTimer--;
        }

        switch (currentState) {
            case IDLE:                  handleIdleState();                  break;
            case PREPARING_POTION:      handlePreparingPotionState();      break;
            case ROTATING_AND_THROWING: handleRotatingAndThrowingState(); break;
            case RESTORING_INV:         handleRestoringInvState();         break;
        }
    }

    private void handleIdleState() {
        if (mc.player.getHealth() <= healthThreshold.get() && potionUseTimer <= 0) {
            int foundSlot = findHealingPotionInHotbar();
            if (foundSlot != -1) {
                potionHotbarSlot = foundSlot;
                currentState = State.PREPARING_POTION;
                operationCooldown = 1; 
            } else {
                if (chatFeedback.get()) {
                    info("No healing potion found in hotbar.");
                }
                potionUseTimer = interval.get();
            }
        }
    }

    private void handlePreparingPotionState() {
        InvUtils.swap(potionHotbarSlot, true); 
        didSwapToPotionSlot = true;
        
        currentState = State.ROTATING_AND_THROWING;
        operationCooldown = 1;
    }

    private void handleRotatingAndThrowingState() {
        ItemStack itemInHand = mc.player.getMainHandStack(); 

        if (!isHealingPotion(itemInHand)) {
            if (didSwapToPotionSlot) InvUtils.swapBack();
            resetState();
            return;
        }

        Rotations.rotate(mc.player.getYaw(), 90f, rotationPriority.get(), () -> {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            potionUseTimer = interval.get();
            if (chatFeedback.get()) {
                info("Used a healing potion.");
            }
            currentState = State.RESTORING_INV;
            operationCooldown = 1; 
        });
        operationCooldown = 5; 
    }

    private void handleRestoringInvState() {
        if (didSwapToPotionSlot) {
            InvUtils.swapBack();
        }
        resetState();
        operationCooldown = 2; 
    }
    
    private void resetState() {
        currentState = State.IDLE;
        potionHotbarSlot = -1;
        didSwapToPotionSlot = false;
        if (operationCooldown > 5 && currentState != State.IDLE) operationCooldown = 5; 
        else if (currentState == State.IDLE) operationCooldown = 0;
    }

    private int findHealingPotionInHotbar() {
        for (int i = 0; i <= 8; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isHealingPotion(stack)) {
                return i;
            }
        }
        return -1;
    }

    private boolean isHealingPotion(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        boolean isCorrectType = stack.getItem() == Items.SPLASH_POTION || stack.getItem() == Items.LINGERING_POTION;
        if (!isCorrectType) {
            return false;
        }

        PotionContentsComponent potionContents = stack.get(DataComponentTypes.POTION_CONTENTS);
        if (potionContents == null) {
            return false;
        }

        for (StatusEffectInstance effectInstance : potionContents.getEffects()) {
            RegistryEntry<StatusEffect> effectType = effectInstance.getEffectType();
            if (effectType == null || !effectType.hasKeyAndValue()) { 
                continue;
            }
            String effectId = effectType.getIdAsString();
            
            if (effectId.equals("minecraft:instant_health") || effectId.equals("minecraft:regeneration")) {
                return true;
            }
        }
        return false;
    }
}