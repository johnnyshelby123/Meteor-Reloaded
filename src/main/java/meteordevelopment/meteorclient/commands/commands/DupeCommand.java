package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class DupeCommand extends Command {

    public DupeCommand() {
        super("dupe", "Duplicates held item. Fills current stack first, then empty slots (Creative mode only).");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // .dupe (defaults to count 1)
        builder.executes(context -> executeDupe(context, 1));

        // .dupe <count>
        builder.then(argument("count", IntegerArgumentType.integer(1))
            .executes(context -> executeDupe(context, IntegerArgumentType.getInteger(context, "count")))
        );
    }

    private int executeDupe(com.mojang.brigadier.context.CommandContext<CommandSource> context, int requestedCount) {
        if (mc.player == null) {
            error("Player not available.");
            return SINGLE_SUCCESS;
        }
        if (!mc.player.getAbilities().creativeMode) {
            error("Creative mode only.");
            return SINGLE_SUCCESS;
        }

        int heldSlot = mc.player.getInventory().getSelectedSlot(); // 0-8 for hotbar
        ItemStack heldStack = mc.player.getInventory().getStack(heldSlot);

        if (heldStack == null || heldStack.isEmpty()) {
            error("You must hold an item in your main hand to duplicate.");
            return SINGLE_SUCCESS;
        }

        int maxStackSize = heldStack.getMaxCount();
        int currentCount = heldStack.getCount();
        int totalAdded = 0;
        int remainingToDupe = requestedCount;

        // 1. Fill the current stack first
        int canAddToCurrent = maxStackSize - currentCount;
        int addToCurrent = Math.min(remainingToDupe, canAddToCurrent);

        if (addToCurrent > 0) {
            ItemStack modifiedHeldStack = heldStack.copy();
            modifiedHeldStack.setCount(currentCount + addToCurrent);
            // Send packet for the held slot (hotbar 0-8 -> packet 36-44)
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + heldSlot, modifiedHeldStack));
            // Client-side update for held slot
            mc.player.getInventory().setStack(heldSlot, modifiedHeldStack);
            totalAdded += addToCurrent;
            remainingToDupe -= addToCurrent;
        }

        // 2. Fill empty slots with the remainder
        if (remainingToDupe > 0) {
            // Iterate through inventory slots (hotbar 0-8, main inventory 9-35)
            for (int i = 0; i < 36 && remainingToDupe > 0; i++) {
                // Skip the currently held slot
                if (i == heldSlot) continue;

                ItemStack slotStack = mc.player.getInventory().getStack(i);

                if (slotStack.isEmpty()) {
                    ItemStack duplicatedStack = heldStack.copy();
                    int countForThisSlot = Math.min(remainingToDupe, maxStackSize);
                    duplicatedStack.setCount(countForThisSlot);

                    // Calculate the correct slot ID for the CreativeInventoryActionC2SPacket
                    int packetSlotId = (i < 9) ? (36 + i) : i;

                    // Send packet to place the item in the empty slot
                    mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(packetSlotId, duplicatedStack));
                    // Client-side update for the empty slot
                    mc.player.getInventory().setStack(i, duplicatedStack);

                    totalAdded += countForThisSlot;
                    remainingToDupe -= countForThisSlot;
                }
            }
        }

        // Report result
        if (totalAdded == 0 && requestedCount > 0) {
            if (currentCount == maxStackSize) {
                 error("Held stack is full and no empty inventory slots found.");
            } else {
                 error("Could not duplicate items. Check inventory space."); // Generic error
            }
        } else if (remainingToDupe > 0) {
            info("Duplicated %d item(s). Inventory full.", totalAdded);
        } else {
            info("Duplicated %d item(s).", totalAdded);
        }

        return SINGLE_SUCCESS;
    }
}

