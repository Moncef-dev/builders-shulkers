package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.client.mixin.CreativeSlotWrapperAccessor;
import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class ShulkerClickHandler {
	private ShulkerClickHandler() {}

	// Starts an open (and returns true) only for a plain right-click, with an empty cursor, on a shulker box in
	// one of the player's own non-equipment inventory slots. Anything else falls through to vanilla handling.
	public static boolean tryHandleSlotClick(Slot slot, int slotId, int mouseButton, ClickType input) {
		if (input != ClickType.PICKUP || mouseButton != 1 || slot == null) return false;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return false;
		if (!mc.player.containerMenu.getCarried().isEmpty()) return false;

		Slot effectiveSlot = unwrapCreativeSlot(slot);
		Inventory inventory = mc.player.getInventory();
		if (effectiveSlot.container != inventory) return false;
		int containerSlot = effectiveSlot.getContainerSlot();
		if (Inventory.EQUIPMENT_SLOT_MAPPING.containsKey(containerSlot)) return false;

		ItemStack stack = effectiveSlot.getItem();
		// Detect shulkers by the vanilla tag (not a hardcoded class): recognizes modded shulkers in the tag too.
		if (!ShulkerContents.isShulker(stack)) return false;
		// Only intercept if the server runs the mod and can receive our open request. On a server without the
		// mod, fall through to vanilla so the shulker keeps its normal right-click behavior (no dead clicks).
		if (!ClientPlatform.network().canSend(OpenShulkerPayload.TYPE)) return false;
		// Respect the server-synced feature gate: if inventory access is disabled, leave the right-click to vanilla.
		if (!ClientGameRuleState.inventoryAccess()) return false;

		// Begin the lid opening, resuming from the shulker's current openness so a quick reopen continues from where
		// the closing lid is instead of snapping shut. Shared with Pocket-Build via ClientShulkerSession
		// .beginHeldOpening; armScreen=true also arms this GUI flow's pending-screen cleanup.
		long animationId = ClientShulkerSession.beginHeldOpening(stack, true);
		ClientPlatform.network().send(new OpenShulkerPayload(containerSlot, animationId));
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
