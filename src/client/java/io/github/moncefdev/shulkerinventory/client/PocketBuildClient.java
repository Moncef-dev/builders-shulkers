package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.network.PocketBuildModePayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// Pocket-Build: place a shulker's selected content from the hotbar, without ever placing the shulker.
//
// The Pocket-Build modifier + right-click with a shulker selected in the hotbar and every menu closed enters the mode
// INSTEAD of placing the shulker (works on a block, on air, or on an entity, so all three use events route through
// here); the unbound "Toggle Pocket-Build" keybind enters/exits in one press (for controller / mobile). While in the
// mode, a PLAIN right-click runs the vanilla use/interact flow on the SELECTED CONTENT (the content-swap mixins put it
// briefly in hand), server-authoritative so nothing can be duplicated. The mouse wheel cycles the selected slot. The
// modifier + right-click again exits; so does opening a container, dropping the shulker, moving the hotbar selection,
// dying, or a pick-block (middle-click).
public final class PocketBuildClient {
	private PocketBuildClient() {}

	// True while a fresh right-click press is still available to act on: the tick handler sets it when right-click is
	// released, and handleUse consumes it on the next use. The modifier + right-click enter/exit fires only on this
	// press edge, so pressing the modifier while right-click is already held (to sprint / sneak mid-build) never flips
	// the mode, and holding it down cannot flip-flop.
	private static boolean rightClickReleased = true;
	// The peek contents overlay only appears after the Peek key has been held this many ticks, so a quick
	// modifier + right-click toggle (which holds the key briefly) never flashes the overlay before the mode opens/closes.
	private static final int PEEK_DELAY_TICKS = 5;
	private static int peekHeldTicks = 0;

	// Held-box identity tracking. The mode box carries the server-set animation marker; an in-place item swap (Item
	// Swapper's R menu, a creative set-slot, any mod that replaces the held stack without moving the selected slot)
	// puts a different shulker in the same slot, so its marker no longer matches and we exit. The marker syncs back a
	// tick or two after entry, so a mismatch is enforced only once it has matched at least once (heldMarkerConfirmed);
	// a bounded grace exits anyway if it never confirms, so the mode can never linger on a box that is not ours.
	private static boolean heldMarkerConfirmed = false;
	private static int ticksInMode = 0;
	private static final int MARKER_SYNC_GRACE_TICKS = 40;

	// Whether the peek contents overlay should be shown right now: in Pocket-Build mode with the Peek key held past the
	// debounce window. Read by PocketBuildOverlay.
	public static boolean peekVisible() {
		return PocketBuildMode.isActive() && peekHeldTicks >= PEEK_DELAY_TICKS;
	}

	// Block the off-hand swap key (F) while the mode is active, before vanilla's keybind handling reads it.
	// Wired by the loader entrypoint to the START of every client tick.
	public static void startClientTick(Minecraft mc) {
		if (!PocketBuildMode.isActive()) {
			return;
		}
		while (mc.options.keySwapOffhand.consumeClick()) {
			// discard
		}
	}

