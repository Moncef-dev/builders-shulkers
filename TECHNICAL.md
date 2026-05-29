# Shulker Inventory - Technical Documentation (v1.0.3)

Contributor-facing notes on the technical problems this mod solves, the chosen solutions,
their scope, and known risks. Describes the state as shipped in v1.0.3.

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

## 3. Per-shulker identity: the `animation_id` component

The lid animation must follow the SPECIFIC shulker even if it moves between slots, so it is
keyed by a per-shulker identity, never by the slot.

Minecraft items are value objects: an `ItemStack` has no per-instance unique id; equality is
by (item, components). To track one specific shulker, the mod adds a synthetic identity: the
`shulker-inventory:animation_id` data component (a `long`). It is allocated client-side at
open, stamped on the source stack server-side, and network-synchronized so the client renderer
can recognize the animating stack wherever it is drawn (GUI, hand, dropped entity).

- Why a data component, and why PERSISTENT (encodable): a stack that lives in a container is
  hashed during container-click validation (`HashedStack`). A non-encodable (transient)
  component throws `not encodable` and crashes the click handler. So the identity component
  must be encodable, i.e. persistent.
- `.ignoreSwapAnimation()` on the component suppresses the first-person hand swap animation when an
  already-present `animation_id` only changes VALUE. It does NOT cover the component being added or
  removed; that case is handled by `IgnoreAnimationIdSwapMixin` (see section 5 for why both are
  needed).
- The animation state machine (progress, OPENING/OPENED/CLOSING) is purely client-side in
  `ClientShulkerSession`. The component is removed via `AnimationFinishedPayload` once the
  closing animation completes.

## 4. Rendering (reuse of vanilla openness)

The lid animation reuses the vanilla `ShulkerBoxSpecialRenderer` `openness` parameter, overridden
by `ShulkerBoxOpennessMixin` (`@ModifyArg`) from the interpolated animation progress. When the
rendered shulker is not one of ours, the mixin returns the original value unchanged, so other
shulkers (and other mods' rendering) are untouched.

The animation id is routed to the renderer through a render-thread-confined side channel in
`ClientShulkerSession` (set by `GuiItemAtlasMixin` / `ItemEntityRendererMixin` around the draw,
read by `ShulkerBoxOpennessMixin`). Covers the GUI item icon, the held first-person item, and
dropped item entities.

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
- `IgnoreAnimationIdSwapMixin` -> `ItemInHandRenderer.shouldInstantlyReplaceVisibleItem` (HEAD,
  cancellable): suppress the hand swap animation when two held stacks differ only by `animation_id`
  being added or removed. This is NOT redundant with the vanilla `.ignoreSwapAnimation()` flag.
  Vanilla decides via `ItemStack.matchesIgnoringComponents`, which first short-circuits to false on
  `components.size()` inequality and only consults the ignore predicate afterwards (at equal size).
  So the flag covers a value change of an already-present component, but a component that is present
  on one stack and absent on the other (open adds it, the finished close removes it) changes the map
  size and still triggers the swap. This mixin covers exactly that add/remove transition.

## 6. Networking payloads

- `OpenShulkerPayload` (C2S): slot index + animation id (request to open, or toggle closed).
- `OpenPlayerInventoryPayload` (S2C): reopen the player's inventory screen after a session ends.
- `AnimationFinishedPayload` (C2S): the closing animation finished, drop the marker.

## 7. Known limitations and risks (v1.0.3)

- Component-equality divergence (observed, not just theoretical). While `animation_id` is present,
  the shulker is not equal by components to an otherwise identical stack without it. This is a real
  consequence: vanilla's own `ItemStack.matchesIgnoringComponents` (used by the first-person swap
  logic) treats the tagged and untagged shulker as different, which is exactly why
  `IgnoreAnimationIdSwapMixin` has to exist (see section 5). The blast radius stays small because the
  paths most sensitive to component equality (stacking, item merging, ground-entity merging) are
  gated by stackability, and shulker boxes are non-stackable in vanilla, so they never take those
  paths. The component is also present only briefly (the open/close animation window). The residual
  effect is cosmetic and short-lived.
- Disk leak (mitigated). If the client disconnects or crashes mid-animation, `AnimationFinishedPayload`
  may never arrive and a harmless junk `animation_id` can remain on that shulker (no visual effect:
  `isAnimating` is false, so the renderer falls back to vanilla openness). A login cleanup
  (`ServerPlayConnectionEvents.JOIN`) strips any leftover marker from the joining player's inventory;
  there is never a live animation at join time, so this self-heals a leak on the next login and also
  cleans markers left by earlier sessions retroactively. Residual: a marker on a shulker that was
  moved out of the player's inventory (for example into a chest) before the cleanup runs is not
  reached and stays as harmless junk. A bounded server-side delayed cleanup at session close (clearing
  the marker after a short grace if the primary payload never removed it) would also heal the
  mid-session and moved-out cases sooner, but it is deliberately not implemented: it would require an
  always-on per-tick scheduler and extra coupling, which is not worth it for a leak that is already
  cosmetically harmless and, for any marker still in the player's inventory, self-heals at the next
  login (the moved-out case above being the only residual).
- The render side channel is render-thread-confined; correct but order-sensitive.
- Close cue uses the shulker MOB sound (our choice). The open uses the shulker box open sound, but
  the close deliberately uses the shulker MOB close sound (`SHULKER_CLOSE`) as a short, clear cue for
  frequent inventory switching. Consequence: a resource pack or mod that retunes shulker MOB sounds
  also changes this close cue (you close a box but hear the retuned mob sound). This is a known
  consequence of our chosen sound, on our side, not an external mod's fault.

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
