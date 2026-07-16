package io.github.moncefdev.shulkerinventory;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildModePayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import io.github.moncefdev.shulkerinventory.platform.Platform;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Shared server-side logic. Each loader's entrypoint wires these handlers into its own networking and event plumbing
// (payload registration, receivers, connection events, game-rule registration); everything here is loader-agnostic.
// Every handler expects to be called on the SERVER thread.
public final class ShulkerInventory {
	private ShulkerInventory() {}

	public static final String MOD_ID = "builders-shulkers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// Open handler: validate the slot, toggle closed if this shulker is already open, otherwise copy the
	// stack's CONTAINER component into a working container and open a vanilla shulker menu bound to it.
	public static void handleOpenShulker(OpenShulkerPayload payload, ServerPlayer player) {
		// Server-authoritative feature gate: if an admin disabled inventory access, ignore the open request.
		if (!ModGameRules.inventoryAccess(player.level().getServer())) {
			return;
		}
		int slotIndex = payload.slotIndex();
		long animationId = payload.animationId();
		Inventory inventory = player.getInventory();

		if (slotIndex < 0 || slotIndex >= inventory.getContainerSize()) {
			LOGGER.warn("Player {} sent invalid slot index {}", player.getName().getString(), slotIndex);
			return;
		}

		if (player.containerMenu instanceof InventoryShulkerBoxMenu currentMenu
				&& currentMenu.getSourceSlotIndex() == slotIndex) {
			currentMenu.closeAndReturnToInventory(player);
			return;
		}

		ItemStack stack = inventory.getItem(slotIndex);
		if (!ShulkerContents.isShulker(stack)) {
			LOGGER.warn("Player {} tried to open non-shulker stack at slot {}: {}",
					player.getName().getString(), slotIndex, stack);
			return;
		}

		NonNullList<ItemStack> items = ShulkerContents.read(stack);
		SimpleContainer shulkerContent = new SimpleContainer(ShulkerContents.SIZE);
		for (int i = 0; i < ShulkerContents.SIZE; i++) {
			shulkerContent.setItem(i, items.get(i));
		}

		MenuProvider provider = new SimpleMenuProvider(
				(syncId, playerInv, p) -> new InventoryShulkerBoxMenu(syncId, playerInv, shulkerContent, slotIndex, animationId),
				stack.getHoverName());

		// Only one container menu exists server-side, so close any other open container first (swap path).
		if (player.containerMenu != player.inventoryMenu) {
			player.doCloseContainer();
		}
		// Tag the stack (in vanilla custom_data) so the client knows to play the lid animation for this open.
		ShulkerAnimationMarker.set(stack, animationId);
		player.openMenu(provider);
		// Mirror the opening lid animation to the OTHER players who can see this player. Broadcast it ALWAYS, so a
		// viewer holds the animation even when the box is not visible yet and only becomes so mid-animation (the
		// opener moves it to the hotbar, or drops it). The SOUND is gated on held: it accompanies the open's start,
		// so a box that becomes visible later has already missed it. The opener's own animation and sound are local.
		boolean held = InventoryShulkerBoxMenu.isHeldSlot(inventory, slotIndex);
		InventoryShulkerBoxMenu.broadcastAnimation(player, animationId, true, held);
	}

	// Pocket-Build mode: the player entered or left the mode holding the shulker at hotbarSlot. Track the
	// server-side mode state (the selected content slot) so a vanilla use-on packet can swap in the right content,
	// and drive the lid animation exactly like an inventory open but WITHOUT opening a menu: on enter, tag the held
	// shulker with the animation id (so every render context animates it) and broadcast the open to viewers who can
	// see the holder; on leave, broadcast the close (the marker persists and is cleaned at login, like the GUI).
	public static void handlePocketBuildMode(PocketBuildModePayload payload, ServerPlayer player) {
		int hotbarSlot = payload.hotbarSlot();
		Inventory inventory = player.getInventory();
		if (hotbarSlot < 0 || hotbarSlot >= Inventory.getSelectionSize()) {
			return;
		}
		ItemStack stack = inventory.getItem(hotbarSlot);
		if (payload.entering()) {
			// Server-authoritative feature gate: if Pocket-Build is disabled, do not enter the mode. The client is
			// gated too (it will not even send this), but enforce it regardless. Leaving is never gated.
			if (!ModGameRules.pocketBuild(player.level().getServer())) {
				return;
			}
			// Entering REQUIRES a shulker in the slot; leaving must NOT. Dropping the held shulker is itself an exit,
			// so by the time this exit packet is processed the slot is already empty - guarding the exit on isShulker
			// (a shared check used to) skipped the state clear, leaving the server stuck in Pocket-Build. A recovered
			// shulker then kept swapping its content on right-click while the client, already out of the mode, predicted
			// placing the shulker itself (ghost blocks; no dup, since the server stays authoritative).
			if (!ShulkerContents.isShulker(stack)) {
				return;
			}
			PocketBuildServerState.enter(player.getUUID(), payload.contentSlot(), payload.animationId());
			ShulkerAnimationMarker.set(stack, payload.animationId());
			// Open broadcast FIRST (it creates the mirror animation on viewers), then the content broadcast, so
			// markPocketBuild / setPocketBuildContent land on an animation that already exists for them.
			InventoryShulkerBoxMenu.broadcastAnimation(player, payload.animationId(), true, true);
			InventoryShulkerBoxMenu.broadcastPocketBuildContent(player, payload.animationId(),
					selectedContent(stack, payload.contentSlot()));
		} else {
			PocketBuildServerState.exit(player.getUUID());
			InventoryShulkerBoxMenu.broadcastAnimation(player, payload.animationId(), false, true);
		}
	}

