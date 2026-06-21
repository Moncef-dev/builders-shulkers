# Builder's Shulkers

A Fabric mod for Minecraft that lets you use a shulker box's contents without ever placing it
down: open and rearrange it straight from your inventory like a chest, and place or use its
items from your hotbar while you build.

## Links
- Youtube showcase: https://www.youtube.com/watch?v=VknrkiXw_Fk
- CurseForge: https://www.curseforge.com/minecraft/mc-mods/builders-shulkers
- Modrinth: https://modrinth.com/mod/builders-shulkers

## What you can do

### Open a shulker from your inventory
- Right-click a shulker box in your inventory to open it, just like opening a chest.
- Move items in and out with all the usual controls: left and right click, shift-click, drag, hotbar number keys, and drop.
- Close it with Escape, or by right-clicking the same shulker again; the contents are saved safely back into it.
- While a shulker is open, right-click another one to switch straight to it.
- Hear the familiar open and close sounds, and watch the lid smoothly animate open and closed (the animation follows the shulker through any state it can be in).

### Build from a shulker in your hotbar (Pocket-Build)
- Hold a shulker box in your hotbar and Ctrl + right-click to enter Pocket-Build mode, instead of placing the box.
- Scroll the mouse wheel to select the previous/next different block inside the box; its name shows above your hotbar, its count shows on the slot, and the box opens with a dissolving lid that reveals the selected block drawn inside it.
- Middle-click a block you already have in the box to select it straight away; if it is not in the box, middle-click takes it from your inventory as usual.
- Right-click to place that block straight from the box - on the ground, into an item frame, and so on - while the box stays in your hand. The block drawn inside the box follows it in your hand and on the ground.
- Pocket-Build places blocks; a non-block selection does nothing, so the box stays a pocket of building blocks.
- Hold Ctrl to peek at the box's full contents in an overlay above your hotbar.

Everything is handled by the server, so your items can never be duplicated.

### Customization
- Press B (rebindable) to open the settings screen and tune the cosmetics: lid animations, the lid effect (dissolve, disappear, or stay solid), the block drawn inside the box and its count, the open/close sounds, and more.
- Every control lives in Options -> Controls under "Builder's Shulkers" and can be rebound or unbound: the Pocket-Build hold key (default Left Control), a peek key (default Left Control), and a single-press "Toggle Pocket-Build" (unbound by default) to enter and leave the mode with one button (handy on a controller or on touch).
- Server admins can turn the access from inventory and Pocket-Build features on or off with gamerules.

## Versions

### 1.2.0
**A settings screen.** Press B (rebindable) to open Builder's Shulkers settings and tune the look and feel: turn the inventory and Pocket-Build lid animations on or off, choose how the Pocket-Build lid clears (dissolve, vanish, or a plain solid lid), show or hide the block rendered inside the box and its count, resize the item count, toggle the open/close sounds, and enable/disable other player's shulker animations in multiplayer.

**Rebindable controls.** Every control now lives in Options -> Controls under "Builder's Shulkers" and can be rebound or unbound: the Pocket-Build hold key (default Left Control), a peek key (default Left Control), and a single-press "Toggle Pocket-Build" (unbound by default) so you can enter and leave the mode with one button (handy for a controller or on touch).

**Server gamerules.** Admins can switch the access from inventory and Pocket-Build features on or off per world.

**Wider version support.** The 26.1.2 build now also runs on Minecraft 26.1 and 26.1.1.

Pocket-Build now enters and leaves only on the actual right-click, so pressing the hold key while right click is pressed no longer flips the mode.

** **

### 1.1.5
The mod now runs on Minecraft 26.2 as well as 26.1.2.

If you also have Litematica installed, middle-clicking one of its schematic ghost blocks while in Pocket-Build selects that block from the shulker you are holding, the same way middle-clicking a real block does (if you do not have it in the box, it is picked from your inventory as usual).

** **

### 1.1.4
Shulker boxes added by other mods that hold more than the usual 27 slots no longer lose the items in those extra slots when opened through this mod. Only the first 27 slots are shown; the rest are left untouched.

