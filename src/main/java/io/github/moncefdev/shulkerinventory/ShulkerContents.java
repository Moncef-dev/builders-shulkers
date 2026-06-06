package io.github.moncefdev.shulkerinventory;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
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

	// Whether this stack is a shulker box, detected by the vanilla tag (not a hardcoded class), so modded shulkers
	// in the tag are recognized too. The single detection used everywhere the mod special-cases a shulker.
	public static boolean isShulker(ItemStack stack) {
		return !stack.isEmpty() && stack.typeHolder().is(ItemTags.SHULKER_BOXES);
	}

	// Reads the stack's CONTAINER component into a fresh SIZE-slot list (empty for unused slots).
	public static NonNullList<ItemStack> read(ItemStack shulker) {
		NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
		return items;
	}

	// Writes the given items back into the stack's CONTAINER component, PRESERVING any slots beyond SIZE. Our UI only
	// edits the first SIZE (vanilla 27) slots, but a shulker added by another mod can be in the SHULKER_BOXES tag yet
	// carry an EXTENDED container (more than SIZE slots). So we read the full existing container, overwrite ONLY slots
	// 0..SIZE-1 with the edited items, and keep every slot past SIZE untouched - no item is ever lost. A vanilla-size
	// shulker has nothing past SIZE, so fullSize stays SIZE and the SIZE-slot loop overwrites the whole list: the result
	// is then byte-for-byte the old behaviour (no vanilla change).
	public static void write(ItemStack shulker, List<ItemStack> items) {
		ItemContainerContents existing = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		int fullSize = Math.max(SIZE, (int) existing.allItemsCopyStream().count());
		NonNullList<ItemStack> full = NonNullList.withSize(fullSize, ItemStack.EMPTY);
		existing.copyInto(full);
		for (int i = 0; i < SIZE; i++) {
			full.set(i, items.get(i));
		}
		shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(full));
	}
}
