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
		// Build-only: a non-usable selection predicts nothing (mirrors the server gate so prediction matches authority).
		if (!content.isEmpty() && !PocketBuildRules.isUsable(content)) {
			return InteractionResult.FAIL;
		}
		int handSlot = player.getInventory().getSelectedSlot();
		ItemStack held = content.copy();
		player.getInventory().setItem(handSlot, held);
		try {
			return body.get();
		} finally {
			player.getInventory().setItem(handSlot, shulker);
		}
	}
}
