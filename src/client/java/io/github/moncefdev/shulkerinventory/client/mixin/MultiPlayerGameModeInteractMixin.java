package io.github.moncefdev.shulkerinventory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.client.PocketBuildClientSwap;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

// Client-side counterpart of MultiPlayerGameModeMixin (blocks) for the ENTITY path: while in Pocket-Build mode, a
// PLAIN right-click on an entity (e.g. an item frame) runs the vanilla interaction on the SELECTED CONTENT, so the
// client predicts it (and plays its sound). interact() sends the interact packet then calls player.interactOn for the
// local prediction; we swap the content in before both. The server stays authoritative via PlayerInteractOnMixin.
// Single-player works through PlayerInteractOnMixin alone (shared state); this wrap is what keeps the prediction right
// on a dedicated server, where the client has no Pocket-Build server state.
// Ctrl + right-click is the mode toggle: the use-event callback cancels that click (returns FAIL) before any
// interaction runs, so even though this wrap swaps content in for it, nothing is placed and the finally restores
// the shulker.
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeInteractMixin {
	// 1.21.11 interact is (Player, Entity, InteractionHand); 26.x added an EntityHitResult parameter.
	@WrapMethod(method = "interact")
	private InteractionResult shulkerInventory$useContentOnEntity(Player player, Entity entity,
			InteractionHand hand, Operation<InteractionResult> original) {
		if (hand != InteractionHand.MAIN_HAND || !PocketBuildMode.isActive()) {
			return original.call(player, entity, hand);
		}
		ItemStack shulker = player.getMainHandItem();
		if (!ShulkerContents.isShulker(shulker)) {
			return original.call(player, entity, hand);
		}
		return PocketBuildClientSwap.runPredicted(player, shulker, () -> original.call(player, entity, hand));
	}
}
