package io.github.moncefdev.shulkerinventory;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import io.github.moncefdev.shulkerinventory.network.AnimationFinishedPayload;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
		PayloadTypeRegistry.serverboundPlay().register(AnimationFinishedPayload.TYPE, AnimationFinishedPayload.STREAM_CODEC);
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
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SHULKER_BOX_OPEN, SoundSource.BLOCKS,
					0.5f, player.level().getRandom().nextFloat() * 0.1f + 0.9f);
			player.openMenu(provider);
			// Mirror the opening lid animation to the other players who can see this player.
			InventoryShulkerBoxMenu.broadcastAnimation(player, animationId, true);
		});

		ServerPlayNetworking.registerGlobalReceiver(AnimationFinishedPayload.TYPE, (payload, context) -> {
			var player = context.player();
			long animationId = payload.animationId();
			Inventory inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				Long stackId = ShulkerAnimationMarker.get(stack);
				if (stackId != null && stackId == animationId) {
					ShulkerAnimationMarker.remove(stack);
				}
			}
		});

		// Login cleanup (safety net). The animation_id marker is normally removed by AnimationFinishedPayload
		// when the closing animation ends. If that payload never arrives (disconnect or crash mid-animation),
		// a harmless leftover marker can persist on a shulker. There is never a live animation at join time, so
		// strip any leftover marker from the joining player's inventory. This also retroactively cleans markers
		// leaked by earlier sessions.
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
