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
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

// Added multi-line support using semicolon (;) separator
// Added "clear" subcommand
public class SetLoreCommand extends Command {

    private static final Set<String> ALL_COLORS = Arrays.stream(Formatting.values())
        .filter(Formatting::isColor)
        .map(Formatting::getName)
        .collect(Collectors.toSet());

    private static final Set<String> ALL_STYLES = Set.of("bold", "italic", "underline", "strikethrough", "obfuscated");

    // Suggestion provider might be less accurate with multi-line format, focuses on the last segment's formatting
    private static final SuggestionProvider<CommandSource> ARGUMENT_SUGGESTIONS = (context, builder) -> {
        String input = builder.getInput();
        int commandEndIndex = input.toLowerCase().indexOf("setlore ") + "setlore ".length();
        if (commandEndIndex == "setlore ".length() -1) { // command not found, try alias
             commandEndIndex = input.toLowerCase().indexOf("lore ") + "lore ".length();
        }
        if (commandEndIndex < 0) return Suggestions.empty(); // Neither command name found

        String argsString = input.substring(commandEndIndex);

        // Don't suggest if the argument is "clear"
        if (argsString.trim().equalsIgnoreCase("clear")) {
            return Suggestions.empty();
        }

        // Find the last semicolon to determine the current line segment
        int lastSemicolon = argsString.lastIndexOf(";");
        String currentSegment = (lastSemicolon == -1) ? argsString : argsString.substring(lastSemicolon + 1);
        int segmentStartIndex = (lastSemicolon == -1) ? commandEndIndex : commandEndIndex + lastSemicolon + 1;

        int commaIndex = currentSegment.indexOf(",");
        String formattingPart;
        int formattingStartIndexInSegment;

        if (commaIndex != -1) {
            // Comma exists: Format args are after comma in the current segment
            formattingPart = currentSegment.substring(commaIndex + 1);
            formattingStartIndexInSegment = commaIndex + 1;
        } else {
            // No comma in the current segment, no formatting suggestions for this segment
            return Suggestions.empty();
        }

        int absoluteFormattingStartIndex = segmentStartIndex + formattingStartIndexInSegment;

        String[] parts = formattingPart.trim().toLowerCase().split("\\s+");
        Set<String> usedStyles = new HashSet<>();
        boolean colorSpecified = false;

        // Determine context based on existing parts in the current formatting part
        if (!formattingPart.trim().isEmpty()) {
            for (String part : parts) {
                 if (ALL_COLORS.contains(part)) {
                    colorSpecified = true;
                 } else if (ALL_STYLES.contains(part)) {
                    usedStyles.add(part);
                 }
            }
        }

        // Determine what the user is currently typing within the formatting part
        String currentWord = "";
        int lastSpaceInFormatting = formattingPart.lastIndexOf(" ");
        if (lastSpaceInFormatting == -1) {
             currentWord = formattingPart.trim().toLowerCase();
        } else if (builder.getStart() > absoluteFormattingStartIndex + lastSpaceInFormatting) {
             currentWord = formattingPart.substring(lastSpaceInFormatting + 1).toLowerCase();
        }

        List<String> suggestions = new ArrayList<>();

        // Suggest colors only if no color has been specified yet in this segment
        if (!colorSpecified) {
            for (String color : ALL_COLORS) {
                if (color.startsWith(currentWord)) {
                    suggestions.add(color);
                }
            }
        }

        // Always suggest available styles for this segment
        for (String style : ALL_STYLES) {
            if (!usedStyles.contains(style) && style.startsWith(currentWord)) {
                suggestions.add(style);
            }
        }

        // Adjust builder offset for the current word within the formatting part
        int currentWordStartOffset = absoluteFormattingStartIndex + (lastSpaceInFormatting == -1 ? 0 : lastSpaceInFormatting + 1);
         if (formattingPart.length() > 0 && formattingPart.trim().isEmpty() && !formattingPart.endsWith(" ")) {
             // If formatting part has content but is just spaces (and not ending with one), suggest at the end
             currentWordStartOffset = absoluteFormattingStartIndex + formattingPart.length();
         }
        if (currentSegment.endsWith(" ") && builder.getStart() == input.length()) {
            // If the segment ends with a space, suggest from the current cursor position
            currentWordStartOffset = input.length();
        }
        // Ensure offset is not before the start of the formatting section
        currentWordStartOffset = Math.max(absoluteFormattingStartIndex, currentWordStartOffset);

        SuggestionsBuilder suggestionsBuilder = builder.createOffset(currentWordStartOffset);
        for (String suggestion : suggestions) {
            suggestionsBuilder.suggest(suggestion);
        }

        return suggestionsBuilder.buildFuture();
    };

