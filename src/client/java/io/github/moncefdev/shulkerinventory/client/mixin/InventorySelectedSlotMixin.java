package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While Pocket-Build mode is active, lock the LOCAL player's hotbar selection: block every vanilla slot change
// (number keys 1-9 and any other caller of setSelectedSlot) so the source shulker stays in hand. Scoped to the
// client player's own inventory by identity, so in singleplayer the integrated server's inventory is never affected
// (its setSelectedSlot keeps working, and the client simply never sends a slot change while locked).
@Mixin(Inventory.class)
public abstract class InventorySelectedSlotMixin {
	@Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$lockHotbar(int slot, CallbackInfo ci) {
		if (!PocketBuildMode.isActive()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && (Object) this == mc.player.getInventory()) {
			ci.cancel();
		}
	}
}
