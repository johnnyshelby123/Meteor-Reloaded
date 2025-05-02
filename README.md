# Meteor Reloaded

A fork of Meteor Client with new modules, commands, and NBT enhancements.

## Features

- Prefilled kits for quick usage
- New modules:
  - **Superman Flight** – free 3D movement
  - **Aquaman** – super speed in water
  - **TP Aura** – rapidly teleport around players (6 PvP modes)
  - **Gravity Control** – adjust gravity strength
  - **Spin** – adds a spin animation
  - **Bouncy Boots** – makes blocks/entities bouncy (configurable with slider)

## New Commands

- `.rename` – rename items with creative flair options
- `.nbt load` – load NBT data into items
- `.nbt loadinvis` – load NBT and make item invisible
- `.kit` – gives predefined kits (easy to extend with wrapper)

## Building

```bash
git clone https://github.com/johnnyshelby123/Meteor-Reloaded.git
cd Meteor-Reloaded
./gradlew build