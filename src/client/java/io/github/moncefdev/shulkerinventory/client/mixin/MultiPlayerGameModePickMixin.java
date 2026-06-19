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

// In Pocket-Build, the VANILLA middle-click pick is routed through the shared PocketBuildClient.handlePocketBuildPick:
// priority 1 selects a matching item from the held shulker (cancel the vanilla pick, stay in the mode), priority 2
// exits the mode and lets the pick run when it would actually take an item, priority 3 is a no-op.
//
// Why pick must exit the mode (priority 2): pick-block is server-authoritative and CHANGES the selected hotbar slot
// (Inventory.addAndPickItem -> setSelectedSlot). In Pocket-Build the local hotbar selection is LOCKED
// (InventorySelectedSlotMixin cancels setSelectedSlot), so the server's slot change can never apply on the client and
// the two desync (held slot corruption). So the mode is exited at the head, before the pick packet, and the pick then
// runs unlocked. The Litematica schematic pick reuses the same handlePocketBuildPick via LitematicaPickBlockCompat.
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
		if (PocketBuildClient.handlePocketBuildPick(player, pick)) {
			ci.cancel();
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
		if (PocketBuildClient.handlePocketBuildPick(player, pick)) {
			ci.cancel();
		}
	}
}
