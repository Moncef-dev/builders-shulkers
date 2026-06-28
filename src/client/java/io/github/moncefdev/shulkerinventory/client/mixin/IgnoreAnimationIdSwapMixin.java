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

// Suppresses the first-person hand swap animation when the old and new held stack differ only in ways that must not
// replay it: our animation marker, or the container/bundle contents that our feature edits LIVE while the box is held.
// The marker lives inside vanilla minecraft:custom_data, which (unlike the old custom component that carried
// .ignoreSwapAnimation()) vanilla does NOT ignore in the swap-equality check; and minecraft:container is likewise not
// flagged, so without this the hand would dip and rise every time we add/remove the marker OR change the contents.
// This extends vanilla's OWN ignoreSwapAnimation opt-out, which vanilla applies to minecraft:damage for the same
// reason (an item whose state changes during normal use should not make the hand dip). Vanilla never flags
// container/bundle_contents only because it never edits a held container; we do.
@Mixin(ItemInHandRenderer.class)
public abstract class IgnoreAnimationIdSwapMixin {
	// Components a content edit changes that must NOT, on their own, replay the swap animation (the marker in
	// custom_data is handled separately below). Comparing keys ourselves also covers add/remove of these components,
	// which vanilla's matchesIgnoringComponents would miss (it short-circuits on a component-map size mismatch before
	// consulting the per-type ignore predicate).
	private static final Set<DataComponentType<?>> SWAP_IGNORED_COMPONENTS =
			Set.of(DataComponents.CONTAINER, DataComponents.BUNDLE_CONTENTS);

	@Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$ignoreMarkerChange(ItemStack oldStack, ItemStack newStack, CallbackInfoReturnable<Boolean> cir) {
		if (oldStack.isEmpty() || newStack.isEmpty()) return;
		if (!oldStack.is(newStack.getItem())) return;
		if (oldStack.getCount() != newStack.getCount()) return;
		if (diffIsOnlySwapCosmetic(oldStack, newStack)) {
			cir.setReturnValue(true);
		}
	}

	// True if the two stacks are equal apart from the swap-cosmetic components (our marker inside custom_data, and the
	// container/bundle contents).
	private static boolean diffIsOnlySwapCosmetic(ItemStack a, ItemStack b) {
		Set<DataComponentType<?>> allKeys = new HashSet<>();
		allKeys.addAll(a.getComponents().keySet());
		allKeys.addAll(b.getComponents().keySet());
		for (DataComponentType<?> type : allKeys) {
			if (type == DataComponents.CUSTOM_DATA) continue;
			if (SWAP_IGNORED_COMPONENTS.contains(type)) continue;
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
