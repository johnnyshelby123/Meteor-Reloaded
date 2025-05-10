package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DataResult;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;

public class RepairCommand extends Command {
    private static final DynamicCommandExceptionType MALFORMED_COMPONENTS_EXCEPTION = new DynamicCommandExceptionType(
        error -> Text.stringifiedTranslatable("arguments.item.malformed", error)
    );

    public RepairCommand() {
        super("repair", "Repairs the item you are currently holding. Creative mode only.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null || mc.world == null) {
                error("Player or world not available.");
                return 0;
            }

            PlayerInventory inventory = mc.player.getInventory();
            ItemStack stack = inventory.getSelectedStack(); 

            if (!validBasic(stack)) {
                return 0; 
            }

            Integer currentDamage = stack.get(DataComponentTypes.DAMAGE);

            if (currentDamage != null && currentDamage > 0) {
                try {
                    NbtCompound componentsToApplyNbt = new NbtCompound();
                    componentsToApplyNbt.putInt("minecraft:damage", 0);

                    var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                    DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsToApplyNbt);

                    ComponentMap newComponents = parseResult.getOrThrow(errorMsg ->
                        new CommandSyntaxException(null, Text.literal("Failed to parse repair components NBT: " + errorMsg))
                    );

                    DataResult<Unit> validationResult = ItemStack.validateComponents(newComponents);
                    validationResult.getOrThrow(MALFORMED_COMPONENTS_EXCEPTION::create);

                    stack.applyComponentsFrom(newComponents);

                    setStack(stack);
                    info(Text.literal("Item repaired successfully.").formatted(Formatting.GREEN));

                } catch (CommandSyntaxException e) {
                    error("Failed to repair item: %s", e.getMessage());
                    return 0;
                } catch (Exception e) {
                    error("An unexpected error occurred during repair: %s", e.getMessage());
                    e.printStackTrace();
                    return 0;
                }
            } else if (currentDamage != null && currentDamage == 0) {
                info(Text.literal("Item is already fully repaired.").formatted(Formatting.YELLOW));
            } else {
                info(Text.literal("This item cannot be damaged or is not damaged.").formatted(Formatting.YELLOW));
            }

            return SINGLE_SUCCESS;
        });
    }

    private void setStack(ItemStack stackToUpdate) {
        if (mc.player != null && mc.player.getAbilities().creativeMode) {
            PlayerInventory inventory = mc.player.getInventory();
            ItemStack currentSelectedStackInHand = inventory.getSelectedStack(); 

            int selectedHotbarSlot = -1;

            if (currentSelectedStackInHand == stackToUpdate && currentSelectedStackInHand != null && !currentSelectedStackInHand.isEmpty()) {
                for (int i = 0; i < PlayerInventory.getHotbarSize(); i++) {
                    if (inventory.getStack(i) == currentSelectedStackInHand) { 
                        selectedHotbarSlot = i;
                        break;
                    }
                }
            }

            if (selectedHotbarSlot != -1) {
                // Send packet to server with the original modified stack instance
                mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + selectedHotbarSlot, stackToUpdate));
                
                // Explicitly update the client-side inventory as well
                // Using .copy() here is generally safer for client-side updates to prevent unintended shared instance issues,
                // though NbtCommand might pass the direct reference. We try with copy first for client-side.
                inventory.setStack(selectedHotbarSlot, stackToUpdate.copy());
            } else {
                error("RepairCommand: Could not dynamically determine selected hotbar slot. Item may not have updated correctly.");
            }
        }
    }

    private boolean validBasic(ItemStack stack) {
        if (mc.player == null) {
            error("Player not available.");
            return false;
        }
        if (!mc.player.getAbilities().creativeMode) {
            error("Creative mode only.");
            return false;
        }
        if (stack == null || stack.isEmpty()) {
            error("You must hold an item in your main hand.");
            return false;
        }
        return true;
    }
}

