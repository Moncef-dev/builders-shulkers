# Shulker Inventory - Technical Documentation (v1.0.7)

Contributor-facing notes on the technical problems this mod solves, the chosen solutions,
their scope, and known risks. Describes the state as shipped in v1.0.7.

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
`shulker-inventory:animation_id` data component (a `long`). It is allocated client-side at open as a
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
opened a shulker sees its lid move. To make the lid visible on a shulker HELD by another player, the server
broadcasts open/close events (`RemoteShulkerAnimationPayload`) to the players who can see the holder, and those
clients run the same animation locally (`startOpeningRemote` / `startClosing`). The broadcast is gated on
`ServerPlayNetworking.canSend` per viewer, so it is never sent to a vanilla client (no mod) or to a viewer that
is mid-join (which would otherwise error the send). The initiator animates locally and is excluded; in
singleplayer there are no other viewers, so the broadcast is a no-op and solo behavior is unchanged. A close
that ends because the source shulker was disturbed (grabbed off its slot) is broadcast SILENTLY: the box has
vanished for viewers, so they drain the animation state without a phantom close sound, while a normal close
(toggle or Escape) keeps the sound.

## 5. Mixins and injection points

Server (`shulker-inventory.mixins.json`):
- `ServerContainerCloseMixin` -> `ServerGamePacketListenerImpl.handleContainerClose` (HEAD,
  cancellable): drops stale close packets during a menu swap, scoped to our own menu (never affects
  other mods' containers).

Client (`shulker-inventory.client.mixins.json`):
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
- `RemoteShulkerAnimationPayload` (S2C, broadcast): mirror a lid animation (open or close) to the players who
  can see the holder, so the lid is visible on the shulker held by another player. Gated by `canSend` per viewer.
  Carries the holder entity id (a mirrored sound plays at the holder's position) and a `playSound` flag (a close
  caused by disturbing the source shulker is sent silent, so viewers drain the animation without a phantom sound).

## 7. Known limitations and risks (v1.0.7)

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

## 8. Compatibility notes for other mod authors

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
