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
  - **GODMODE** - automatically splashes health pots when your health gets too low (configurable)
  - **SAVE DEATH** - automatically copies coords of death spot to clipboard
  - **Stay Behind** - locks you behind a target so you can hit them when they cant hit you

## New Commands

- `.rename` – rename items with creative flair options
- `.nbt load` – load NBT data into items
- `.nbt loadinvis` – load NBT and make item invisible
- `.kit` – gives predefined kits (easy to extend with wrapper)- added custom kitloading support
- `.repair` - repairs a tool (creative mode only)
- `.coords` - returns location
- `.surface` - teleports you to the nearest surface above you
- `.tp` - uses pathfinding to teleport to a player up to 120 blocks away

## Building

```bash
git clone https://github.com/johnnyshelby123/Meteor-Reloaded.git
cd Meteor-Reloaded
./gradlew build