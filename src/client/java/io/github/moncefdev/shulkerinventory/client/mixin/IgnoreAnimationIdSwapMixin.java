package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.ShulkerInventoryComponents;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// Stops the first-person hand swap animation from firing when the only difference between the old and new held
// stack is our transient animation_id component (otherwise opening a held shulker would jolt the hand).
@Mixin(ItemInHandRenderer.class)
public abstract class IgnoreAnimationIdSwapMixin {
	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$ignoreAnimationIdChange(ItemStack oldStack, ItemStack newStack, CallbackInfoReturnable<Boolean> cir) {
		if (oldStack.isEmpty() || newStack.isEmpty()) return;
		if (!oldStack.is(newStack.getItem())) return;
		if (oldStack.getCount() != newStack.getCount()) return;
		if (diffIsOnlyAnimationId(oldStack, newStack)) {
			cir.setReturnValue(true);
		}
	}

	private static boolean diffIsOnlyAnimationId(ItemStack a, ItemStack b) {
		Set<DataComponentType<?>> allKeys = new HashSet<>();
		allKeys.addAll(a.getComponents().keySet());
		allKeys.addAll(b.getComponents().keySet());
		for (DataComponentType<?> type : allKeys) {
			if (type == ShulkerInventoryComponents.ANIMATION_ID) continue;
			if (!Objects.equals(a.get(type), b.get(type))) return false;
		}
		return true;
	}
}
