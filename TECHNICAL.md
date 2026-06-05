# Builder's Shulkers - Technical Documentation (v1.1.1)

Contributor-facing notes on the technical problems this mod solves, the chosen solutions,
their scope, and known risks. Describes the state as shipped in v1.1.1.

## Environment

- Loader: Fabric. Minecraft 26.1.2. JDK 25.
- Mappings: official Mojang names (MC 26.1 ships deobfuscated), not Yarn.
- Split source sets: `client` (rendering, input, screens) and `main` (registration,
  menu, networking). Client code is never called from common code.

## Overview

The mod opens a shulker box's contents in the vanilla chest-style screen directly from the
inventory (right-click), lets the player rearrange items with all vanilla controls, saves on
close, supports switching between shulkers, plays the open/close sounds, and animates the lid
opening in the GUI, on the held item, and on dropped item entities. All item movement is
server-authoritative.

It also adds Pocket-Build mode (1.1.0): hold a shulker in the hotbar and Ctrl + right-click to place or use its
selected content straight from the box, without putting it down (the mouse wheel picks the item). See section 8.

## 1. Reuse of the vanilla ShulkerBoxMenu (no custom type)

`InventoryShulkerBoxMenu extends ShulkerBoxMenu`, backed by a `SimpleContainer(27)` holding a
working copy of the source stack's `minecraft:container` component contents.

- Why: free compatibility with mods that react to the vanilla shulker menu/screen; vanilla
  slot validation (including nested-shulker rejection via `ShulkerBoxSlot`) for free; all
  vanilla shortcuts work without custom code.
- Scope: assumes the vanilla shulker size (27 slots).

## 2. Server-authoritative editing and anti-duplication (commit-on-disturbance)

The worked contents live in the `SimpleContainer`; they are written back into the source
stack's `container` component on each click and on close (`saveContents`).

Anti-dup rule: any interaction that targets the SOURCE shulker stack itself is intercepted in
`InventoryShulkerBoxMenu.clicked` via `touchesSourceSlot` (slot-id match, or a number-key
SWAP onto that slot). The sequence is always: save -> close the session -> replay the player's
action on the inventory menu. The save happens BEFORE the action, so the up-to-date stack is
what gets picked up or relocated.

- Exception (vanilla parity): a drop (`THROW`) on the source slot with a NON-EMPTY cursor is NOT treated as a
  disturbance. Vanilla's `THROW` only drops from the hovered slot when the cursor is empty
  (`AbstractContainerMenu.doClick`, guarded by `getCarried().isEmpty()`), so with a full cursor it is a no-op; the
  click falls through to vanilla and the session stays open instead of closing pointlessly.
- Why it is dup-safe: shulker boxes are non-stackable in vanilla (size 1, empty or filled), so there is no half-stack
  ambiguity, and the contents always travel with the (already-saved) stack.
- `ServerContainerCloseMixin` cancels container-close packets whose id does not match the
  currently open menu, so a late close for a previous menu cannot close a freshly swapped one.
- Cursor handling on close: `closeMenuSilently` transfers the cursor from the shulker menu onto the
  player inventory menu UNCONDITIONALLY, including an empty cursor. This matters because picking the
  source shulker up onto the inventory cursor (the commit-on-disturbance replay) can leave a stale
  cursor stack that creative's set-creative-slot placement never clears. Only re-injecting a non-empty
  cursor would leave that phantom in place; its value never changes, so the game never re-syncs the
  cursor, and in creative (where the client authors its own inventory) the phantom is committed as a
  real duplicate. Transferring unconditionally flushes it and triggers the cursor re-sync.
- Creative cursor reconciliation on close: creative stays special even with the transfer above. In creative
  the cursor is client-authoritative (the client commits slot changes via `SetCreativeModeSlot`), so a creative
  throw or place does NOT clear the server's inventory-menu cursor. A stale cursor left there is re-given by the
  vanilla inventory close, duplicating an item the client already disposed of (for example, picking the source
  shulker onto the cursor, throwing it out, then closing the inventory); and clearing it eagerly at close time
  instead would clobber the client's still-held cursor and lose the item. So at session close
  `InventoryShulkerBoxMenu.dropCreativeCursorShadow` (from `finishReturnToInventory`) drops the server-side cursor
  shadow in creative WITHOUT resyncing: it empties the inventory-menu carried and sets the remote carried to empty,
  so no clearing cursor update is sent and the creative client stays authoritative over the real cursor item.
  Survival is unaffected (the cursor is server-authoritative there and must keep its carried so the vanilla close
  re-gives it).

## 3. Per-shulker identity: the `animation_id` component

The lid animation must follow the SPECIFIC shulker even if it moves between slots, so it is
keyed by a per-shulker identity, never by the slot.

Minecraft items are value objects: an `ItemStack` has no per-instance unique id; equality is
by (item, components). To track one specific shulker, the mod adds a synthetic identity: the
`builders-shulkers:animation_id` data component (a `long`). It is allocated client-side at open as a
globally-unique random long, stamped on the source stack server-side, and network-synchronized so the
client renderer can recognize the animating stack wherever it is drawn (GUI, hand, dropped entity).

- Why a data component, and why PERSISTENT (encodable): a stack that lives in a container is
  hashed during container-click validation (`HashedStack`). A non-encodable (transient)
  component throws `not encodable` and crashes the click handler. So the identity component
  must be encodable, i.e. persistent.
