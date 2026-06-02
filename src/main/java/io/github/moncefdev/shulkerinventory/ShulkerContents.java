package io.github.moncefdev.shulkerinventory;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

import java.util.List;

// Shared read/write helpers for a shulker item's contents (its vanilla minecraft:container component), as a
// fixed-size list. The slot count is the vanilla shulker capacity (ShulkerBoxBlockEntity.CONTAINER_SIZE), so it
// tracks vanilla instead of a magic 27 repeated across the mod. Lives in the common source set: both the server
// (open handler, content-swap mixins, menu save) and the client (Pocket-Build state) read shulker contents the
// same way, so the format can never diverge between them.
public final class ShulkerContents {
	private ShulkerContents() {}

	// The number of content slots in a shulker, straight from vanilla (rows x columns).
	public static final int SIZE = ShulkerBoxBlockEntity.CONTAINER_SIZE;

	// Reads the stack's CONTAINER component into a fresh SIZE-slot list (empty for unused slots).
	public static NonNullList<ItemStack> read(ItemStack shulker) {
		NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	// Writes the given items back into the stack's CONTAINER component.
	public static void write(ItemStack shulker, List<ItemStack> items) {
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}
}