	// Pocket-Build selection: keep the server's selected content slot in sync as the player scrolls, so a normal
	// vanilla use-on packet swaps in the right content (handled by ServerPlayerGameModeMixin).
	public static void handlePocketBuildSelect(PocketBuildSelectPayload payload, ServerPlayer player) {
		PocketBuildServerState.select(player.getUUID(), payload.contentSlot());
		// Mirror the new selection to viewers, so the block drawn inside the held box updates as the player
		// scrolls. The held slot is locked in the mode, so the main-hand stack is the Pocket-Build shulker and
		// carries the animation id marker.
		ItemStack held = player.getMainHandItem();
		if (ShulkerContents.isShulker(held)) {
			Long animationId = ShulkerAnimationMarker.get(held);
			if (animationId != null) {
				InventoryShulkerBoxMenu.broadcastPocketBuildContent(player, animationId,
						selectedContent(held, payload.contentSlot()));
			}
		}
	}

	// Login cleanup. We deliberately never strip the animation_id marker DURING a session: an async strip that
	// mutates an inventory or cursor stack mid-session emits a slot/cursor update that can collide with a
	// concurrent click prediction on the client, briefly rendering a duplicated shulker. A leftover marker is
	// harmless in the meantime (an id with no live animation renders as a closed lid, and it stays identical on
	// client and server so click hashing still matches), so we clear it at the next safe point: login. There is
	// never a live animation at join time, and the whole inventory re-syncs on join, so stripping here is safe.
	// Login also covers every way a previous session could end (quit, kick, crash) and cleans markers left by
	// earlier sessions.
	public static void onPlayerJoin(ServerPlayer player, MinecraftServer server) {
		Inventory inventory = player.getInventory();
		int cleaned = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (ShulkerAnimationMarker.get(stack) != null) {
				ShulkerAnimationMarker.remove(stack);
				cleaned++;
			}
		}
		if (cleaned > 0) {
			LOGGER.info("Removed {} stale animation marker(s) from {}'s inventory on join",
					cleaned, player.getName().getString());
		}
		// Sync the current game-rule state to the joining player (custom rules are not synced automatically).
		Platform.network().send(player, gameRuleState(server));
	}

	// Drop any Pocket-Build state when a player leaves, so a stale selected slot never lingers server-side.
	public static void onPlayerDisconnect(ServerPlayer player) {
		PocketBuildServerState.exit(player.getUUID());
	}

	// Inventory-access rule changed: re-broadcast the state, and when it was turned OFF, force every connected client
	// out of the feature by closing any open shulker menu server-authoritatively (this saves the contents and reopens
	// the player's own inventory on the client).
	public static void inventoryAccessRuleChanged(boolean value, MinecraftServer server) {
		broadcastGameRuleState(server);
		if (!value) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.containerMenu instanceof InventoryShulkerBoxMenu menu) {
					menu.closeAndReturnToInventory(player);
				}
			}
		}
	}

	// Pocket-Build rule changed: re-broadcast the state, and when it was turned OFF, clear the server-side mode state
	// so no in-flight content swap goes through; the client leaves the mode itself when it receives the new state
	// (see ShulkerInventoryClient, the held shulker has no menu to drive the close).
	public static void pocketBuildRuleChanged(boolean value, MinecraftServer server) {
		broadcastGameRuleState(server);
		if (!value) {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PocketBuildServerState.exit(player.getUUID());
			}
		}
	}

	// The Pocket-Build selected content (the shulker's content at the selected slot), or EMPTY if the slot is out of
	// range (e.g. -1 when nothing is selected). EMPTY is a valid broadcast: the dissolve still applies for viewers,
	// just with nothing drawn inside the box.
	private static ItemStack selectedContent(ItemStack shulker, int contentSlot) {
		if (contentSlot < 0 || contentSlot >= ShulkerContents.SIZE) {
			return ItemStack.EMPTY;
		}
		return ShulkerContents.read(shulker).get(contentSlot);
	}

	private static GameRuleStatePayload gameRuleState(MinecraftServer server) {
		return new GameRuleStatePayload(ModGameRules.inventoryAccess(server), ModGameRules.pocketBuild(server));
	}

	private static void broadcastGameRuleState(MinecraftServer server) {
		GameRuleStatePayload payload = gameRuleState(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Platform.network().send(player, payload);
		}
	}
}
