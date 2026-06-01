package io.github.moncefdev.shulkerinventory;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShulkerInventory implements ModInitializer {
	public static final String MOD_ID = "shulker-inventory";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		PayloadTypeRegistry.serverboundPlay().register(OpenShulkerPayload.TYPE, OpenShulkerPayload.STREAM_CODEC);
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
			if (stack.isEmpty() || !stack.typeHolder().is(ItemTags.SHULKER_BOXES)) {
				LOGGER.warn("Player {} tried to open non-shulker stack at slot {}: {}",
						player.getName().getString(), slotIndex, stack);
				return;
			}

			ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
			NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
			contents.copyInto(items);

			SimpleContainer shulkerContent = new SimpleContainer(27);
			for (int i = 0; i < 27; i++) {
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
			boolean held = slotIndex == inventory.getSelectedSlot() || slotIndex == Inventory.SLOT_OFFHAND;
			if (held) {
				InventoryShulkerBoxMenu.broadcastAnimation(player, animationId, true, true);
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
		});

		LOGGER.info("Shulker Inventory initialized");
	}
}
