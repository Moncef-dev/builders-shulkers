package io.github.moncefdev.shulkerinventory;

import net.minecraft.core.NonNullList;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

// Server-authoritative content-swap, shared by the block path (ServerPlayerGameModeMixin) and the entity path
// (PlayerInteractOnMixin). Briefly puts the SELECTED content of the held shulker in hand, runs the WHOLE vanilla
// flow on it (the caller supplies it as body), then ALWAYS restores the shulker in a finally and removes from the
// shulker's CONTAINER exactly what vanilla consumed. The try/finally is the anti-duplication invariant (the held
// shulker can never be lost mid-swap, even if the vanilla flow throws); keeping it here means that invariant lives
// in one place for both vanilla operations instead of being copied per mixin.
public final class PocketBuildContentSwap {
	private PocketBuildContentSwap() {}

	public static InteractionResult runOnServer(Player player, ItemStack shulker,
			Function<ItemStack, InteractionResult> body) {
		NonNullList<ItemStack> items = ShulkerContents.read(shulker);
		// Nothing selected (empty shulker -> slot -1): swap an EMPTY hand so the vanilla flow runs with no item
		// (places nothing / just the bare interaction), never falling through to using the shulker itself.
		int slot = PocketBuildServerState.selectedSlot(player.getUUID());
		ItemStack content = slot >= 0 ? items.get(slot) : ItemStack.EMPTY;
		int handSlot = player.getInventory().getSelectedSlot();
		ItemStack held = content.copy();
		player.getInventory().setItem(handSlot, held);
		try {
			// Pass the swapped-in content as the held item so the whole vanilla flow uses it (the block path also
			// forwards it as its stack argument; the entity path reads the hand itself).
			return body.apply(held);
		} finally {
			player.getInventory().setItem(handSlot, shulker);
			int consumed = content.getCount() - held.getCount();
			if (consumed > 0) {
				ItemStack remaining = content.copy();
				remaining.shrink(consumed);
				items.set(slot, remaining);
				ShulkerContents.write(shulker, items);
			}
		}
	}
}