	// Re-arm the toggle, then apply the exit conditions while the mode is active.
	// Wired by the loader entrypoint to the END of every client tick.
	public static void endClientTick(Minecraft mc) {
		if (!mc.options.keyUse.isDown()) {
			rightClickReleased = true;
		}
		if (!PocketBuildMode.isActive()) {
			return;
		}
		if (mc.player == null) {
			exitMode();
			return;
		}
		// Death ends the mode. Without keepInventory the shulker drops and the !stillShulker check below catches it;
		// WITH keepInventory it stays in hand and neither the death nor the respawn screen is a container, so nothing
		// else would close the mode.
		if (mc.player.isDeadOrDying()) {
			exitMode();
			return;
		}
		ItemStack held = mc.player.getMainHandItem();
		boolean stillShulker = ShulkerContents.isShulker(held);
		// Only opening a container UI (the player's own inventory, a chest, any container) ends the mode, like
		// the chest GUI flow. The pause menu, options, chat, etc. leave the mode running.
		if (mc.screen instanceof AbstractContainerScreen || !stillShulker
				|| mc.player.getInventory().getSelectedSlot() != PocketBuildMode.sourceHotbarSlot()) {
			exitMode();
			return;
		}
		// Held-box identity: an in-place item swap (Item Swapper's R menu, a creative set-slot, any mod that
		// replaces the held stack WITHOUT moving the selected slot) puts a different shulker in the same slot, which
		// the checks above miss. The mode box carries the server-set marker; a swapped-in box does not match, so we
		// exit - the swap then cleanly closes box 1 and leaves box 2 a normal held shulker. Enforce a mismatch only
		// once the marker has matched at least once (it syncs back a tick or two after entry); a bounded grace exits
		// anyway if it never confirms (e.g. a swap during that initial window), so the mode can never linger.
		ticksInMode++;
		Long heldMarker = ShulkerAnimationMarker.get(held);
		if (heldMarker != null && heldMarker == PocketBuildMode.animationId()) {
			heldMarkerConfirmed = true;
		} else if (heldMarkerConfirmed || ticksInMode > MARKER_SYNC_GRACE_TICKS) {
			exitMode();
			return;
		}
		// Keep the content drawn inside the box in sync with the scroll selection; it stays frozen on the
		// animation through the close (where the mode is already inactive).
		ClientShulkerSession.setPocketBuildContent(PocketBuildMode.animationId(), PocketBuildMode.selectedStack(held));
		// Debounce the peek: count how long the Peek key has been held continuously. A quick modifier + right-click
		// toggle holds it only briefly, so it never reaches the threshold and the contents overlay never flashes
		// before the mode closes; a deliberate peek (sustained hold) does show it.
		if (BuildersShulkersKeybinds.isPeekDown()) {
			peekHeldTicks++;
		} else {
			peekHeldTicks = 0;
		}
	}

	// The shared Pocket-Build toggle for every right-click (block, air, or entity). Ctrl + right-click enters the mode
	// when a shulker is selected in the hotbar and every menu is closed (cancelling the vanilla place/interact), and
	// exits it while active; a PLAIN right-click in the mode is PASSed so the vanilla use/interact flow runs - the
	// content-swap mixins briefly put the SELECTED CONTENT in hand, so the whole vanilla flow (place / interact / sound /
	// adventure / orientation, an entity action like placing into an item frame too) applies to the content, never the
	// shulker. Entry needs a server that runs the mod, so the placement is validated server-authoritatively, never duped.
	public static InteractionResult handleUse(Player player, Level world, InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND || player != mc.player) {
			return InteractionResult.PASS;
		}
		boolean modifier = BuildersShulkersKeybinds.isPocketBuildModifierDown();
		// The press EDGE: true only on the first use since right-click was released. Consumed every call, so the modifier
		// must be held AT the right-click press to act - pressing it while right-click is already held (e.g. to sprint /
		// sneak mid-build) no longer enters/exits the mode.
		boolean pressEdge = rightClickReleased;
		rightClickReleased = false;

