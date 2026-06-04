package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.PocketBuildRules;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

// Client-prediction counterpart of PocketBuildContentSwap, shared by the block wrap (MultiPlayerGameModeMixin) and
// the entity wrap (MultiPlayerGameModeInteractMixin). Briefly puts the selected content in hand so the client
// predicts the vanilla use/interact on it (and plays its sound), then ALWAYS restores the shulker in a finally.
// No CONTAINER write-back: the server is authoritative over the contents and syncs them back; the client only
// predicts the action. An empty selection swaps an EMPTY hand, so the prediction places nothing.
public final class PocketBuildClientSwap {
	private PocketBuildClientSwap() {}

	public static InteractionResult runPredicted(Player player, ItemStack shulker, Supplier<InteractionResult> body) {
		ItemStack content = PocketBuildMode.selectedStack(shulker);
		// Swap the content in only when it is actually usable (a placeable block). For an empty or non-usable selection,
		// run the SAME vanilla flow but with an EMPTY hand: it places/uses nothing, yet the use-event callbacks still
		// fire and the bare interaction is not swallowed. That matters because both leaving the mode with Ctrl +
		// right-click and "open a chest to leave" rely on the vanilla flow running - returning FAIL here instead skipped
		// it entirely, so with a non-usable item selected the mode could not be closed by aiming at a block, and chests
		// would not open. Mirrors the server gate (PocketBuildContentSwap) so prediction matches authority.
		boolean swap = !content.isEmpty() && PocketBuildRules.isUsable(content);
		int handSlot = player.getInventory().getSelectedSlot();
		player.getInventory().setItem(handSlot, swap ? content.copy() : ItemStack.EMPTY);
		try {
			return body.get();
		} finally {
			player.getInventory().setItem(handSlot, shulker);
		}
	}
}
