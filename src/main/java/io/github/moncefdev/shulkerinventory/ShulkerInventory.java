package io.github.moncefdev.shulkerinventory;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildModePayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShulkerInventory implements ModInitializer {
	public static final String MOD_ID = "shulker-inventory";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(OpenShulkerPayload.TYPE, OpenShulkerPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketBuildModePayload.TYPE, PocketBuildModePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketBuildSelectPayload.TYPE, PocketBuildSelectPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenPlayerInventoryPayload.TYPE, OpenPlayerInventoryPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RemoteShulkerAnimationPayload.TYPE, RemoteShulkerAnimationPayload.STREAM_CODEC);

		// Open handler: validate the slot, toggle closed if this shulker is already open, otherwise copy the
		// stack's CONTAINER component into a working container and open a vanilla shulker menu bound to it.
		ServerPlayNetworking.registerGlobalReceiver(OpenShulkerPayload.TYPE, (payload, context) -> {
			var player = context.player();
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
			// Mirror the opening lid animation/sound to the OTHER players who can see this player, but only when the
			// shulker is HELD (visible in hand): an open from an invisible inventory slot has nothing for them to
			// see or hear. The opener's own animation and sound are handled on their own client.
			boolean held = InventoryShulkerBoxMenu.isHeldSlot(inventory, slotIndex);
			if (held) {
				InventoryShulkerBoxMenu.broadcastAnimation(player, animationId, true, true);
			}
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
			if (!ShulkerContents.isShulker(stack)) {
				return;
			}
			if (payload.entering()) {
				PocketBuildServerState.enter(player.getUUID(), payload.contentSlot());
				ShulkerAnimationMarker.set(stack, payload.animationId());
				InventoryShulkerBoxMenu.broadcastAnimation(player, payload.animationId(), true, true);
			} else {
				PocketBuildServerState.exit(player.getUUID());
				InventoryShulkerBoxMenu.broadcastAnimation(player, payload.animationId(), false, true);
			}
		});

		// Pocket-Build selection: keep the server's selected content slot in sync as the player scrolls, so a normal
		// vanilla use-on packet swaps in the right content (handled by ServerPlayerGameModeMixin).
		ServerPlayNetworking.registerGlobalReceiver(PocketBuildSelectPayload.TYPE, (payload, context) ->
				PocketBuildServerState.select(context.player().getUUID(), payload.contentSlot()));

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
		});

		// Drop any Pocket-Build state when a player leaves, so a stale selected slot never lingers server-side.
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PocketBuildServerState.exit(handler.player.getUUID()));

		LOGGER.info("Shulker Inventory initialized");
	}
}
