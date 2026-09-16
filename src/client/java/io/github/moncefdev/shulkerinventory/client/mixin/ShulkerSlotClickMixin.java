package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ShulkerClickHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Routes right-clicks on a shulker (in the survival inventory and container screens) to our open handler.
@Mixin(AbstractContainerScreen.class)
public abstract class ShulkerSlotClickMixin {
	// Pinned to the (slot, slotId, button, clickType) funnel by descriptor: 26.3 added a second slotClicked overload
	// that only adapts a MouseButtonEvent to the button int before delegating here, so a name-only match would bind
	// two methods with incompatible signatures.
	@Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
			at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$detectRightClickOnShulker(
			Slot slot, int slotId, int mouseButton, ContainerInput clickType, CallbackInfo ci) {
		if (ShulkerClickHandler.tryHandleSlotClick(slot, slotId, mouseButton, clickType)) {
			ci.cancel();
		}
	}
}
