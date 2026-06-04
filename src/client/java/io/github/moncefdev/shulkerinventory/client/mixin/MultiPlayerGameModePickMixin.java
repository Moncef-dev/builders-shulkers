package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
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
// so it cannot coexist with the mode: pick-block EXITS Pocket-Build first (at the head, before the pick packet is sent),
// then the pick runs vanilla and unlocked, and the selected slot follows the server normally - no desync window.
//
// But it exits ONLY when the pick will ACTUALLY change the selected slot, replicating the server's tryPickItem
// condition exactly (same vanilla calls): creative (infinite materials) always picks; in survival the pick happens only
// if the player ALREADY has the block's / entity's item somewhere in the inventory. A no-op pick (survival, no matching
// item) changes nothing server-side, so it must leave the mode untouched.
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModePickMixin {
	@Inject(method = "handlePickItemFromBlock", at = @At("HEAD"))
	private void shulkerInventory$exitOnPickBlock(BlockPos pos, boolean includeData, CallbackInfo ci) {
		if (!PocketBuildMode.isActive()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		Level level = mc.level;
		if (player == null || level == null) {
			return;
		}
		// Mirror the server: includeData is forced off unless the player has infinite materials (creative).
		boolean serverIncludeData = player.hasInfiniteMaterials() && includeData;
		ItemStack pick = level.getBlockState(pos).getCloneItemStack(level, pos, serverIncludeData);
		if (shulkerInventory$willPick(player, pick)) {
			PocketBuildClient.exitMode();
		}
	}

	@Inject(method = "handlePickItemFromEntity", at = @At("HEAD"))
	private void shulkerInventory$exitOnPickEntity(Entity entity, boolean includeData, CallbackInfo ci) {
		if (!PocketBuildMode.isActive()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		if (shulkerInventory$willPick(player, entity.getPickResult())) {
			PocketBuildClient.exitMode();
		}
	}

	// The server's tryPickItem condition: creative (infinite materials) always picks; otherwise the pick happens only
	// when the player already holds a matching item somewhere in the inventory. Anything else is a no-op.
	private static boolean shulkerInventory$willPick(LocalPlayer player, ItemStack pick) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return pick != null && !pick.isEmpty() && player.getInventory().findSlotMatchingItem(pick) != -1;
	}
}
