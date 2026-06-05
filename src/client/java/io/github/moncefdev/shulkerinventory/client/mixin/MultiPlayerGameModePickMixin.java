package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

// In Pocket-Build, middle-click FIRST tries to select a matching item from the held shulker (priority 1, 1.1.2): if the
// picked block/entity item is already in the box (same item + components, first slot, like vanilla findSlotMatchingItem),
// that slot is selected (synced like a scroll) and the vanilla pick is cancelled, so you stay in the mode. Only if it is
// NOT in the box does the original exit-and-pick behaviour below run (priority 2: take it from the inventory; 3: no-op).
//
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
	@Inject(method = "handlePickItemFromBlock", at = @At("HEAD"), cancellable = true)
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
		// Priority 1: the picked block is already in the held shulker -> select that slot, stay in the mode, cancel the
		// vanilla pick (no hotbar change, no desync).
		if (shulkerInventory$pickIntoShulker(player, pick)) {
			ci.cancel();
			return;
		}
		// Priority 2/3: leave the mode and let the vanilla pick run (take a matching item from the inventory, or no-op).
		if (shulkerInventory$willPick(player, pick)) {
			PocketBuildClient.exitMode();
		}
	}

	@Inject(method = "handlePickItemFromEntity", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$exitOnPickEntity(Entity entity, boolean includeData, CallbackInfo ci) {
		if (!PocketBuildMode.isActive()) {
			return;
		}
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return;
		}
		ItemStack pick = entity.getPickResult();
		if (shulkerInventory$pickIntoShulker(player, pick)) {
			ci.cancel();
			return;
		}
		if (shulkerInventory$willPick(player, pick)) {
			PocketBuildClient.exitMode();
		}
	}

	// Priority 1 of the Pocket-Build pick-block: if the picked item is already in the held shulker, select that slot
	// (synced to the server like a scroll), and report it handled so the caller cancels the vanilla pick and stays in the
	// mode. The in-box content render follows on the next client tick. Returns false if the item is not in the box.
	private static boolean shulkerInventory$pickIntoShulker(LocalPlayer player, ItemStack pick) {
		if (!PocketBuildMode.selectMatchingSlot(player.getMainHandItem(), pick)) {
			return false;
		}
		if (ClientPlayNetworking.canSend(PocketBuildSelectPayload.TYPE)) {
			ClientPlayNetworking.send(new PocketBuildSelectPayload(PocketBuildMode.selectedContentSlot()));
		}
		return true;
	}

	// The server's tryPickItem condition: creative (infinite materials) always picks; otherwise the pick happens only
	// when the player already holds a matching item somewhere in the inventory. Anything else is a no-op.
	// RE-VERIFY against vanilla tryPickItem at each Minecraft update: this is a hand-copied condition, the one vanilla
	// rule the mod re-implements rather than reuses. If vanilla adds a new way to pick and this copy still returns false,
	// we would NOT exit while the server DOES change the slot - the locked-hotbar desync above silently returns.
	private static boolean shulkerInventory$willPick(LocalPlayer player, ItemStack pick) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		return pick != null && !pick.isEmpty() && player.getInventory().findSlotMatchingItem(pick) != -1;
	}
}
