/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

 package meteordevelopment.meteorclient.systems.modules.player;

 import meteordevelopment.meteorclient.systems.modules.Categories;
 import meteordevelopment.meteorclient.systems.modules.Module;
 
 public class Freeze extends Module {
     public Freeze() {
         super(Categories.Player, "freeze", "Instantly stops all player momentum.");
     }
 
     @Override
     public void onActivate() {
         if (mc.player != null) {
             // Set velocity to zero in all axes
             mc.player.setVelocity(0, 0, 0);
         }
         
         // Instantly disable the module after activation
         toggle();
     }
 }
 
 