- Why a globally-unique RANDOM long (not a per-client counter): each client tracks its live animations in
  its own map keyed by this id. A per-client sequential counter restarts at the same values on every client,
  so in multiplayer a shulker carrying client A's id could collide with an unrelated animation client B tracks
  under the same id, making B render A's shulker as open. A random 64-bit id makes that collision
  astronomically improbable, and the worst case of a collision is a brief self-healing cosmetic glitch on one
  client (never a duplication). Zero is reserved as the render side channel's "no id" sentinel.
- `.ignoreSwapAnimation()` on the component suppresses the first-person hand swap animation when an
  already-present `animation_id` only changes VALUE. It does NOT cover the component being added or
  removed; that case is handled by `IgnoreAnimationIdSwapMixin` (see section 5 for why both are
  needed).
- The animation state machine (progress, OPENING/OPENED/CLOSING) is purely client-side in
  `ClientShulkerSession`. The marker is never stripped during a session (see section 7 for why); it is
  cleared at the next login.

## 4. Rendering (reuse of vanilla openness)

The lid animation reuses the vanilla `ShulkerBoxSpecialRenderer` `openness` parameter, overridden
by `ShulkerBoxOpennessMixin` (`@ModifyArg`) from the interpolated animation progress. When the
rendered shulker is not one of ours, the mixin returns the original value unchanged, so other
shulkers (and other mods' rendering) are untouched.

The animation id is routed to the renderer through a render-thread-confined side channel in
`ClientShulkerSession` (set by `GuiItemAtlasMixin` / `ItemEntityRendererMixin` / `HeldItemAnimationMixin` /
`FirstPersonHeldItemMixin` around the draw, read by `ShulkerBoxOpennessMixin`). Covers the GUI item icon,
dropped item entities, the item held by an entity in third person (notably another player's hand), and the
local player's own first-person hand. Each shulker is animated only by its own marker through these channels:
there is no broad fallback (so the local player's animation can never bleed onto an unrelated held shulker).

Shared animation (multiplayer). The animation state machine is per-client, so by default only the player who
opened a shulker sees its lid move. To make it visible on a shulker HELD by another player, the server broadcasts
open/close events (`RemoteShulkerAnimationPayload`) to the players who can see the holder, and those clients run the
same animation locally (`startOpeningRemote` / `startClosing`). The broadcast is gated on `ServerPlayNetworking.canSend`
per viewer, so it is never sent to a vanilla client (no mod) or to a viewer that is mid-join. The initiator animates
locally and is excluded; in singleplayer there are no other viewers, so it is a no-op and solo behavior is unchanged.

The animation is broadcast ALWAYS, not only when the box is held at the start: a viewer must hold the state so the lid
is right whenever the box becomes visible (moved to the hotbar, or dropped) part-way through. The SOUND, by contrast,
is gated on VISIBILITY at the event, since it accompanies that instant. An open plays the sound only when the box is
already held. A close plays it when the box is still held OR was just dropped to the world (a visible item entity,
detected by a `ContainerInput.THROW` on the source slot); a grab/swap that moves the box to the cursor or another slot
(invisible to viewers) stays silent. `startOpeningRemote` also RESUMES from the previous animation's openness (tracked
per holder), so a quick re-open continues from where the closing lid was for viewers too, matching the opener
(`beginHeldOpening`). That per-holder tracking also bounds viewer state to one live animation per holder (a new open
drains the holder's previous one) and is cleared on disconnect, so off-view or out-of-hand opens cannot accumulate
orphaned states.

Per-animation lid model (multiplayer). The vanilla `ShulkerBoxModel` is shared per colour and its lid position is
resolved at DRAW, so in the batched world render (held items) the LAST `setupAnim` of the frame wins: a second held
shulker of the same colour, even a static one, would reset an animated box's lid to closed at draw (the dissolve,
captured at submit, would still play, making it obvious). So an ANIMATED box is given its OWN `ShulkerBoxModel`
instance, keyed by animation id and cached on the `AnimationState` (freed when it drains); two animated same-colour
boxes still get distinct models, and static boxes keep the shared vanilla model. The GUI draws each slot immediately
(no batching) and placed shulkers use a different renderer instance, so neither is affected. The same per-instance
technique keeps a NESTED content shulker's lid static (`separateNestedShulkerModel`, keyed by sprite).

## 5. Mixins and injection points

Server (`builders-shulkers.mixins.json`):
- `ServerContainerCloseMixin` -> `ServerGamePacketListenerImpl.handleContainerClose` (HEAD,
  cancellable): drops stale close packets during a menu swap, scoped to our own menu (never affects
  other mods' containers).

Client (`builders-shulkers.client.mixins.json`):
- `ShulkerSlotClickMixin` / `ShulkerCreativeSlotClickMixin` -> `slotClicked` (HEAD, cancellable):
  route a right-click on a shulker to the open handler. The interception is gated on
  `ClientPlayNetworking.canSend(OpenShulkerPayload.TYPE)`: on a server that does not run the mod the
  server cannot receive the open request, so the click is left to vanilla and the shulker keeps its
  normal right-click behavior instead of being a dead click.
- `CreativeSlotWrapperAccessor`: unwrap the creative slot wrapper.
- `ShulkerBoxScreenAssociateMixin` / `ContainerScreenCloseMixin`: tie the open/close animation
  to the shulker screen lifecycle.
- `ShulkerAnimatedMixin` / `GuiItemAtlasMixin` / `ShulkerBoxOpennessMixin`: drive the GUI lid
  openness from animation progress.
- `ItemEntityRenderStateMixin` / `ItemEntityRendererMixin`: animate the lid on dropped shulkers.
- `HeldItemAnimationMixin` -> `ItemInHandLayer.submitArmWithItem` (HEAD/RETURN): publish the animation id
  around the third-person held-item draw so the lid animates on a shulker held by an entity (another player).
- `FirstPersonHeldItemMixin` -> `ItemInHandRenderer.renderItem` (HEAD/RETURN): publish the animation id around
  the local player's own first-person held-item draw, so their held shulker animates in first person. This
  replaces an earlier broad fallback in `ShulkerBoxOpennessMixin` (apply the local player's held animation to
  any shulker with no side channel set), which wrongly rendered other players' held shulkers as open from the
  local player's view.
- `IgnoreAnimationIdSwapMixin` -> `ItemInHandRenderer.shouldInstantlyReplaceVisibleItem` (HEAD,
  cancellable): suppress the hand swap animation when two held stacks differ only by `animation_id`
  being added or removed. This is NOT redundant with the vanilla `.ignoreSwapAnimation()` flag.
  Vanilla decides via `ItemStack.matchesIgnoringComponents`, which first short-circuits to false on
  `components.size()` inequality and only consults the ignore predicate afterwards (at equal size).
  So the flag covers a value change of an already-present component, but a component that is present
  on one stack and absent on the other (opening a shulker adds it; an untouched shulker has none)
  changes the map size and still triggers the swap. This mixin covers exactly that add/remove transition.

## 6. Networking payloads

- `OpenShulkerPayload` (C2S): slot index + animation id (request to open, or toggle closed).
- `OpenPlayerInventoryPayload` (S2C): reopen the player's inventory screen after a session ends.
- `RemoteShulkerAnimationPayload` (S2C, broadcast): mirror a lid animation (open or close) to the players who can
  see the holder, so the lid is visible on the shulker held by another player. Gated by `canSend` per viewer. Carries
  the holder entity id (a mirrored sound plays at the holder's position) and a `playSound` flag, set from VISIBILITY at
  the event (open: held; close: held or dropped to the world), so a close that only moved the box to the cursor or
  another slot is sent silent (the box is invisible to viewers) while a drop keeps its sound.
- `PocketBuildRemoteContentPayload` (S2C, broadcast): the Pocket-Build selected stack to draw inside a held box for
  viewers (animation id, holder entity id, `ItemStack`), so they see the dissolving lid and the block inside it, not
  just the plain lid. Sent on enter, on every scroll, and after each placement, so the block updates as the holder
  builds and renders empty once a slot's last item is placed. Gated by `canSend` per viewer.

## 7. Known limitations and risks (v1.1.1)

- Component-equality divergence (observed, not just theoretical). While `animation_id` is present,
  the shulker is not equal by components to an otherwise identical stack without it. This is a real
  consequence: vanilla's own `ItemStack.matchesIgnoringComponents` (used by the first-person swap
  logic) treats the tagged and untagged shulker as different, which is exactly why
  `IgnoreAnimationIdSwapMixin` has to exist (see section 5). The blast radius stays small because the
  paths most sensitive to component equality (stacking, item merging, ground-entity merging) are
  gated by stackability, and shulker boxes are non-stackable in vanilla, so they never take those
  paths. The marker now persists from the open until the next login (see the next bullet), not only the
  animation window, but for these non-stackable items the divergence stays cosmetic and inert.
- Marker lifetime: retained for the whole session, cleared at login (by design). The lid animation needs a marker
  that lives on the stack for the animation's lifetime, and that marker must be encodable/persistent: a stack
  sitting in a container is hashed during container-click validation, and a transient (non-encodable) component
  throws and crashes the click handler. So the marker is written to the stack by design. We do NOT remove it when
  the animation ends, nor at any other mid-session point (see the next bullet for why), so after a close the
  `animation_id` stays on the shulker. This is harmless: an id with no live animation renders as a closed lid
  (`isAnimating` is false, the renderer falls back to vanilla openness), and the marker stays identical on client
  and server, so container-click hashing still matches. It is cleared at the next login
  (`ServerPlayConnectionEvents.JOIN` strips any leftover from the joining player's inventory; there is never a live
  animation at join time and the whole inventory re-syncs on join, so this is safe), and it is overwritten anyway
  the next time that shulker is opened. Login covers every way a prior session can end (quit, kick, crash) and
  retroactively cleans markers left by earlier sessions.
- Why the marker is not stripped mid-session (this removed a former transient visual duplicate). An earlier build
  cleared the marker the instant the closing animation finished, including off the cursor. That async, server-side
  strip mutates an inventory or cursor stack and emits a slot/cursor update; under load it could land on the same
  client frame as a concurrent click prediction (for example placing the shulker back as the animation ends),
  briefly rendering the shulker BOTH in the slot and on the cursor until the next click reconciled it. It was a
  pure render artifact, observed in both creative and survival: the server stayed authoritative at exactly one copy
  throughout (instrumentation counting the per-shulker marker across the inventory and the cursor never saw a
  marker more than once over a full session, and no path creates a second authoritative stack, so the
  anti-duplication guarantee is intact). Removing all mid-session stripping eliminates the artifact at the source.
  A finer trigger would not help anyway: `ItemStack`s are value objects COPIED on almost every move, so the live
  reference a close-time cleanup would scrub can become a stale copy elsewhere the instant the player relocates the
  shulker, and reliably following that copy would require per-tick world scans or a per-stack identity registry.
  Login is the one cheap, safe cleanup point, and the brief on-stack residue between a close and the next login is
  accepted.
- The render side channel is render-thread-confined; correct but order-sensitive.
- (Resolved in 1.1.1.) An earlier build ran every selected item through vanilla's full use-on flow, so a tool, a
  bucket or a spawn egg used from the box performed its action rather than only placing. Pocket-Build is now restricted
  to block items (section 8), which removes that behavior; the trade-off that block-only rule makes (it excludes the
  placeable non-blocks: buckets, item frames, paintings, boats, spawn eggs, ...) is documented there.

## 8. Pocket-Build mode (1.1.0)

Hold a shulker in the hotbar and Ctrl + right-click to enter Pocket-Build mode; a plain right-click then places
or uses the SELECTED CONTENT of the box, never the box itself, and the mouse wheel cycles the selection. It is
server-authoritative, so nothing can be duplicated.

- Content-swap (the Vanilla+ core). Rather than re-implementing placement conditions (can-place, interact-vs-place,
  adventure-mode permissions, the place sound, orientation, the survival-only consume), the mod briefly swaps the
  selected content into the player's hand and lets the WHOLE vanilla use/interact flow run on it, then restores the
  shulker in a `try/finally` (the held shulker can never be lost mid-swap; anti-dup). Every property emerges from
  vanilla; nothing is re-coded. Two paths are wrapped, on the client (prediction) and the server (authoritative):
  - Block use: `MultiPlayerGameModeMixin` and `ServerPlayerGameModeMixin` (`@WrapMethod` on `useItemOn`).
  - Entity interaction: `MultiPlayerGameModeInteractMixin` (`interact`) and the common `PlayerInteractOnMixin`
    (`Player.interactOn`, run on both sides).
  The server writes the FULL post-use stack back into the box's `container` component (not just the count delta), so
  everything the vanilla flow did to the item is kept: the count decrements in survival and not in creative (emergent
  from `BlockItem.place`, which only shrinks the held stack in survival), and an item the use REPLACES is carried back
  too (placing powder snow empties the bucket to an empty bucket, which an earlier count-only write-back lost). Once a
  gamerule allows non-block use (below), tool durability and consumption are preserved the same way, with no extra code.
- Mode state. Client `PocketBuildMode` holds the active flag, the source hotbar slot, the selected content slot, and
  the animation id; server `PocketBuildServerState` holds, per player in the mode, the selected content slot (so the
  use-on packet swaps in the right content). `isActive` (key present) is kept distinct from `selectedSlot` (which
  returns -1 both for "not in the mode" and "in the mode with nothing selected"), so an empty selection places nothing
  instead of the box.
- Input. `MouseHandlerScrollMixin` reroutes the wheel to cycle the selected NON-EMPTY content slot (cancelling the
  vanilla hotbar scroll); `InventorySelectedSlotMixin` blocks the number-key slot change, scoped to the local player;
  the off-hand swap key (F) is drained each client tick. The selection is synced to the server with
  `PocketBuildSelectPayload`.
- Exit conditions. The mode ends on: Ctrl + right-click again; opening any container UI; the held item ceasing to be a
  shulker (dropped, cleared, replaced); the hotbar selection moving off the source slot; or the player dying
  (`isDeadOrDying`, so a keepInventory death - which neither drops the shulker nor opens a container - cannot leave the
  mode stuck through respawn). The pause menu, options and chat deliberately leave the mode running. Pick-block is a
  special case: it is server-authoritative (the server moves the picked item into a hotbar slot and changes the selected
  slot, which the locked hotbar cannot follow, desyncing the inventory), so `MultiPlayerGameModePickMixin` exits the
  mode at the head of `handlePickItemFromBlock` / `handlePickItemFromEntity`, before the pick runs - which then proceeds
  vanilla and unlocked.
- Animation. Entering and leaving the mode reuse the inventory-open machinery (sections 3-4): the client begins the
  lid animation via the shared `ClientShulkerSession.beginHeldOpening`, which resumes from the shulker's current
  openness on a quick re-enter; the server stamps the `animation_id` marker and broadcasts open/close, so the held
  shulker's lid animates in every render context and on other players' view of the holder. `beginHeldOpening` is
  shared with the inventory open path so the resume behaviour cannot diverge between the two modes.
- Peek overlay and item name (client HUD). While Ctrl is held (after a short debounce, so the quick Ctrl + right-click
  toggle never flashes it), `PocketBuildOverlay` draws the 27 contents as the actual vanilla shulker-box container
  interface above the hotbar (through the 26.1.2 `GuiGraphicsExtractor`), with the selected slot under the vanilla
  container hover highlight. Each item is drawn with vanilla's own `itemDecorations` (count, durability bar, cooldown
  overlay), so every slot subtlety appears exactly as in any container, instead of being redrawn by hand (1.1.1).
  `GuiSelectedItemNameMixin` redirects the held-stack read in `Gui.tick` to the selected content, so the vanilla hotbar
  item-name popup names the content (pop, fade, rarity colour) on each scroll.
- Payloads: `PocketBuildModePayload` (C2S: enter/exit, hotbar slot, animation id, selected content slot) and
  `PocketBuildSelectPayload` (C2S: selected content slot).

### Placement scope: block items only (1.1.1)

Pocket-Build only acts on BLOCK items: a non-block selection (a tool, a bucket, a boat, a spawn egg, ...) does nothing
on right-click. `PocketBuildRules.isUsable` is the single gate, checked by both the server content-swap and the client
prediction. For a non-usable (or empty) selection the swap puts an EMPTY hand in place and STILL runs the vanilla flow
rather than cancelling it: nothing is placed or used (the shulker, kept aside until the finally, is never placed), but
the bare interaction is not swallowed - Ctrl + right-click can still leave the mode and a chest still opens - and the
content slot is left untouched (no write-back). Cancelling the flow instead (an early FAIL) ate the exit click and the
chest interaction whenever a non-usable item was selected.

- Why: Pocket-Build is a pocket of placeable BLOCKS, not a tool/interaction handler. Without the gate, running every
  item through vanilla's use-on flow meant a tool used from the box performed its right-click action (a shovel made a
  path, a hoe tilled, flint and steel lit a fire) and a bucket or spawn egg deployed - the incoherence being that the
  box could trigger half a tool's behavior (its right-click) while it could never do the tool's primary job (mining).
  Restricting to blocks makes the feature coherent: the box places blocks, full stop.
- Why `BlockItem` is the boundary and not a hand-rolled list: it is Mojang's own type for "this item becomes a block
  when placed", so it stays correct for modded and future blocks automatically, and placement itself still runs through
  the unchanged vanilla content-swap. It is a single, stable type check, not a re-coded approximation of "placeable".
- Concession - what a block-only rule excludes. `instanceof BlockItem` is exact for "is a block" but not for "places
  something": the placeable items it leaves out are water/lava buckets, item frames, paintings, armor stands, boats,
  minecarts, end crystals, spawn eggs, leads, flint and steel, fire charge, ender eye, bone meal. These are all
  entities, fluids or tool-style uses - consistent with "build, not interact" - so they are intentionally out of scope.
  (`powder_snow_bucket` is a `BlockItem` and is allowed, since it places a block.)
- Perspective - a gamerule (off by default) is planned to widen the rule to "use any item exactly as if it were in the
  hotbar". Instant uses already emerge from the content-swap (placing, buckets, fire, throwing); continuous uses
  (eating, drinking, drawing a bow) need the content to stay in hand for the use's whole duration, which the current
  instantaneous swap does not do - that is the open design question for the gamerule.

### Content rendering: the selected content inside the box (1.1.1)

The held shulker draws its selected content inside the box, in every render context (inventory slot, first person,
third person, and dropped on the ground), so the player sees what they are about to place. The content is composed into the shulker's OWN item
render state, the vanilla bundle technique: `PocketBuildContentLayerMixin` (`@Inject` at the tail of
`ItemModelResolver.appendItemLayers`) appends the content's layers to that render state and shrinks them inside the box.
One render state means the content shares the box's depth, so it is occluded by the box geometry through the depth test
(it reads as sitting INSIDE the box), with no second pass and no ordering tricks. Other players see the same in-box
content: the holder's selected stack is broadcast to viewers (`PocketBuildRemoteContentPayload`, see sections 4 and 6),
which draw it the same way inside the held box and dissolve its lid, updating as the holder scrolls or places.

Classified by HOW it renders, not by item type, all by inherent render signals (no hard-coded item list), cached per
item in `PocketBuildContentRender`:
- FLAT (2D sprite) vs 3D block: the model's own `usesBlockLight` flag (`isFlat`) - true for a shaded 3D cube, false for
  a flat sprite. `instanceof BlockItem` does NOT capture this - a sapling is a BlockItem yet renders as a flat cross.
- Special-renderer block: any content drawn by a block-entity special renderer (`isSpecial`) - skull, conduit, copper
  golem statue, shulker, bed, chest, decorated pot - detected from the probe's layers.
- The one type test: a BlockItem that has a special renderer yet reports FLAT lighting (`isSpecial && isFlat &&
  instanceof BlockItem`). This is the decorated pot: its 3D inventory look comes from the oversized picture-in-picture
  path, which the in-box composition bypasses, so its layer is flat-lit and straight. It is pulled back onto the 3D
  path (below). A flat special item that is NOT a block (a shield) is genuinely flat and stays on the flat path.

(Placement legality stays a separate BlockItem test, `PocketBuildRules`; the two are different questions.)

Why centring reads the box's REAL geometry centre, not (0.5, 0.5, 0.5). As an item, the shulker box is drawn by its
special renderer in its ENTITY-model native space: `ShulkerBoxRenderer` applies its block-entity re-centring transform
(`translate(0.5)`, `scale(1,-1,-1)`, `translate(0,-1,0)`) only when given a direction, i.e. for a placed block-entity,
NOT for an item. So the box's visual centre is not (0.5, 0.5, 0.5), and content placed there drifts off the box (onto
the hand in first person). The box's true centre is instead read live from its own layers' extents (pushed through
their full transform) and the content is centred on THAT - which holds in every context because both are expressed in
the same shared (pre-outer-pose) space, so the shared outer pose preserves the alignment.

