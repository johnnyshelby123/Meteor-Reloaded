/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.mixin;

 import meteordevelopment.meteorclient.systems.config.Config;
 import net.minecraft.client.gui.screen.SplashTextRenderer;
 import net.minecraft.client.resource.SplashTextResourceSupplier;
 import org.spongepowered.asm.mixin.Mixin;
 import org.spongepowered.asm.mixin.Unique;
 import org.spongepowered.asm.mixin.injection.At;
 import org.spongepowered.asm.mixin.injection.Inject;
 import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
 
 import java.util.List;
 import java.util.Random;
 
 @Mixin(SplashTextResourceSupplier.class)
 public abstract class SplashTextResourceSupplierMixin {
     @Unique
     private static final Random random = new Random(); // Made static as it's used by a static method or should be shared
     @Unique
     private final List<String> meteorSplashes = getMeteorSplashes(); // Keep as instance field if getMeteorSplashes() is instance or called from instance context
 
     @Inject(method = "get", at = @At("HEAD"), cancellable = true)
     private void onApply(CallbackInfoReturnable<SplashTextRenderer> cir) {
         if (Config.get() == null || !Config.get().titleScreenSplashes.get()) return;
 
         // 75% chance to show custom splash, 25% for default
         if (random.nextInt(100) < 75) { // Numbers 0-74 (75 numbers) will result in custom splash
             if (!meteorSplashes.isEmpty()) { // Ensure the list is not empty
                 cir.setReturnValue(new SplashTextRenderer(meteorSplashes.get(random.nextInt(meteorSplashes.size()))));
             }
         }
         // If the condition (random.nextInt(100) < 75) is false, or meteorSplashes is empty,
         // then we don't call cir.setReturnValue(), allowing the original method to proceed
         // and show a default Minecraft splash text.
     }

    @Unique
     private static List<String> getMeteorSplashes() {
         return List.of(
                 "Meteor Reloaded!",
                 "Star Meteor Reloaded on GitHub!",
                 "Based utility mod.",
                 "§6Johnny §fbased god",
                 "§4Pirating is morally justified!",
                 "§4IM bout to CRASH OUT",
                 "§6Meteor on Crack!",
                 "Once you go black, YOU NEVER GO BACK!",
                 "I aint gay but $20 is $20",
                 "Aint no party like a Diddy party! ",
                 "Still not a virus!",
                 "Your PC's favorite client!",
                 "Now with more features!",
                 "Get good, get Meteor!",
                 "§cCracked by the best!",
                 "§eDon't tell Mojang!",
                 "Oy Vey, mr Goldstein",
                 "§bAll lives Matter",
                 "§aGreen text > everything",
                 "§dSus among us?",
                 "§9Join the Discord!",
                 "The cake is a lie.",
                 "Do a barrel roll!",
                 "It's over 9000!"
         );
     }
 
 }

