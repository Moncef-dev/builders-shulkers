package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Pick-block (middle-click) is server-authoritative: the client sends a pick packet and the SERVER finds/creates the
// item, drops it in a hotbar slot, and CHANGES the selected slot to that slot (Inventory.addAndPickItem ->
// setSelectedSlot(getSuitableHotbarSlot())). In Pocket-Build mode the local hotbar selection is LOCKED
// (InventorySelectedSlotMixin cancels setSelectedSlot), so the server's selected-slot change can never be applied on
// the client: the two desync, which corrupts the held slot (ghost placements, and the source shulker flickering out of
// its inventory slot). A pick-block is fundamentally a hotbar-selection change, which the locked mode exists to forbid,
// so it cannot coexist with the mode: pick-block EXITS Pocket-Build first (at the head, before the pick packet is
// sent), then the pick runs vanilla and unlocked, and the selected slot follows the server normally - no desync.
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModePickMixin {
	@Inject(method = "handlePickItemFromBlock", at = @At("HEAD"))
	private void shulkerInventory$exitOnPickBlock(BlockPos pos, boolean includeData, CallbackInfo ci) {
		if (PocketBuildMode.isActive()) {
			PocketBuildClient.exitMode();
		}
	}

	@Inject(method = "handlePickItemFromEntity", at = @At("HEAD"))
	private void shulkerInventory$exitOnPickEntity(Entity entity, boolean includeData, CallbackInfo ci) {
		if (PocketBuildMode.isActive()) {
			PocketBuildClient.exitMode();
		}
	}
}