**The unified 3D-block path.** Every non-flat block - a plain block (deepslate, stairs, walls), a block with a CUSTOM
display (big/small dripleaf), and a special-renderer block (statue, chest, conduit, bed, mob head, shulker, decorated
pot) - takes ONE path: appended with the GUI (inventory) display, turned to follow the box, shrunk, and re-centred.

- Orientation - follow the box (`boxGuiRef` + a rotation delta). The content is appended with the GUI display, which is
  correct in the slot for every block. In held and dropped (on-the-ground) views the box turns to a non-GUI display, so
  the content must turn WITH it.
  Applied per layer: `delta = boxRot(context) * boxRot(gui)^-1` (the rotation the box itself undergoes from its GUI
  display to this context), pre-multiplied onto the layer's own GUI rotation (composed in quaternions, re-expressed as
  Euler XYZ to match `ItemTransform`). So the content keeps its correct inventory orientation and merely follows the
  box - exactly like a plain block does. A block's OWN per-context display is NOT used because it can be a "held in the
  hand" pose in third person (mob heads, statues, the dripleaf's custom display) that reads as held-by-the-player; a
  plain block (deepslate) lands on the orientation it already had natively (the delta reconstructs it), so it is
  unchanged in result, while a custom/special block is pulled onto the same box-following orientation. `boxRot(gui)` is
  the reference read from `boxGuiRef` (below). Scope: all non-flat content.
