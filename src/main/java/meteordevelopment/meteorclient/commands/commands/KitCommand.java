package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DataResult;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.ComponentMap;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.RegistryOps;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Unit;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.stream.Collectors;

public class KitCommand extends Command {
    private static final DynamicCommandExceptionType MALFORMED_ITEM_EXCEPTION = new DynamicCommandExceptionType(
        error -> Text.stringifiedTranslatable("arguments.item.malformed", error)
    );

    public KitCommand() {
        super("kit", "Gives you a predefined kit item.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Add kits here, setting makeInvisible to true or false as needed
        registerKitSubcommand(builder, "fair", Items.LIGHT_BLUE_SHULKER_BOX, "kits/fair.json", false);
        registerKitSubcommand(builder, "kbstick", Items.STICK, "kits/kbstick.json", true);
        registerKitSubcommand(builder, "legit", Items.RED_SHULKER_BOX, "kits/legit.json", false);
        registerKitSubcommand(builder, "wither", Items.BLACK_SHULKER_BOX, "kits/withergrief.json", false);
        registerKitSubcommand(builder, "unfair", Items.BLUE_SHULKER_BOX, "kits/unfairpvp.json", false);

        // Example for adding an invisible kit later:
        // registerKitSubcommand(builder, "invis-fair", Items.LIGHT_BLUE_SHULKER_BOX, "kits/fair.json", true);
    }

    private void registerKitSubcommand(LiteralArgumentBuilder<CommandSource> builder, String kitName, Item itemType, String resourcePath, boolean makeInvisible) {
        Identifier resourceId = Identifier.of("meteor-client", resourcePath);

        builder.then(literal(kitName).executes(context -> {
            if (!validBasic()) return SINGLE_SUCCESS;

            String nbtString = loadNbtFromResource(resourceId);
            if (nbtString == null) {
                error("Failed to load NBT data for kit ", kitName);
                return SINGLE_SUCCESS;
            }

            ItemStack newStack = new ItemStack(itemType);
            if (applyNbtOntoStack(newStack, nbtString, kitName)) {
                // Apply invisibility if requested
                if (makeInvisible) {
                    newStack.remove(DataComponentTypes.ITEM_MODEL);
                }
                setStack(newStack);
                info("Gave kit: %s (%s)%s", kitName, itemType.getName().getString(), makeInvisible ? " (Invisible)" : "");
            }

            return SINGLE_SUCCESS;
        }));
    }

    private String loadNbtFromResource(Identifier resourceId) {
        ResourceManager resourceManager = mc.getResourceManager();
        Optional<Resource> resourceOptional = resourceManager.getResource(resourceId);

        if (resourceOptional.isPresent()) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(resourceOptional.get().getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            } catch (Exception e) {
                MeteorClient.LOG.error("Failed to load kit NBT from resource: {}", resourceId, e);
                return null;
            }
        } else {
            MeteorClient.LOG.error("Kit NBT resource not found: {}", resourceId);
            return null;
        }
    }

    private boolean applyNbtOntoStack(ItemStack stack, String nbtString, String kitName) {
        try {
            NbtCompound rootNbt = NbtHelper.fromNbtProviderString(nbtString);
            NbtCompound componentsNbt = null;

            if (rootNbt.contains("components")) {
                NbtElement element = rootNbt.get("components");
                if (element instanceof NbtCompound) {
                    componentsNbt = (NbtCompound) element;
                } else {
                    error("Unexpected type for 'components' tag in kit NBT for '%s'. Expected NbtCompound.", kitName);
                    return false;
                }
            } else {
                if (nbtString.trim().startsWith("{")) {
                    componentsNbt = rootNbt;
                } else {
                    error("Kit NBT data for '%s' does not contain a valid 'components' tag.", kitName);
                    return false;
                }
            }

            if (componentsNbt == null) {
                error("Failed to identify components NBT data in kit NBT for '%s'.", kitName);
                return false;
            }

            var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
            DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsNbt);

            ComponentMap parsedComponents = parseResult.getOrThrow(errorMsg ->
                new CommandSyntaxException(null, Text.literal("Failed to parse components NBT for kit '" + kitName + "': " + errorMsg))
            );

            DataResult<Unit> validationResult = ItemStack.validateComponents(parsedComponents);
            validationResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);

            stack.applyComponentsFrom(parsedComponents);

            return true;

        } catch (CommandSyntaxException e) {
            error("Failed to apply NBT components for kit '%s': %s", kitName, e.getMessage());
        } catch (Exception e) {
            error("An unexpected error occurred while processing kit NBT for '%s': %s", kitName, e.getMessage());
            MeteorClient.LOG.error("Kit NBT processing error for kit '{}'", kitName, e);
        }
        return false;
    }

    private void setStack(ItemStack stack) {
        if (mc.player != null && mc.player.getAbilities().creativeMode) {
            int slot = mc.player.getInventory().getSelectedSlot();
            // Update server
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + slot, stack));
            // Update client immediately
            mc.player.getInventory().setStack(slot, stack);
        }
    }

    private boolean validBasic() {
        if (mc.player == null) {
            error("Player is not available.");
            return false;
        }
        if (!mc.player.getAbilities().creativeMode) {
            error("Creative mode only.");
            return false;
        }
        return true;
    }
}