package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class RenameCommand extends Command {

    private static final Set<String> ALL_COLORS = Arrays.stream(Formatting.values())
        .filter(Formatting::isColor)
        .map(Formatting::getName)
        .collect(Collectors.toSet());

    private static final Set<String> ALL_STYLES = Set.of("bold", "italic", "underline", "strikethrough", "obfuscated");

    private static final SuggestionProvider<CommandSource> ARGUMENT_SUGGESTIONS = (context, builder) -> {
        String input = builder.getInput();
        int commandEndIndex = input.toLowerCase().indexOf("rename ") + "rename ".length();
        String argsString = input.substring(commandEndIndex);

        int commaIndex = argsString.indexOf(",");
        String formattingPart;
        int formattingStartIndex;

        if (commaIndex != -1) {
            // Comma exists: Format args are after comma
            formattingPart = argsString.substring(commaIndex + 1);
            formattingStartIndex = commandEndIndex + commaIndex + 1;
        } else {
            // No comma: Check if first word looks like formatting
            String firstWord = argsString.split("\\s+", 2)[0].toLowerCase();
            if (ALL_COLORS.contains(firstWord) || ALL_STYLES.contains(firstWord) || argsString.trim().isEmpty()) {
                // Assume format-only mode (or empty args)
                formattingPart = argsString;
                formattingStartIndex = commandEndIndex;
            } else {
                // Assume name-only mode (no formatting suggestions)
                return Suggestions.empty();
            }
        }

        String[] parts = formattingPart.trim().toLowerCase().split("\\s+");
        Set<String> usedStyles = new HashSet<>();
        boolean colorSpecified = false;

        // Determine context based on existing parts
        if (!formattingPart.trim().isEmpty()) {
            for (String part : parts) {
                 if (ALL_COLORS.contains(part)) {
                    colorSpecified = true;
                    // Don't break, need to collect all used styles
                 } else if (ALL_STYLES.contains(part)) {
                    usedStyles.add(part);
                 }
            }
        }

        // Determine what the user is currently typing
        String currentWord = "";
        int lastSpaceInFormatting = formattingPart.lastIndexOf(" ");
        if (lastSpaceInFormatting == -1) {
             currentWord = formattingPart.trim().toLowerCase();
        } else if (builder.getStart() > formattingStartIndex + lastSpaceInFormatting) {
             currentWord = formattingPart.substring(lastSpaceInFormatting + 1).toLowerCase();
        }

        List<String> suggestions = new ArrayList<>();

        // Suggest colors only if no color has been specified yet
        if (!colorSpecified) {
            for (String color : ALL_COLORS) {
                if (color.startsWith(currentWord)) {
                    suggestions.add(color);
                }
            }
        }

        // Always suggest available styles
        for (String style : ALL_STYLES) {
            if (!usedStyles.contains(style) && style.startsWith(currentWord)) {
                suggestions.add(style);
            }
        }

        // Adjust builder offset
        int currentWordStartOffset = formattingStartIndex + (lastSpaceInFormatting == -1 ? 0 : lastSpaceInFormatting + 1);
        if (formattingPart.length() > 0 && formattingPart.trim().isEmpty()) {
             currentWordStartOffset = formattingStartIndex + formattingPart.length();
        }
        if (argsString.endsWith(" ") && builder.getStart() == input.length()) {
            currentWordStartOffset = input.length();
        }
        currentWordStartOffset = Math.max(formattingStartIndex, currentWordStartOffset);

        SuggestionsBuilder suggestionsBuilder = builder.createOffset(currentWordStartOffset);
        for (String suggestion : suggestions) {
            suggestionsBuilder.suggest(suggestion);
        }

        return suggestionsBuilder.buildFuture();
    };

    public RenameCommand() {
        super("rename", "Renames/formats item. Format: [New Name,] color style...", "rn");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(argument("arguments", StringArgumentType.greedyString())
            .suggests(ARGUMENT_SUGGESTIONS)
            .executes(this::executeRename)
        );
    }

    private int executeRename(CommandContext<CommandSource> context) throws CommandSyntaxException {
        if (!validBasic()) return SINGLE_SUCCESS;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            error("You must hold an item in your main hand.");
            return SINGLE_SUCCESS;
        }

        String arguments = StringArgumentType.getString(context, "arguments").trim();
        String name = null;
        String formattingArgs = "";
        boolean formatOnly = false;

        int commaIndex = arguments.indexOf(",");

        if (commaIndex != -1) {
            name = arguments.substring(0, commaIndex).trim();
            if (commaIndex + 1 < arguments.length()) {
                formattingArgs = arguments.substring(commaIndex + 1).trim();
            }
            if (name.isEmpty()) {
                error("Please provide an item name before the comma.");
                return SINGLE_SUCCESS;
            }
        } else {
            String firstWord = arguments.split("\\s+", 2)[0].toLowerCase();
            if (arguments.isEmpty() || ALL_COLORS.contains(firstWord) || ALL_STYLES.contains(firstWord)) {
                formatOnly = true;
                formattingArgs = arguments;
                Text currentNameText = stack.get(DataComponentTypes.CUSTOM_NAME);
                if (currentNameText != null) {
                    name = currentNameText.getString();
                } else {
                     error("Item has no existing custom name to reformat. Use 'New Name,' syntax.");
                     return SINGLE_SUCCESS;
                }
            } else {
                name = arguments;
                formattingArgs = "";
                if (name.contains(" ")) {
                    error("Item name cannot contain spaces unless followed by a comma and formatting. Format: 'New Name, color style...' or 'SingleWordName'.");
                    return SINGLE_SUCCESS;
                }
            }
        }

        if (name == null || name.isEmpty()) {
             error("Could not determine item name.");
             return SINGLE_SUCCESS;
        }

        Formatting color = Formatting.WHITE;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;
        boolean obfuscated = false;
        boolean colorSet = false;

        if (!formattingArgs.isBlank()) {
            String[] parts = formattingArgs.trim().toLowerCase().split("\\s+");
            for (String part : parts) {
                Formatting f = Formatting.byName(part);
                if (f != null && f.isColor() && !colorSet) {
                    color = f;
                    colorSet = true;
                } else if ("bold".equals(part)) bold = true;
                else if ("italic".equals(part)) italic = true;
                else if ("underline".equals(part)) underline = true;
                else if ("strikethrough".equals(part)) strikethrough = true;
                else if ("obfuscated".equals(part)) obfuscated = true;
            }
        }

        Style style = Style.EMPTY.withColor(color).withBold(bold).withItalic(italic).withUnderline(underline).withStrikethrough(strikethrough).withObfuscated(obfuscated);
        MutableText nameText = Text.literal(name).setStyle(style);

        ItemStack modifiedStack = stack.copy();
        modifiedStack.set(DataComponentTypes.CUSTOM_NAME, nameText);
        modifiedStack.set(DataComponentTypes.ITEM_NAME, nameText);

        setStack(modifiedStack);

        if (formatOnly && !arguments.isEmpty()) {
             info(Text.literal("Reformatted item to ").append(nameText).append("."));
        } else {
             info(Text.literal("Renamed item to ").append(nameText).append("."));
        }

        return SINGLE_SUCCESS;
    }

    private void setStack(ItemStack stack) {
        if (mc.player != null && mc.player.getAbilities().creativeMode) {
            int slot = mc.player.getInventory().getSelectedSlot();
            mc.player.networkHandler.sendPacket(new CreativeInventoryActionC2SPacket(36 + slot, stack));
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

