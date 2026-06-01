# Shulker Inventory

A Fabric mod for Minecraft that lets you open and rearrange the contents of a shulker box
straight from your inventory, without ever placing it down.

## What you can do

- Right-click a shulker box in your inventory to open it, just like opening a chest.
- Move items in and out with all the usual controls: left and right click, shift-click,
  drag, hotbar number keys, and drop.
- Close it with Escape, or by right-clicking the same shulker again; the contents are saved safely back into it.
- While a shulker is open, right-click another one to switch straight to it.
- Hear the familiar shulker open and close sounds.
- Watch the shulker lid smoothly animate open and closed; the animation follows the shulker through any state it can be in.

Everything is handled by the server, so your items can never be duplicated.

## Versions

### 1.0.7
Better compatibility with players who do not have the mod: an open shulker box no longer stops them from joining the server, and on a server without the mod you now get a short message saying so while shulker boxes keep their normal right-click behavior. Open and close sounds are more consistent now: you always hear your own, and other players hear them only for a shulker you are visibly holding. Also fixes several glitches: a phantom close sound, a shulker that could look stuck open to other players, creative item loss or duplication when leaving a shulker, and a brief visual double when placing a shulker back at the instant its lid finished closing.

### 1.0.6
When you open a shulker box held in your hand, other players can now see its lid animate too.

### 1.0.5
Fixed a creative-mode duplication that could happen when picking a shulker box onto the cursor and then closing the inventory. Fixed a multiplayer visual glitch where a transferred shulker box could appear stuck open for another player.

### 1.0.4
Closing a shulker box now plays the shulker box's own close sound, matching the lid animation, instead of the shorter shulker creature sound.

### 1.0.3
Fixed an item duplication bug in creative mode that could occur when closing a shulker box. Survival mode was never affected.

### 1.0.2
On a server that does not have the mod installed, right-clicking a shulker box now keeps its normal behavior (you can pick it up) instead of doing nothing.

### 1.0.1
Shulker boxes added by other mods are now recognized (anything in the shulker boxes tag). Switching between shulkers and closing them is more reliable in edge cases.

### 1.0.0
First stable release. Opening a held shulker no longer jolts your hand, so the animation stays smooth everywhere.

### 0.9.3-beta
The lid animation now also plays on shulkers dropped in the world.

### 0.9.2-beta
Added the smooth shulker lid open and close animation in the inventory.

### 0.9.0-beta
First playable release: open a shulker from your inventory, rearrange its contents, switch
between shulkers, with open and close sounds.
