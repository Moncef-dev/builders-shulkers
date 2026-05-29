package io.github.moncefdev.shulkerinventory.client.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")
public interface CreativeSlotWrapperAccessor {
	@Accessor("target")
	Slot shulkerInventory$getTarget();
}
