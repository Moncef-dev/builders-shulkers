package io.github.moncefdev.shulkerinventory.menu;

import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

// Reuses the vanilla ShulkerBoxMenu (free mod compatibility and vanilla slot handling), backed by a working
// copy of the source stack's CONTAINER contents. The working copy is written back to the stack on close or on
// any interaction that disturbs the source slot, so all item moves stay server-authoritative.
public class InventoryShulkerBoxMenu extends ShulkerBoxMenu {
	private final SimpleContainer shulkerContent;
	private final int sourceSlotIndex;
	private final int sourceSlotInMenu;
	private boolean alreadySaved = false;

	public InventoryShulkerBoxMenu(int syncId, Inventory playerInventory, SimpleContainer shulkerContent, int sourceSlotIndex) {
		super(syncId, playerInventory, shulkerContent);
		this.shulkerContent = shulkerContent;
		this.sourceSlotIndex = sourceSlotIndex;
		this.sourceSlotInMenu = findMenuSlotIndex(playerInventory, sourceSlotIndex);
	}

	private int findMenuSlotIndex(Inventory inventory, int containerSlot) {
		for (int i = 0; i < this.slots.size(); i++) {
			Slot s = this.slots.get(i);
			if (s.container == inventory && s.getContainerSlot() == containerSlot) {
				return i;
			}
		}
		return -1;
	}

	public int getSourceSlotIndex() {
		return sourceSlotIndex;
	}

	public void closeAndReturnToInventory(ServerPlayer serverPlayer) {
		saveContents(serverPlayer);
		alreadySaved = true;
		playCloseSound(serverPlayer);
		closeMenuSilently(serverPlayer);
		finishReturnToInventory(serverPlayer);
	}

	// Syncs the inventory menu to the client and asks it to reopen the inventory screen. The authoritative cursor is
	// kept in sync by closeMenuSilently (which transfers the cursor onto the inventory menu, including an empty
	// cursor) plus this broadcast, so the client never keeps a stale cursor item.
	private void finishReturnToInventory(ServerPlayer serverPlayer) {
		serverPlayer.inventoryMenu.broadcastFullState();
		ServerPlayNetworking.send(serverPlayer, OpenPlayerInventoryPayload.INSTANCE);
	}

	@Override
	public void clicked(int slotId, int button, ContainerInput input, Player player) {
		// Commit-on-disturbance (anti-dup): if the action targets the source shulker stack itself, save the
		// worked contents into it, end the session, then replay the action on the inventory menu. Saving always
		// happens before the move, so the up-to-date stack is what gets picked up or relocated.
		if (touchesSourceSlot(slotId, button, input)) {
			broadcastFullState();
			if (player instanceof ServerPlayer serverPlayer) {
				saveContents(serverPlayer);
				alreadySaved = true;
				playCloseSound(serverPlayer);
				closeMenuSilently(serverPlayer);
				replayClickOnInventoryMenu(serverPlayer, slotId, button, input);
				finishReturnToInventory(serverPlayer);
			}
			return;
		}
		super.clicked(slotId, button, input, player);
		if (player instanceof ServerPlayer) {
			saveContents(player);
		}
	}

	private void closeMenuSilently(ServerPlayer serverPlayer) {
		ItemStack carriedSnapshot = this.getCarried();
		this.setCarried(ItemStack.EMPTY);
		serverPlayer.doCloseContainer();
		// Always transfer the cursor onto the inventory menu, INCLUDING an empty cursor. The shulker menu carried
		// is the authoritative cursor at close. Re-injecting only when non-empty (the old behavior) left any stale
		// inventoryMenu carried in place: a phantom from an earlier source-slot pickup that creative's
		// set-creative-slot placement never clears. That value then never changes, so vanilla never re-syncs it and
		// the client commits it as a real duplicate in creative. Setting it unconditionally flushes the phantom (to
		// empty) and, because the value changes, triggers the cursor re-sync to the client.
		serverPlayer.inventoryMenu.setCarried(carriedSnapshot);
	}

	private void replayClickOnInventoryMenu(ServerPlayer serverPlayer, int slotId, int button, ContainerInput input) {
		if (slotId < 0 || slotId >= this.slots.size()) {
			return;
		}
		Slot source = this.slots.get(slotId);
		if (!(source.container instanceof Inventory)) {
			return;
		}
		int targetSlot = findInventoryMenuSlotIndex(serverPlayer.inventoryMenu, source.getContainerSlot());
		if (targetSlot < 0) {
			return;
		}
		serverPlayer.inventoryMenu.clicked(targetSlot, button, input, serverPlayer);
	}

	private static int findInventoryMenuSlotIndex(InventoryMenu inventoryMenu, int containerSlot) {
		for (int i = 0; i < inventoryMenu.slots.size(); i++) {
			Slot s = inventoryMenu.slots.get(i);
			if (s.container instanceof Inventory && s.getContainerSlot() == containerSlot) {
				return i;
			}
		}
		return -1;
	}

	private boolean touchesSourceSlot(int slotId, int button, ContainerInput input) {
		if (slotId == sourceSlotInMenu) {
			return true;
		}
		if (input == ContainerInput.SWAP && button == sourceSlotIndex) {
			return true;
		}
		return false;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!alreadySaved) {
			saveContents(player);
			playCloseSound(player);
		}
	}

	private static void playCloseSound(Player player) {
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.SHULKER_CLOSE, SoundSource.BLOCKS,
				0.5f, player.level().getRandom().nextFloat() * 0.1f + 0.9f);
	}

	// Writes the working container back into the source stack's CONTAINER component. Guards that the source slot
	// still holds a shulker (it may have moved), to avoid writing onto the wrong item.
	private void saveContents(Player player) {
		ItemStack sourceStack = player.getInventory().getItem(sourceSlotIndex);
		if (sourceStack.isEmpty() || !sourceStack.typeHolder().is(ItemTags.SHULKER_BOXES)) {
			ShulkerInventory.LOGGER.warn(
					"Cannot save shulker contents: source slot {} no longer contains a shulker (item: {})",
					sourceSlotIndex, sourceStack);
			return;
		}

		List<ItemStack> items = new ArrayList<>(shulkerContent.getContainerSize());
		for (int i = 0; i < shulkerContent.getContainerSize(); i++) {
			items.add(shulkerContent.getItem(i));
		}
		sourceStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}
}
