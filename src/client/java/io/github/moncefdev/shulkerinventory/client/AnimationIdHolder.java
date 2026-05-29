package io.github.moncefdev.shulkerinventory.client;

// Mixin-injected accessor that lets us stash an animation id on an item entity's render state (duck typing),
// since that render state has no field of our own.
public interface AnimationIdHolder {
	long shulkerInventory$getAnimationId();

	void shulkerInventory$setAnimationId(long id);
}
