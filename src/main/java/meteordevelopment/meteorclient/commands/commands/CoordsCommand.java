package meteordevelopment.meteorclient.commands.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class CoordsCommand extends Command {
    public CoordsCommand() {
        super("coords", "Displays your current coordinates.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null) {
                error("Player not available.");
                return 0;
            }

            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();

            String coordinates = String.format("X: %.2f, Y: %.2f, Z: %.2f", x, y, z);
            // Using the info() method from the base Command class to send the message
            info(Text.literal(coordinates).formatted(Formatting.YELLOW));

            return SINGLE_SUCCESS;
        });
    }
}