		if (PocketBuildMode.isActive()) {
			// Exit (and cancel the use) only on a fresh modifier + right-click press; otherwise PASS, so a plain
			// right-click - or holding right-click while the modifier happens to be down - keeps placing the content.
			if (modifier && pressEdge) {
				exitMode();
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}

		// Enter (and cancel the vanilla shulker place) only on a fresh modifier + right-click press with a shulker in
		// hand, every menu closed, and a server that runs the mod (so it can validate the placement
		// server-authoritatively). Otherwise leave vanilla alone, so a plain right-click places the shulker and holding
		// right-click then pressing the modifier does not enter mid-build.
		ItemStack held = player.getMainHandItem();
		if (modifier && pressEdge && ShulkerContents.isShulker(held)
				&& mc.screen == null && ClientPlatform.network().canSend(PocketBuildModePayload.TYPE)
				&& ClientGameRuleState.pocketBuild()) {
			enterMode(player, held);
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	private static void enterMode(Player player, ItemStack held) {
		// Restart the peek debounce from zero, so opening the mode (a modifier + right-click that holds the key briefly)
		// does not immediately flash the contents overlay, just like the exit case.
		peekHeldTicks = 0;
		// Fresh session: re-arm the held-box identity tracking (see the tick handler).
		heldMarkerConfirmed = false;
		ticksInMode = 0;
		int slot = player.getInventory().getSelectedSlot();
		// Begin the lid opening, resuming from the shulker's current openness (shared with the inventory open mode via
		// ClientShulkerSession.beginHeldOpening), so a quick re-enter continues from where the closing lid is instead
		// of snapping shut. No screen here, so armScreen=false. The server tags the held stack with this id (synced
		// back) so the render resolves the marker to this animation across every context.
		long id = ClientShulkerSession.beginHeldOpening(held, false);
		// Tag the animation as Pocket-Build so the lid dissolves for the whole open, even when the selected slot is empty.
		ClientShulkerSession.markPocketBuild(id);
		PocketBuildMode.enter(slot, held, id);
		// Hand the selected content to the animation so the render draws it inside the box from the start of the open.
		ClientShulkerSession.setPocketBuildContent(id, PocketBuildMode.selectedStack(held));
		ClientPlatform.network().send(new PocketBuildModePayload(true, slot, id, PocketBuildMode.selectedContentSlot()));
		ClientShulkerSession.playOwnSound(true);
	}

	public static void exitMode() {
		long id = PocketBuildMode.animationId();
		int slot = PocketBuildMode.sourceHotbarSlot();
		PocketBuildMode.exit();
		heldMarkerConfirmed = false;
		ticksInMode = 0;
		if (ClientPlatform.network().canSend(PocketBuildModePayload.TYPE)) {
			ClientPlatform.network().send(new PocketBuildModePayload(false, slot, id, -1));
		}
		ClientShulkerSession.startClosing(id);
		ClientShulkerSession.playOwnSound(false);
	}

	// Single-key Pocket-Build entry/exit for the "Toggle Pocket-Build" keybind: exit when active, otherwise enter when a
	// shulker is selected in the hotbar and every menu is closed on a server that runs the mod (the same conditions as
	// the modifier + right-click path). A one-press path for controller / mobile; the modifier + right-click is unchanged.
	public static void requestToggle() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		if (PocketBuildMode.isActive()) {
			exitMode();
			return;
		}
		ItemStack held = mc.player.getMainHandItem();
		if (ShulkerContents.isShulker(held) && mc.screen == null
				&& ClientPlatform.network().canSend(PocketBuildModePayload.TYPE) && ClientGameRuleState.pocketBuild()) {
			enterMode(mc.player, held);
		}
	}

	// Select the content matching `pick` inside the held shulker and sync the new selection to the server (like a
	// scroll). Returns true if `pick` was found in the box (selection made), false otherwise. Shared by the vanilla
	// pick handling (MultiPlayerGameModePickMixin) and the Litematica pick interop (LitematicaPickBlockCompat).
	public static boolean selectContentMatching(ItemStack heldShulker, ItemStack pick) {
		if (!PocketBuildMode.selectMatchingSlot(heldShulker, pick)) {
			return false;
		}
		if (ClientPlatform.network().canSend(PocketBuildSelectPayload.TYPE)) {
			ClientPlatform.network().send(new PocketBuildSelectPayload(PocketBuildMode.selectedContentSlot()));
		}
		return true;
	}

	// Full Pocket-Build pick-block decision, shared by the vanilla pick (MultiPlayerGameModePickMixin) and the
	// Litematica schematic pick (LitematicaPickBlockCompat). Priority 1: `pick` is already in the held shulker -> select
	// that content (synced like a scroll) and return true, so the caller cancels the default pick and stays in the mode.
	// Priority 2: otherwise, if the pick would actually take an item (creative, or the item is already in the inventory),
	// EXIT Pocket-Build so the default pick runs and the selected slot follows normally (the locked hotbar would
	// otherwise desync). Priority 3: a no-op pick leaves the mode untouched. Returns true ONLY for the select-from-box case.
	public static boolean handlePocketBuildPick(LocalPlayer player, ItemStack pick) {
		if (selectContentMatching(player.getMainHandItem(), pick)) {
			return true;
		}
		if (willPickItem(player, pick)) {
			exitMode();
		}
		return false;
	}

	// The server's tryPickItem condition: creative (infinite materials) always picks; otherwise the pick happens only
	// when the player already holds a matching item somewhere in the inventory. Anything else is a no-op.
	// RE-VERIFY against vanilla tryPickItem at each Minecraft update: this is a hand-copied condition, the one vanilla
	// rule the mod re-implements rather than reuses.
	private static boolean willPickItem(LocalPlayer player, ItemStack pick) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return pick != null && !pick.isEmpty() && player.getInventory().findSlotMatchingItem(pick) != -1;
	}
}