- The shrink is COMPOSED with each layer's own localTransform, not substituted for it: a plain block has an identity
  localTransform there, but a special block-entity renderer keeps its model-fitting transform there - the entity-model
  Y-flip and scaling, and a bed's separate HEAD/FOOT placement - so composing preserves it; substituting it dropped the
  fit and left a statue upside down or a bed reduced to a single piece.
- Size - a CONSTANT 0.6x of the content's OWN natural footprint (no box-relative cap). Uniform, so two blocks with the
  same body render at the same height, just as the vanilla slot uses one fixed GUI scale for every block: a conduit
  (model ~0.6 of a full block) stays small and a chest stays chest-sized, instead of being inflated to fill the box. An
  earlier version scaled to 0.6 OF THE BOX (`0.6 * box/content`) and then added a `min(1, box/content)` cap to undo the
  resulting inflation - but the cap over-shrank any block whose bbox merely exceeds the box on one thin axis (a
  calibrated_sculk_sensor's amethyst horn), breaking the same-height match; the constant 0.6x needs no cap (even the
  largest content still sits inside the box). Because the content is appended GUI-scaled (constant) while the box shrinks
  in held and dropped views, its scale is also multiplied by the box's OWN shrink factor (`boxScale-now / boxScale-gui`, from
  `boxGuiRef`) - a single scalar that keeps each item's proportions and makes the in-box size ratio the same in the slot
  and in hand. Scope: all non-flat content.