** **

### 1.1.3
In Pocket-Build, scrolling now jumps to the next or previous DIFFERENT block instead of stepping through every stack one by one. Stacks of the same block are skipped, so switching blocks stays fast even when your shulker holds many stacks of the same item. Scrolling back lands on the first of those stacks.

** **

### 1.1.2
**Pocket-Build now supports middle-click.** Middle-clicking a block you already have in the box selects it straight away, so you stay in the mode and keep building; if the block is not in the box, middle-click works as before and takes it from your inventory.

**Count on the held box.** The shulker's hotbar slot now shows how many of the selected block you have, in the corner like a normal stacked item.

** **

### 1.1.1
**Content inside the box.** The shulker now shows the block you have selected drawn inside it while you build (in your hand and dropped on the ground), and its lid dissolves open to reveal it. Other players also see that full animation on a shulker you hold: the dissolving lid and the block inside, updating as you build, not just a basic opening lid.

**Pocket-Build.** It now places blocks specifically (a non-block selection does nothing), and the Ctrl peek shows each item's full details (count, durability bar, cooldown). Leaving the mode (Ctrl + right-click, or opening a chest) now works whatever you have selected; picking a block (middle-click), dying, and dropping the box now exit the mode cleanly; and scrolling after emptying a slot continues to the next item.

**Animations and sounds.** Lid animations now pause with the game. Those shown to other players are more reliable too: an animation that starts out of sight still shows once the box comes into view, a quick re-open resumes from where the lid was instead of snapping shut, and dropping an open shulker now plays its closing sound for them.

Renamed Builder's Shulkers (previously Shulker Inventory).

** **

### 1.1.0
New Pocket-Build mode: hold a shulker box in your hotbar and Ctrl + right-click to build straight from it without placing it down. Scroll to pick an item, right-click to place or use it (blocks, item frames, and more), and hold Ctrl to peek at the box's contents. The held box's lid animates open while you build.

** **

### 1.0.8
Quickly reopening a shulker box now resumes its lid animation from where it was, instead of briefly snapping the lid shut before opening again.

** **

### 1.0.7
**Compatibility with players who do not have the mod.** An open shulker box no longer stops them from joining the server, and on a server without the mod you now get a short message saying so, while shulker boxes keep their normal right-click behavior.

**Sounds.** Open and close sounds are more consistent: you always hear your own, and other players hear them only for a shulker you are visibly holding.

**Fixes.** A phantom close sound; a shulker that could look stuck open to other players; creative item loss or duplication when leaving a shulker; and a brief visual double when placing a shulker back at the instant its lid finished closing.

** **

### 1.0.6
When you open a shulker box held in your hand, other players can now see its lid animate too.

** **

### 1.0.5
Fixed a creative-mode duplication that could happen when picking a shulker box onto the cursor and then closing the inventory. Fixed a multiplayer visual glitch where a transferred shulker box could appear stuck open for another player.

** **

### 1.0.4
Closing a shulker box now plays the shulker box's own close sound, matching the lid animation, instead of the shorter shulker creature sound.

** **

### 1.0.3
Fixed an item duplication bug in creative mode that could occur when closing a shulker box. Survival mode was never affected.

** **

### 1.0.2
On a server that does not have the mod installed, right-clicking a shulker box now keeps its normal behavior (you can pick it up) instead of doing nothing.

** **

### 1.0.1
Shulker boxes added by other mods are now recognized (anything in the shulker boxes tag). Switching between shulkers and closing them is more reliable in edge cases.

** **

### 1.0.0
First stable release. Opening a held shulker no longer jolts your hand, so the animation stays smooth everywhere.

** **

### 0.9.3-beta
The lid animation now also plays on shulkers dropped in the world.

** **

### 0.9.2-beta
Added the smooth shulker lid open and close animation in the inventory.

** **

### 0.9.0-beta
First playable release: open a shulker from your inventory, rearrange its contents, switch
between shulkers, with open and close sounds.

** **
