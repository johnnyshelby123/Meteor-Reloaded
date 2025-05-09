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
     private boolean override = true;
     @Unique
     private static final Random random = new Random();
     @Unique
     private final List<String> meteorSplashes = getMeteorSplashes();
 
     @Inject(method = "get", at = @At("HEAD"), cancellable = true)
     private void onApply(CallbackInfoReturnable<SplashTextRenderer> cir) {
         if (Config.get() == null || !Config.get().titleScreenSplashes.get()) return;
 
         if (override) cir.setReturnValue(new SplashTextRenderer(meteorSplashes.get(random.nextInt(meteorSplashes.size()))));
         override = !override;
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
                 "Aint no party like a Diddy party! "
         );
     }
 
 }
