/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.commands.commands;

 import com.mojang.brigadier.arguments.IntegerArgumentType;
 import com.mojang.brigadier.builder.LiteralArgumentBuilder;
 import com.mojang.brigadier.exceptions.CommandSyntaxException;
 import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
 import com.mojang.serialization.DataResult;
 import meteordevelopment.meteorclient.commands.Command;
 import meteordevelopment.meteorclient.commands.arguments.ComponentMapArgumentType;
 import meteordevelopment.meteorclient.utils.misc.text.MeteorClickEvent;
 import net.minecraft.command.CommandSource;
 import net.minecraft.command.DataCommandObject;
 import net.minecraft.command.EntityDataObject;
 import net.minecraft.command.argument.NbtPathArgumentType;
 import net.minecraft.command.argument.RegistryKeyArgumentType;
 import net.minecraft.component.*;
 import net.minecraft.item.ItemStack;
 import net.minecraft.nbt.NbtCompound;
 import net.minecraft.nbt.NbtElement;
 import net.minecraft.nbt.NbtHelper;
 import net.minecraft.nbt.NbtOps;
 import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
 import net.minecraft.registry.Registries;
 import net.minecraft.registry.RegistryKey;
 import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.text.HoverEvent;
 import net.minecraft.text.MutableText;
 import net.minecraft.text.Style;
 import net.minecraft.text.Text;
 import net.minecraft.util.Formatting;
 import net.minecraft.util.Unit;
 
 import java.util.List;
 import java.util.Locale;
 import java.util.Set;
 
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
         builder.then(literal("add").then(argument("component", ComponentMapArgumentType.componentMap(REGISTRY_ACCESS)).executes(ctx -> {
             ItemStack stack = mc.player.getInventory().getSelectedStack();
 
             if (validBasic(stack)) {
                 ComponentMap itemComponents = stack.getComponents();
                 ComponentMap newComponents = ComponentMapArgumentType.getComponentMap(ctx, "component");
 
                 ComponentMap testComponents = ComponentMap.of(itemComponents, newComponents);
                 DataResult<Unit> dataResult = ItemStack.validateComponents(testComponents);
                 dataResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);
 
                 stack.applyComponentsFrom(testComponents);
 
                 setStack(stack);
             }
 
             return SINGLE_SUCCESS;
         })));
 
         builder.then(literal("set").then(argument("component", ComponentMapArgumentType.componentMap(REGISTRY_ACCESS)).executes(ctx -> {
             ItemStack stack = mc.player.getInventory().getSelectedStack();
 
             if (validBasic(stack)) {
                 ComponentMap components = ComponentMapArgumentType.getComponentMap(ctx, "component");
                 MergedComponentMap stackComponents = (MergedComponentMap) stack.getComponents();
 
                 DataResult<Unit> dataResult = ItemStack.validateComponents(components);
                 dataResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);
 
                 ComponentChanges.Builder changesBuilder = ComponentChanges.builder();
                 Set<ComponentType<?>> types = stackComponents.getTypes();
 
                 //set changes
                 for (Component<?> entry : components) {
                     changesBuilder.add(entry);
                     types.remove(entry.type());
                 }
 
                 //remove the rest
                 for (ComponentType<?> type : types) {
                     changesBuilder.remove(type);
                 }
 
                 stackComponents.applyChanges(changesBuilder.build());
 
                 setStack(stack);
             }
 
             return SINGLE_SUCCESS;
         })));
 
         builder.then(literal("remove").then(argument("component", RegistryKeyArgumentType.registryKey(RegistryKeys.DATA_COMPONENT_TYPE)).executes(ctx -> {
             ItemStack stack = mc.player.getInventory().getSelectedStack();
 
             if (validBasic(stack)) {
                 @SuppressWarnings("unchecked")
                 RegistryKey<ComponentType<?>> componentTypeKey = (RegistryKey<ComponentType<?>>) ctx.getArgument("component", RegistryKey.class);
 
                 ComponentType<?> componentType = Registries.DATA_COMPONENT_TYPE.get(componentTypeKey);
 
                 MergedComponentMap components = (MergedComponentMap) stack.getComponents();
                 components.applyChanges(ComponentChanges.builder().remove(componentType).build());
 
                 setStack(stack);
             }
 
             return SINGLE_SUCCESS;
         }).suggests((ctx, suggestionsBuilder) -> {
             ItemStack stack = mc.player.getInventory().getSelectedStack();
             if (stack != ItemStack.EMPTY) {
                 ComponentMap components = stack.getComponents();
                 String remaining = suggestionsBuilder.getRemaining().toLowerCase(Locale.ROOT);
 
                 CommandSource.forEachMatching(components.getTypes().stream().map(Registries.DATA_COMPONENT_TYPE::getEntry).toList(), remaining, entry -> {
                     if (entry.getKey().isPresent()) return entry.getKey().get().getValue();
                     return null;
                 }, entry -> {
                     ComponentType<?> dataComponentType = entry.value();
                     if (dataComponentType.getCodec() != null) {
                         if (entry.getKey().isPresent()) {
                             suggestionsBuilder.suggest(entry.getKey().get().getValue().toString());
                         }
                     }
                 });
             }
 
             return suggestionsBuilder.buildFuture();
         })));
 
         builder.then(literal("get").executes(context -> {
             DataCommandObject dataCommandObject = new EntityDataObject(mc.player);
             NbtPathArgumentType.NbtPath handPath = NbtPathArgumentType.NbtPath.parse("SelectedItem");
 
             MutableText text = Text.empty().append(copyButton);
 
             try {
                 List<NbtElement> nbtElement = handPath.get(dataCommandObject.getNbt());
                 if (!nbtElement.isEmpty()) {
                     text.append(" ").append(NbtHelper.toPrettyPrintedText(nbtElement.getFirst()));
                 }
             } catch (CommandSyntaxException e) {
                 text.append("{}");
             }
 
             info(text);
 
             return SINGLE_SUCCESS;
         }));
 
         builder.then(literal("copy").executes(context -> {
             DataCommandObject dataCommandObject = new EntityDataObject(mc.player);
             NbtPathArgumentType.NbtPath handPath = NbtPathArgumentType.NbtPath.parse("SelectedItem");
 
             MutableText text = Text.empty().append(copyButton);
             String nbt = "{}";
 
             try {
                 List<NbtElement> nbtElement = handPath.get(dataCommandObject.getNbt());
                 if (!nbtElement.isEmpty()) {
                     text.append(" ").append(NbtHelper.toPrettyPrintedText(nbtElement.getFirst()));
                     nbt = nbtElement.getFirst().toString();
                 }
             } catch (CommandSyntaxException e) {
                 text.append("{}");
             }
 
             mc.keyboard.setClipboard(nbt);
 
             text.append(" data copied!");
             info(text);
 
             return SINGLE_SUCCESS;
         }));
 
         builder.then(literal("count").then(argument("count", IntegerArgumentType.integer(-127, 127)).executes(context -> {
             ItemStack stack = mc.player.getInventory().getSelectedStack();
 
             if (validBasic(stack)) {
                 int count = IntegerArgumentType.getInteger(context, "count");
                 stack.setCount(count);
                 setStack(stack);
                 info("Set mainhand stack count to %s.", count);
             }
 
             return SINGLE_SUCCESS;
         })));
 
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

                    // Use RegistryOps.of for registry-aware parsing (Fix for enchantments etc.)
                    var registryOps = RegistryOps.of(NbtOps.INSTANCE, mc.world.getRegistryManager());
                    DataResult<ComponentMap> parseResult = ComponentMap.CODEC.parse(registryOps, componentsNbt);

                    ComponentMap newComponents = parseResult.getOrThrow(errorMsg ->
                        new CommandSyntaxException(null, Text.literal("Failed to parse components NBT: " + errorMsg))
                    );

                    // Validate the components from the clipboard
                    DataResult<Unit> validationResult = ItemStack.validateComponents(newComponents);
                    validationResult.getOrThrow(MALFORMED_ITEM_EXCEPTION::create);

                    // *** FIX FOR TEXTURES: Apply components from clipboard, merging with existing ***
                    // This replaces the old logic that removed all existing components first.
                    // It effectively overwrites components present in 'newComponents'
                    // while leaving other existing components (like texture/model data) intact.
                    stack.applyComponentsFrom(newComponents);

                    // Update the item on the server
                    setStack(stack);

                    info("Successfully loaded and merged NBT data from clipboard to the held item.");

                } catch (CommandSyntaxException e) {
                    error("Failed to load NBT: %s", e.getMessage());
                } catch (Exception e) {
                    error("An unexpected error occurred: %s", e.getMessage());
                }
            }

            return SINGLE_SUCCESS;
        }));


     }
 
     private void setStack(ItemStack stack) {
         mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + mc.player.getInventory().getSelectedSlot(), stack));
     }
 
     private boolean validBasic(ItemStack stack) {
         if (!mc.player.getAbilities().creativeMode) {
             error("Creative mode only.");
             return false;
         }

         if (stack == ItemStack.EMPTY) {
             error("You must hold an item in your main hand.");
             return false;
         }
         return true;
     }
 }
 