- Oversized-flat block (the decorated pot, the one type test above). Its GUI layer is flat and straight, so the
  standard block iso rotation (`boxGuiRef`'s GUI rotation) is substituted onto it before the delta, giving it the 3D
  inventory look; it then follows the box and shrinks like the others. Scope: a BlockItem with a special renderer that
  reports flat.
- Centre offset - horizontal always, height in held and dropped only. The offset (box centre - content centre, read from
  the layers' transformed extents) is added to the block's display-transform translation, shifting the content in the
  shared space onto the box centre. X and Z (horizontal) are centred in EVERY view, so the content stays inside the box
  and does not drift toward the hand. The HEIGHT (Y) is centred in HELD and DROPPED views: there the box is posed
  (tilted) and the full three-axis centre is what keeps the content contained (dropping Y there also skews the apparent
  X/Z under the pose rotation). In the upright GUI slot the height is NOT centred, so the block keeps its natural height -
  a slab rests on the box floor instead of being lifted to the vertical centre, and a calibrated_sculk_sensor keeps its
  own height. For a cube-filling block the horizontal offset is ~0, so it is untouched.

**The flat (2D) path** (tools, the shield, flat blocks like saplings). Drawn with its GUI model, its own display
transform dropped to `NO_TRANSFORM` (a flat sprite's GUI transform has no rotation, so dropping it keeps the
orientation but removes the positioning translation that would otherwise anchor it to the hand), then centred on the
box's real centre and scaled: 0.5 of the box, 0.4 in third person, with a small upward nudge in first person (a flat
sprite otherwise sits a touch low and the wider ones, e.g. a sword or trident, clip the box edges). `NO_TRANSFORM`
itself still applies a `translate(-0.5)` inside `ItemTransform.apply`, compensated in the centring maths.
- Concession - slot lighting. In the GUI the lighting entry (3D vs flat) is chosen ONCE per item render state, from the
  FIRST layer - the box, a 3D block (`GuiItemAtlas` reads `usesBlockLight`) - so a flat sprite composed into it inherits
  the box's 3D diffuse and looks a touch DIMMER than its normal inventory icon. Its own flat lighting would need a
  separate render state, but a separate GUI item has its own depth and could not be HALF inside the box (in front of
  one wall and behind another): depth and lighting are coupled per render state in the GUI atlas. The
  dim-but-correctly-occluded layer is the accepted trade-off; held/world lighting is already correct.

**Nested shulker content** (a shulker drawn inside the box - command-block only, as shulkers cannot be nested in
survival). The content shulker stays closed (static, opaque) while the box opens, via two mechanisms:
- A per-layer content flag (`PocketBuildContentLayer`, set on the appended layers; `LayerRenderStateContentMixin`
  publishes it to the render side channel for the duration of each layer's submit). The lid openness and dissolve
  mixins read it (`isRenderingContentLayer`) to keep a content shulker's lid at 0 and un-dissolved while the box
  animates. Deterministic, independent of draw order or colour.
- A SEPARATE-model renderer for the content shulker (`separateNestedShulkerModel`, using
  `ShulkerBoxSpecialRendererAccessor` / `ShulkerBoxRendererAccessor`). Vanilla resolves a shulker lid pose from ONE
  `ShulkerBoxModel` shared per colour, so forcing a same-colour content shulker closed would otherwise drag the box's
  own lid closed too (they share that one model). A fresh `ShulkerBoxSpecialRenderer` with its OWN model (same colour
  sprite) is built once per colour, cached, and swapped onto the content layer's renderer field. Scope: shulker content
  only; an instance is created only when shulkers are actually nested, at most one per colour, never otherwise.

**The box GUI reference (`boxGuiRef`).** The box's GUI display rotation and scale - the reference the orientation delta
and the size shrink-factor divide FROM. Probed deterministically from the box item's OWN model (vanilla, or whatever a
resource pack defines for that colour), on a fresh marker-less stack so the content mixin does nothing during the probe
(no re-entrancy), cached per item. A few floats per shulker colour; a RAM-only memoisation of a deterministic model
property, nothing on disk.

**On this section's complexity and its future.** This path deliberately classifies and re-orients content by reading
vanilla's OWN render signals (the block-light flag, the presence of a special renderer, the display transforms) rather
than a hard-coded list of items, so it follows modded and future items automatically through the same generic path. The
handful of cases handled by hand (the oversized-flat block routed back to 3D; the shared-model nested shulker) exist
only because the base item-render layer groups a few items in ways the in-box composition then has to unpick. As
Minecraft's item-render categories and their exceptions evolve, some of those hand-handled cases may be expressible
through the generic path and fold back into it. Any future change that removes a special case or a concession WITHOUT a
regression will be adopted.

### Lid dissolve (1.1.1)

As a Pocket-Build shulker opens, its lid DISSOLVES away instead of only lifting, so it stops hiding the content
composed inside the box.

- Why a dissolve and not a translucent fade: a true alpha fade cannot be ordered correctly here. The content is drawn
  in the item render pass and the lid in the model-translucent pass; the two are composited in an order that is fixed
  by the engine and differs by context (the content draws in front of a faded lid in some contexts, the faded lid
  masks the content in others). Translucent blending is order-dependent and that order is not controllable across the
  item/model pass boundary, so a fade was abandoned.
- The dissolve reuses the vanilla `entityCutoutDissolve` render type (the Ender Dragon death effect):
  `ShulkerBoxLidFadeMixin` (`@WrapOperation` on the lid model submit) draws the base opaque and the lid with this
  render type, tinted `ARGB.white(1 - openness)`. The shader discards a texel when `(1 - openness)` falls below that
  texel's threshold in a mask texture. Cutout WRITES depth and never blends, so it is ORDER-INDEPENDENT: the opaque
  content shows through the holes in every context, and nothing behind the shulker (water, clouds) is masked through
  them - the exact two failures a real fade could not avoid.
- The mask is generated at runtime (`DissolveMask`), not shipped as a file. The shader reads ONLY the mask's alpha
  (at the lid's UVs), so the mask is a 512x512 texture whose alpha is filled from a per-texel integer hash - an even,
  uncorrelated spread of thresholds (white noise) - with RGB left unused. The exact pattern never mattered, only that
  the thresholds are uniform, so a deterministic hash replaces a stored 512x512 RGBA PNG (three dead channels and
  ~400 KB) with no visible change. 512x512 keeps the effective texel density over the shulker atlas sub-rectangle, and
  the texture samples NEAREST, so the dissolve grain stays aligned with the box's own 16-pixels-per-face art. It is a
  `DynamicTexture` (not a `ReloadableTexture`), so a resource reload leaves it in place.
  - Concession - texel granularity. The dissolve grain is the shulker texture's resolution, not the screen's, because
    the mask is sampled at the model's UVs. A custom screen-space dither shader would give per-screen-pixel grain at
    any zoom, but that is a custom render pipeline: a shader pack (Iris) replaces the pipeline and would not run it,
    and it re-implements rendering rather than reusing a vanilla render type, so it trades away both compatibility and
    the Vanilla+ guarantee. Reusing the vanilla dissolve was preferred.
  - The mask lives in the mod's namespace and the discard is driven by the mask alpha (not the shulker texture's
    pixels), so a resource pack that retextures the shulker still dissolves identically.
- The dissolve runs for the whole Pocket-Build open, including an empty selection (so the lid behaves the same whether
  or not a block is selected): it is gated on `ClientShulkerSession.isPocketBuild` (a per-animation flag set when the
  mode opens the box), NOT on the content being present. It is scoped to the local player's live held shulker through
  the render side channel, so placed shulker blocks and other shulkers are never touched. A shulker drawn INSIDE the
  box as content is also excluded (the content-layer flag, see Section 8): it keeps an opaque, un-dissolved lid.

### Selected content count on the hotbar slot (1.1.2)

In Pocket-Build the held shulker's hotbar slot shows the COUNT of the SELECTED CONTENT (how many of the block you are
about to place you have), like a normal hotbar item, instead of the shulker's own count. `GuiSelectedContentDecorationsMixin`
redirects the `GuiGraphicsExtractor.itemDecorations` call in `Gui.extractSlot` (symmetrically to how
`GuiSelectedItemNameMixin` redirects the name popup). The held Pocket-Build shulker is identified by its `animation_id`
marker matching the active session, so another shulker in the hotbar is unaffected. The slot ICON is drawn earlier and
separately, so it is left untouched (it stays the shulker with the content rendered inside).

