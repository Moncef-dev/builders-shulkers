package io.github.moncefdev.shulkerinventory;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

// Stores the transient lid-animation marker inside the VANILLA minecraft:custom_data component, under a namespaced
// key, instead of a custom DataComponentType. Why: a custom component type is a SYNCED registry entry, so clients
// without this mod are kicked at join ("unknown registry entry"). custom_data is a vanilla component that every
// client has, so non-mod clients can join, decode stacks that carry it, and simply ignore the extra NBT key.
// Mod clients read the marker to drive the lid animation. The id is removed when the close animation finishes.
public final class ShulkerAnimationMarker {
	public static final String KEY = "builders-shulkers:animation_id";

	private ShulkerAnimationMarker() {}

	public static Long get(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return null;
		return data.copyTag().getLong(KEY).orElse(null);
	}

	public static void set(ItemStack stack, long animationId) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putLong(KEY, animationId));
	}

	public static void remove(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) return;
		CompoundTag tag = data.copyTag();
		if (!tag.contains(KEY)) return;
		tag.remove(KEY);
		if (tag.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		} else {
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
	}
}
