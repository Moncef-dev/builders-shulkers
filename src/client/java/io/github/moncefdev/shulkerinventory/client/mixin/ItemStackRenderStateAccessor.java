package io.github.moncefdev.shulkerinventory.client.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Reaches the render state's layer array and active count, so the Pocket-Build content layers (appended after the
// shulker's own) can be located and scaled down to sit inside the box.
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] shulkerInventory$getLayers();

	@Accessor("activeLayerCount")
	int shulkerInventory$getActiveLayerCount();
}
