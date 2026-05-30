package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

// Suppresses the first-person hand swap animation when the only difference between the old and new held stack is
// our animation marker. The marker now lives inside vanilla minecraft:custom_data, which (unlike the old custom
// component that carried .ignoreSwapAnimation()) vanilla does NOT ignore in the swap-equality check, so without
// this the hand would jolt every time we add/remove the marker.
@Mixin(ItemInHandRenderer.class)
public abstract class IgnoreAnimationIdSwapMixin {
	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$ignoreMarkerChange(ItemStack oldStack, ItemStack newStack, CallbackInfoReturnable<Boolean> cir) {
		if (oldStack.isEmpty() || newStack.isEmpty()) return;
		if (!oldStack.is(newStack.getItem())) return;
		if (oldStack.getCount() != newStack.getCount()) return;
		if (diffIsOnlyMarker(oldStack, newStack)) {
			cir.setReturnValue(true);
		}
	}

	// True if the two stacks are equal apart from our marker key inside custom_data.
	private static boolean diffIsOnlyMarker(ItemStack a, ItemStack b) {
		Set<DataComponentType<?>> allKeys = new HashSet<>();
		allKeys.addAll(a.getComponents().keySet());
		allKeys.addAll(b.getComponents().keySet());
		for (DataComponentType<?> type : allKeys) {
			if (type == DataComponents.CUSTOM_DATA) continue;
			if (!Objects.equals(a.get(type), b.get(type))) return false;
		}
		return customDataWithoutMarker(a).equals(customDataWithoutMarker(b));
	}

	private static CompoundTag customDataWithoutMarker(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag tag = data == null ? new CompoundTag() : data.copyTag();
		tag.remove(ShulkerAnimationMarker.KEY);
		return tag;
	}
}
