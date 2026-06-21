package io.github.moncefdev.shulkerinventory;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildModePayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

public class ShulkerInventory implements ModInitializer {
	public static final String MOD_ID = "builders-shulkers";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(OpenShulkerPayload.TYPE, OpenShulkerPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketBuildModePayload.TYPE, PocketBuildModePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketBuildSelectPayload.TYPE, PocketBuildSelectPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenPlayerInventoryPayload.TYPE, OpenPlayerInventoryPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RemoteShulkerAnimationPayload.TYPE, RemoteShulkerAnimationPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PocketBuildRemoteContentPayload.TYPE, PocketBuildRemoteContentPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GameRuleStatePayload.TYPE, GameRuleStatePayload.STREAM_CODEC);

		// Feature toggles exposed as vanilla game rules (default true). Their values are synced to clients (on join and
		// on change) so the client can gate its own interception, since custom game rules are not synced automatically.
		ModGameRules.register(ShulkerInventory::broadcastGameRuleState);

		// Open handler: validate the slot, toggle closed if this shulker is already open, otherwise copy the
		// stack's CONTAINER component into a working container and open a vanilla shulker menu bound to it.
		ServerPlayNetworking.registerGlobalReceiver(OpenShulkerPayload.TYPE, (payload, context) -> {
			var player = context.player();
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
		});

		// Pocket-Build mode: the player entered or left the mode holding the shulker at hotbarSlot. Track the
		// server-side mode state (the selected content slot) so a vanilla use-on packet can swap in the right content,
		// and drive the lid animation exactly like an inventory open but WITHOUT opening a menu: on enter, tag the held
		// shulker with the animation id (so every render context animates it) and broadcast the open to viewers who can
		// see the holder; on leave, broadcast the close (the marker persists and is cleaned at login, like the GUI).
		ServerPlayNetworking.registerGlobalReceiver(PocketBuildModePayload.TYPE, (payload, context) -> {
			var player = context.player();
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
				PocketBuildServerState.enter(player.getUUID(), payload.contentSlot());
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
		});

		// Pocket-Build selection: keep the server's selected content slot in sync as the player scrolls, so a normal
		// vanilla use-on packet swaps in the right content (handled by ServerPlayerGameModeMixin).
		ServerPlayNetworking.registerGlobalReceiver(PocketBuildSelectPayload.TYPE, (payload, context) -> {
			var player = context.player();
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
		});

		// Login cleanup. We deliberately never strip the animation_id marker DURING a session: an async strip that
		// mutates an inventory or cursor stack mid-session emits a slot/cursor update that can collide with a
		// concurrent click prediction on the client, briefly rendering a duplicated shulker. A leftover marker is
		// harmless in the meantime (an id with no live animation renders as a closed lid, and it stays identical on
		// client and server so click hashing still matches), so we clear it at the next safe point: login. There is
		// never a live animation at join time, and the whole inventory re-syncs on join, so stripping here is safe.
		// Login also covers every way a previous session could end (quit, kick, crash) and cleans markers left by
		// earlier sessions.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			Inventory inventory = handler.player.getInventory();
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
						cleaned, handler.player.getName().getString());
			}
			// Sync the current game-rule state to the joining player (custom rules are not synced automatically).
			ServerPlayNetworking.send(handler.player, gameRuleState(server));
		});

		// Drop any Pocket-Build state when a player leaves, so a stale selected slot never lingers server-side.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PocketBuildServerState.exit(handler.player.getUUID()));

		LOGGER.info("Builder's Shulkers initialized");
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
			ServerPlayNetworking.send(player, payload);
		}
	}
}
