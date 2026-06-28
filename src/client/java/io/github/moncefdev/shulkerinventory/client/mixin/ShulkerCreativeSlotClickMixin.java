package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ShulkerClickHandler;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Same as ShulkerSlotClickMixin but for the creative inventory screen, which handles clicks separately.
@Mixin(CreativeModeInventoryScreen.class)
public abstract class ShulkerCreativeSlotClickMixin {
	@Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$detectRightClickOnShulker(
			Slot slot, int slotId, int mouseButton, ClickType clickType, CallbackInfo ci) {
		if (ShulkerClickHandler.tryHandleSlotClick(slot, slotId, mouseButton, clickType)) {
			ci.cancel();
		}
	}
}