Only the count is drawn, NOT the full decorations: Pocket-Build places BLOCKS only (the full-use gamerule does not exist
yet), so the durability bar and cooldown overlay have no meaning here. The count is drawn right-aligned at the vanilla
position and scaled to 0.8 about its RIGHT EDGE (via the `GuiGraphicsExtractor.pose()` `Matrix3x2fStack`), so it reads a
touch smaller while staying right-aligned: scaling about the centre would push a 1-digit count further right than a
2-digit one. Shown only when the count is > 1, like vanilla.

### Pick-block selects from the box first (1.1.2)

In Pocket-Build, middle-click (pick-block) tries the held shulker BEFORE the inventory. `MultiPlayerGameModePickMixin`
computes the picked item exactly like vanilla (`getCloneItemStack` for a block, `getPickResult` for an entity), then:
- Priority 1: if a content slot matches it, SELECT that slot and cancel the vanilla pick, staying in the mode. The match
  is `ItemStack.isSameItemSameComponents` on the FIRST matching slot - the exact criterion and order vanilla's
  `Inventory.findSlotMatchingItem` uses for the inventory pick-block (so a renamed block / a specific banner / a player
  head only matches its exact self, and Ctrl + pick, which puts data into the picked item, matches accordingly). The
  selection is synced to the server with `PocketBuildSelectPayload`, like a scroll; the in-box render follows next tick.
