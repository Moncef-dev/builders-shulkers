package com.example.shulkerinventory.client;

import com.example.shulkerinventory.client.mixin.CreativeSlotWrapperAccessor;
import com.example.shulkerinventory.network.OpenShulkerPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

public final class ShulkerClickHandler {
	private ShulkerClickHandler() {}

	// Starts an open (and returns true) only for a plain right-click, with an empty cursor, on a shulker box in
	// one of the player's own non-equipment inventory slots. Anything else falls through to vanilla handling.
	public static boolean tryHandleSlotClick(Slot slot, int slotId, int mouseButton, ContainerInput input) {
		if (input != ContainerInput.PICKUP || mouseButton != 1 || slot == null) return false;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		if (!mc.player.containerMenu.getCarried().isEmpty()) return false;

		Slot effectiveSlot = unwrapCreativeSlot(slot);
		Inventory inventory = mc.player.getInventory();
		if (effectiveSlot.container != inventory) return false;
		int containerSlot = effectiveSlot.getContainerSlot();
		if (Inventory.EQUIPMENT_SLOT_MAPPING.containsKey(containerSlot)) return false;

		ItemStack stack = effectiveSlot.getItem();
		if (stack.isEmpty()) return false;
		if (!(stack.getItem() instanceof BlockItem blockItem)) return false;
		if (!(blockItem.getBlock() instanceof ShulkerBoxBlock)) return false;

		long animationId = ClientShulkerSession.allocateId();
		ClientShulkerSession.startOpening(animationId);
		ClientPlayNetworking.send(new OpenShulkerPayload(containerSlot, animationId));
		return true;
	}

	// Creative-mode slots are wrapped; unwrap to reach the real inventory slot behind them.
	private static Slot unwrapCreativeSlot(Slot slot) {
		if (slot instanceof CreativeSlotWrapperAccessor wrapper) {
			return wrapper.shulkerInventory$getTarget();
		}
		return slot;
	}
}
