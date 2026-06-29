package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.AnimationIdHolder;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// Adds the animation id storage (AnimationIdHolder) to the held-item render state, so the third-person held-shulker lid
// animation is driven from the render state instead of the raw ItemStack. submitArmWithItem receives this render state
// on every version, whereas the raw ItemStack only reaches it on 1.21.11, which added that parameter (1.21.10 has
// none); routing through the render state keeps the feature at full parity across versions through one path.
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateAnimationMixin implements AnimationIdHolder {
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