- Priority 2 (unchanged): not in the box but IN the inventory (or creative) -> leave the mode and let the vanilla pick
  take it (server-authoritative).
- Priority 3 (unchanged): nowhere at all -> STAY in the mode and do nothing. The vanilla pick is a no-op, so there is no
  slot change to desync. Leaving the mode stays gated on the pick actually happening (`willPick`), exactly as before.

The box selection is purely client-side state (no item moves), so there is no duplication concern.

## 9. Compatibility notes for other mod authors

This mod is built to be a good vanilla citizen, but it relies on a few vanilla contracts. If
another mod breaks one of these, interop may suffer. Below: what could clash, and how to stay
compatible from the other mod's side.

- Custom shulker-like items are supported through the tag. We detect shulkers by the vanilla
  `minecraft:shulker_boxes` ITEM tag (`stack.typeHolder().is(ItemTags.SHULKER_BOXES)`), not a
  hardcoded class, so a custom shulker item that is in that tag is recognized automatically.
  To be compatible: add custom shulker variants to the vanilla `minecraft:shulker_boxes` item tag.

- Conflicting interception of inventory clicks. Our click mixins inject at `slotClicked` HEAD
  and cancel only for a tightly gated case (plain right-click, empty cursor, a shulker in the
  player's own non-equipment slot). A mod that injects at the same point and cancels broadly or
  unconditionally can swallow the click before us (or vice versa). To be compatible: gate your
  cancellation narrowly to your own case; do not blanket-cancel `slotClicked`.

- Conflicting shulker rendering. We override the shulker `openness` with `@ModifyArg` and fall
  back to the original value for shulkers that are not ours. A mod that `@Overwrite`s the shulker
  renderer, or modifies the same `openness` argument without preserving the incoming value, can
  clobber our animation (or we can perturb theirs depending on mixin order). To be compatible:
  prefer additive `@ModifyArg`/`@Inject` that respect the incoming value over `@Overwrite`.

- Making shulkers stackable. Shulker boxes are non-stackable in vanilla (size 1, always), which our
  anti-dup relies on (no half-stack-split duplication). A mod that makes shulkers stackable would
  weaken that. To be compatible: keep shulker boxes non-stackable.

- Aggressively stripping or rejecting unknown components. A mod (or server anti-cheat) that strips
  namespaced components it does not recognize can remove `animation_id` mid-animation (cosmetic
  glitch), and one that rejects items carrying unknown components could flag a shulker that is
  briefly animating. To be compatible: leave unknown namespaced components alone, or allowlist
  mod-namespaced components.

- Non-standard container-close handling. We assume the vanilla one-open-container-at-a-time model
  and standard close packets. A mod that drives multiple menus or sends non-standard close packets
  may interact with our stale-close guard. To be compatible: follow the vanilla container lifecycle.
