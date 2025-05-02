package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.serialization.DataResult;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
import net.minecraft.command.CommandSource;
import net.minecraft.command.DataCommandObject;
import net.minecraft.command.EntityDataObject;
import net.minecraft.command.argument.NbtPathArgumentType;
import net.minecraft.component.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Unit;


public class NbtCommand extends Command {
    private static final DynamicCommandExceptionType MALFORMED_ITEM_EXCEPTION = new DynamicCommandExceptionType(
        error -> Text.stringifiedTranslatable("arguments.item.malformed", error)
    );
    private final Text copyButton = Text.literal("NBT").setStyle(Style.EMPTY
        .withFormatting(Formatting.UNDERLINE)
        .withClickEvent(new MeteorClickEvent(
            this.toString("copy")
        ))
        .withHoverEvent(new HoverEvent.ShowText(
            Text.literal("Copy the NBT data to your clipboard.")
        )));

    public NbtCommand() {
        super("nbt", "Modifies NBT data for an item, example: ");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("copy").executes(context -> {
            ItemStack stack = mc.player.getInventory().getSelectedStack();
            if (!validBasic(stack)) return SINGLE_SUCCESS;
        
            var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
            var result = ItemStack.CODEC.encode(stack, registryOps, new NbtCompound());
        
            if (result.result().isEmpty()) {
                error("Failed to encode item NBT.");
                return SINGLE_SUCCESS;
            }
        
            NbtCompound nbt = (NbtCompound) result.result().get();

            mc.keyboard.setClipboard(nbt.toString());
        
            MutableText text = Text.empty()
                .append(copyButton)
                .append(" ")
                .append(NbtHelper.toPrettyPrintedText(nbt))
                .append(" data copied!");
        
            info(text);
            return SINGLE_SUCCESS;
        }));
        
        

