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
		// Build-only: a non-usable selection (anything that isn't a placeable block) does nothing. The shulker is never
		// swapped out of hand here, so it can be neither placed nor lost. The future gamerule widens isUsable.
		if (!content.isEmpty() && !PocketBuildRules.isUsable(content)) {
			return InteractionResult.FAIL;
		}
		int handSlot = player.getInventory().getSelectedSlot();
		ItemStack held = content.copy();
		player.getInventory().setItem(handSlot, held);
		try {
			// Pass the swapped-in content as the held item so the whole vanilla flow uses it (the block path also
			// forwards it as its stack argument; the entity path reads the hand itself).
			return body.apply(held);
		} finally {
			// Write back the ACTUAL post-use stack from the hand (not a count delta): it captures everything the vanilla
			// flow did to the content - plain count for a block, but also item REPLACEMENT/emptying (a powder snow bucket
			// becomes an empty bucket) and, once the gamerule allows tools, durability/component changes. Read it before
			// restoring the shulker. Items are conserved: the slot goes content -> afterUse, the difference is what
			// vanilla placed/consumed into the world.
			ItemStack afterUse = player.getInventory().getItem(handSlot).copy();
			player.getInventory().setItem(handSlot, shulker);
			if (slot >= 0) {
				items.set(slot, afterUse);
				ShulkerContents.write(shulker, items);
			}
		}
	}
}