    public SetLoreCommand() {
        // Updated description for multi-line support and clear command
        super("setlore", "Sets, formats, or clears item lore. Use semicolon to separate lines. Format: <Line1 Text>[, color style...]; <Line2 Text>[, color style...] | clear", "lore");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        // Argument for setting/formatting lore
        builder.then(argument("arguments", StringArgumentType.greedyString())
            .suggests(ARGUMENT_SUGGESTIONS)
            .executes(this::executeSetLore)
        );

        // Subcommand to clear lore
        builder.then(literal("clear")
            .executes(this::executeClearLore)
        );
    }

    private int executeClearLore(CommandContext<CommandSource> context) throws CommandSyntaxException {
        if (!validBasic()) return SINGLE_SUCCESS;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            error("You must hold an item in your main hand.");
            return SINGLE_SUCCESS;
        }

        // Check if the item actually has lore to remove
        if (stack.get(DataComponentTypes.LORE) == null) {
            info("Item does not have any lore to clear.");
            return SINGLE_SUCCESS;
        }

        ItemStack modifiedStack = stack.copy();

        // Remove the lore component
        modifiedStack.remove(DataComponentTypes.LORE);

        setStack(modifiedStack);

        info("Cleared lore from the held item.");
        return SINGLE_SUCCESS;
    }

    private int executeSetLore(CommandContext<CommandSource> context) throws CommandSyntaxException {
        if (!validBasic()) return SINGLE_SUCCESS;

        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            error("You must hold an item in your main hand.");
            return SINGLE_SUCCESS;
        }

        String allArguments = StringArgumentType.getString(context, "arguments").trim();

        // Prevent accidental clearing if user types "clear" without the subcommand structure
        if (allArguments.equalsIgnoreCase("clear")) {
             error("To clear lore, use '.setlore clear'.");
             return SINGLE_SUCCESS;
        }

        if (allArguments.isEmpty()) {
            error("Please provide lore text or use '.setlore clear'.");
            return SINGLE_SUCCESS;
        }

        List<Text> loreLines = new ArrayList<>();
        String[] lineSegments = allArguments.split(";");

        for (String segment : lineSegments) {
            segment = segment.trim();
            if (segment.isEmpty()) {
                // Allow empty lines by adding an empty text component
                loreLines.add(Text.literal(""));
                continue;
            }

            String loreTextStr;
            String formattingArgs = "";
            int commaIndex = segment.indexOf(",");

            if (commaIndex != -1) {
                loreTextStr = segment.substring(0, commaIndex).trim();
                if (commaIndex + 1 < segment.length()) {
                    formattingArgs = segment.substring(commaIndex + 1).trim();
                }
                if (loreTextStr.isEmpty() && !formattingArgs.isEmpty()) {
                     loreTextStr = "";
                } else if (loreTextStr.isEmpty()) {
                     error("Please provide lore text before the comma in segment: ", segment);
                     return SINGLE_SUCCESS;
                }
            } else {
                loreTextStr = segment;
                formattingArgs = "";
            }

            Formatting color = Formatting.WHITE; // Default color for each line
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
            MutableText loreLineText = Text.literal(loreTextStr).setStyle(style);
            loreLines.add(loreLineText);
        }

        ItemStack modifiedStack = stack.copy();
        LoreComponent loreComponent = new LoreComponent(loreLines);
        modifiedStack.set(DataComponentTypes.LORE, loreComponent);
        setStack(modifiedStack);

        info("Set item lore (%d lines).", loreLines.size());
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