        builder.then(literal("add")
            .then(argument("tag", StringArgumentType.word())
                .executes(context -> {
                    String tag = StringArgumentType.getString(context, "tag");
                    ItemStack stack = mc.player.getInventory().getSelectedStack();

                    if (!validBasic(stack)) {
                        info("Invalid item stack.");
                        return 0; // Returning success with no action taken
                    }

                    try {
                        NbtCompound componentsNbt = new NbtCompound();

                        switch (tag) {
                            case "unbreakable" -> componentsNbt.put("minecraft:unbreakable", new NbtCompound());
                            case "consumable" -> {
                                NbtCompound consumable = new NbtCompound();
                                consumable.put("on_consume_effects", new NbtList()); // Empty effect array for later addition
                                componentsNbt.put("minecraft:consumable", consumable);
                            }
                            default -> throw new CommandSyntaxException(null, Text.literal("Support for tag '" + tag + "' not added yet."));
                        }

                        // Parse components NBT
                        var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                        DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsNbt);

                        ComponentMap newComponents = parseResult.getOrThrow(errorMsg ->
                            new CommandSyntaxException(null, Text.literal("Failed to parse components NBT: " + errorMsg))
                        );

                        // Validate the components
                        DataResult<Unit> validationResult = ItemStack.validateComponents(newComponents);
                        validationResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);

                        // Apply components to the item stack
                        stack.applyComponentsFrom(newComponents);
                        setStack(stack);

                        info("Successfully added %s tag to the held item.", tag);
                    } catch (CommandSyntaxException e) {
                        error("Failed to add tag: %s", e.getMessage());
                        return 0; // Return with error
                    } catch (Exception e) {
                        error("Unexpected error: %s", e.getMessage());
                        return 0; // Return with error
                    }

                    return 1; // Success
                })
            )
        );

        builder.then(literal("setitemmodel")
        .then(argument("model", StringArgumentType.word())
            .executes(context -> {
                String model = StringArgumentType.getString(context, "model");
                ItemStack stack = mc.player.getInventory().getSelectedStack();
    
                if (!validBasic(stack)) return SINGLE_SUCCESS;
    
                try {
                    NbtCompound componentsNbt = new NbtCompound();
    
                    if (!"null".equalsIgnoreCase(model)) {
                        componentsNbt.putString("minecraft:item_model", "minecraft:" + model);
                    }
    
                    var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                    DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsNbt);
    
                    ComponentMap parsedComponents = parseResult.getOrThrow(errorMsg ->
                        new CommandSyntaxException(null, Text.literal("Failed to parse components NBT: " + errorMsg))
                    );
    
                    DataResult<Unit> validationResult = ItemStack.validateComponents(parsedComponents);
                    validationResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);
    
                    stack.applyComponentsFrom(parsedComponents);
                    setStack(stack);
    
                    info("Successfully set the item model to: %s", model);
                } catch (CommandSyntaxException e) {
                    error("Failed to apply item model: %s", e.getMessage());
                } catch (Exception e) {
                    error("Unexpected error: %s", e.getMessage());
                }
    
                return SINGLE_SUCCESS;
            })
        )
    );
    
    
    

    
            

        builder.then(literal("count").then(argument("count", IntegerArgumentType.integer(1, 127)).executes(context -> {
            ItemStack stack = mc.player.getInventory().getSelectedStack();
        
            if (validBasic(stack)) {
                int count = IntegerArgumentType.getInteger(context, "count");
        
                // Set actual count
                stack.setCount(count);
        
                // Inject max_stack_size into components
                NbtCompound components = new NbtCompound();
                components.put("minecraft:max_stack_size", NbtInt.of(count));
        
                NbtCompound root = new NbtCompound();
                root.put("components", components);
        
                var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                var parseResult = ComponentMap.CODEC.parse(registryOps, components);
                ComponentMap compMap = parseResult.getOrThrow(msg -> new CommandSyntaxException(null, Text.literal(msg)));
        
                stack.applyComponentsFrom(compMap);
                setStack(stack);
        
                info("Set count and max_stack_size to %s.", count);
            }
        
            return SINGLE_SUCCESS;
        })));
        
        
        

        // Original load command (using applyComponentsFrom)
        builder.then(literal("load").executes(context -> {
            ItemStack stack = mc.player.getInventory().getSelectedStack();

            if (validBasic(stack)) {
                String clipboardContent = mc.keyboard.getClipboard();

                if (clipboardContent == null || clipboardContent.isEmpty()) {
                    error("Clipboard is empty. Copy NBT data first using .nbt copy.");
                    return SINGLE_SUCCESS;
                }
 
                try {
                    NbtCompound rootNbt = NbtHelper.fromNbtProviderString(clipboardContent);

                    if (!rootNbt.contains("components")) {
                        error("No 'components' NBT tag found in clipboard data.");
                        return SINGLE_SUCCESS;
                    }

                    NbtElement componentsElement = rootNbt.get("components");
                    if (componentsElement == null || componentsElement.getType() != NbtElement.COMPOUND_TYPE) {
                        error("The 'components' tag is not a valid NBT Compound.");
                        return SINGLE_SUCCESS;
                    }

                    NbtCompound componentsNbt = (NbtCompound) componentsElement;

                    var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                    DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsNbt);

                    ComponentMap newComponents = parseResult.getOrThrow(errorMsg ->
                        new CommandSyntaxException(null, Text.literal("Failed to parse components NBT: " + errorMsg))
                    );

                    DataResult<Unit> validationResult = ItemStack.validateComponents(newComponents);
                    validationResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);

                    // Apply components from clipboard, potentially merging/overwriting based on applyComponentsFrom behavior
                    stack.applyComponentsFrom(newComponents);

                    setStack(stack);

                    info("Successfully loaded and applied NBT data from clipboard to the held item.");

                } catch (CommandSyntaxException e) {
                    error("Failed to load NBT: %s", e.getMessage());
                } catch (Exception e) {
                    error("An unexpected error occurred: %s", e.getMessage());
                }
            }

            return SINGLE_SUCCESS;
        }));
        
        builder.then(literal("loadinvis").executes(context -> {
            ItemStack stack = mc.player.getInventory().getSelectedStack();
        
            if (validBasic(stack)) {
                String clipboardContent = mc.keyboard.getClipboard();
        
                if (clipboardContent == null || clipboardContent.isEmpty()) {
                    error("Clipboard is empty. Copy NBT data first (e.g., using .nbt copy).");
                    return SINGLE_SUCCESS;
                }
        
                try {
                    NbtCompound rootNbt = NbtHelper.fromNbtProviderString(clipboardContent);
                    NbtCompound componentsNbt = null;
        
                    // --- Fix Start: Use contains(String) only --- 
                    // Check if clipboard contains components tag using only the key name
                    if (rootNbt.contains("components")) { // MODIFIED: Use contains(String) only
                        NbtElement element = rootNbt.get("components");
                        // Verify the type using instanceof after getting the element
                        if (element instanceof NbtCompound) {
                            componentsNbt = (NbtCompound) element;
                        } else {
                             error("Unexpected type for 'components' tag. Expected NbtCompound.");
                             return SINGLE_SUCCESS;
                        }
                    } else {
                        // If no components tag, maybe the clipboard IS the components tag?
                        if (clipboardContent.trim().startsWith("{")) { // Basic check if it looks like a compound
                             componentsNbt = rootNbt; // Try using the root as components
                        } else {
                            error("NBT data in clipboard does not contain a valid 'components' tag.");
                            return SINGLE_SUCCESS;
                        }
                    }
                    // --- Fix End ---
        
                    // Ensure componentsNbt is not null after the checks
                    if (componentsNbt == null) {
                         error("Failed to identify components NBT data.");
                         return SINGLE_SUCCESS;
                    }
        
                    // Remove "!minecraft:item_model" before parsing, if it exists, to avoid parser error.
                    // Also check using contains(String) only
                    if (componentsNbt.contains("!minecraft:item_model")) { 
                        componentsNbt.remove("!minecraft:item_model");
                        info("Note: '!minecraft:item_model' found in clipboard NBT was ignored during initial parse.");
                    }
        
                    var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                    DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsNbt);
        
                    ComponentMap parsedComponents = parseResult.getOrThrow(errorMsg ->
                        new CommandSyntaxException(null, Text.literal("Failed to parse components NBT: " + errorMsg))
                    );
        
                    DataResult<Unit> validationResult = ItemStack.validateComponents(parsedComponents);
                    validationResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);
        
                    // Apply the parsed components (without the !minecraft:item_model)
                    stack.applyComponentsFrom(parsedComponents);
        
                    // Explicitly remove the item model component to achieve invisibility
                    stack.remove(DataComponentTypes.ITEM_MODEL);
        
                    setStack(stack); // Apply the modified stack to the player's hand
        
                    info("Successfully applied NBT components and removed item model for invisibility.");
        
                } catch (CommandSyntaxException e) {
                    error("Failed to apply NBT components: %s", e.getMessage());
                } catch (Exception e) {
                    // Catch broader exceptions during NBT parsing or manipulation
                    error("An unexpected error occurred while processing NBT: %s", e.getMessage());
                    e.printStackTrace(); // Log stack trace for debugging
                }
            }
        
            return SINGLE_SUCCESS;
        }));
    }
    private void setStack(ItemStack stack) {
        // Ensure the player is in creative mode before sending the packet
        if (mc.player != null && mc.player.getAbilities().creativeMode) {
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + mc.player.getInventory().getSelectedSlot(), stack));
        }
    }

    private boolean validBasic(ItemStack stack) {
        if (mc.player == null) {
             error("Player is not available.");
             return false;
        }
        if (!mc.player.getAbilities().creativeMode) {
            error("Creative mode only.");
            return false;
        }

        if (stack == null || stack.isEmpty()) { // Check for null stack as well
            error("You must hold an item in your main hand.");
            return false;
        }
        return true;
    }
}

