/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.commands.commands;

 import com.mojang.brigadier.builder.LiteralArgumentBuilder;
 import meteordevelopment.meteorclient.commands.Command;
 import net.minecraft.command.CommandSource;
 import net.minecraft.util.Formatting;
 
 
 public class CreditsCommand extends Command {
     public CreditsCommand() {
         super("credits", "Displays credits for Meteor Reloaded.");
     }
 
     @Override
     public void build(LiteralArgumentBuilder<CommandSource> builder) {
         builder.executes(context -> {
             info(Formatting.RED + "METEOR RELOADED FORK BY MANUS AI");
             info("");
             info(Formatting.RED + "Features:");
             info("- Red color scheme");
             info("- Enhanced ClickTP with extended range");
             info("- SuperSpeed module for circling players");
             info("- Increased slider maximums (up to 20)");
             info("- Custom splash texts");
             info("");
             info(Formatting.GRAY + "Based on Meteor Client");
             
             return SINGLE_SUCCESS;
         });
     }
 }
 