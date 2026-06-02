package io.github.moncefdev.shulkerinventory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.moncefdev.shulkerinventory.PocketBuildContentSwap;
import io.github.moncefdev.shulkerinventory.PocketBuildServerState;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

// Content-swap (server authority): when the player is in Pocket-Build mode, a normal vanilla use-on-block runs on the
// SELECTED CONTENT of the held shulker instead of on the shulker. PocketBuildContentSwap briefly puts a copy of the
// content in the hand, runs the WHOLE vanilla flow on it, then restores the shulker and removes from its CONTAINER
// exactly what vanilla consumed. Everything emerges (interact-vs-place, adventure-mode permissions, orientation, the
// place sound for other players, survival-only consume); the shulker is never the item used; no reimplemented
// conditions. The swap helper wraps the flow in its own try/finally, so the shulker is ALWAYS restored even if the
// vanilla flow throws (anti-duplication: the held shulker can never be lost mid-swap).
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
	@WrapMethod(method = "useItemOn")
	private InteractionResult shulkerInventory$useContent(ServerPlayer player, Level level, ItemStack stack,
			InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
		if (hand != InteractionHand.MAIN_HAND || !PocketBuildServerState.isActive(player.getUUID())) {
			return original.call(player, level, stack, hand, hit);
		}
		ItemStack shulker = player.getMainHandItem();
		if (!ShulkerContents.isShulker(shulker)) {
			return original.call(player, level, stack, hand, hit);
		}
		// Pass the swapped-in content as BOTH the stack argument and the held item, so the whole vanilla flow uses it.
		return PocketBuildContentSwap.runOnServer(player, shulker, held -> original.call(player, level, held, hand, hit));
	}
}
