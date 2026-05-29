package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.AnimationIdHolder;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Adds the animation id storage (AnimationIdHolder) to the item entity render state.
@Mixin(ItemEntityRenderState.class)
public abstract class ItemEntityRenderStateMixin implements AnimationIdHolder {
	@Unique
	private long shulkerInventory$animationId = 0L;

	@Override
	public long shulkerInventory$getAnimationId() {
		return shulkerInventory$animationId;
	}

	@Override
	public void shulkerInventory$setAnimationId(long id) {
		shulkerInventory$animationId = id;
	}
}
