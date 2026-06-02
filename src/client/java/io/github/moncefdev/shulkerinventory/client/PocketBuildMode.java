package io.github.moncefdev.shulkerinventory.client;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

// Client-side state for the Pocket-Build prototype: whether the mode is active, which hotbar slot holds the source
// shulker, which content slot is selected, and the animation id allocated for this session. The lid animation runs
// through the shared marker machinery (the server tags the held shulker, the client drives the animation by that
// id), so this class no longer computes openness itself.
public final class PocketBuildMode {
	private PocketBuildMode() {}

	public static final int CONTAINER_SIZE = 27;

	private static boolean active = false;
	private static int sourceHotbarSlot = -1;
	// Index into the shulker's content slots, or -1 when the shulker has nothing to select.
	private static int selectedContentSlot = -1;
	private static long animationId = 0L;

	public static boolean isActive() {
		return active;
	}

	public static int sourceHotbarSlot() {
		return sourceHotbarSlot;
	}

	public static int selectedContentSlot() {
		return selectedContentSlot;
	}

	public static long animationId() {
		return animationId;
	}

	public static void enter(int hotbarSlot, ItemStack shulker, long id) {
		active = true;
		sourceHotbarSlot = hotbarSlot;
		selectedContentSlot = firstNonEmptySlot(readContents(shulker));
		animationId = id;
	}

	public static void exit() {
		active = false;
		sourceHotbarSlot = -1;
		selectedContentSlot = -1;
		animationId = 0L;
	}

	// Advance the selection to the next (direction +1) or previous (direction -1) NON-EMPTY content slot, wrapping
	// around. With no non-empty slot the selection clears. Empty slots are skipped so a blind scroll never lands on
	// nothing during a build.
	public static void cycle(int direction, ItemStack shulker) {
		NonNullList<ItemStack> items = readContents(shulker);
		int[] nonEmpty = new int[CONTAINER_SIZE];
		int count = 0;
		for (int i = 0; i < CONTAINER_SIZE; i++) {
			if (!items.get(i).isEmpty()) {
				nonEmpty[count++] = i;
			}
		}
		if (count == 0) {
			selectedContentSlot = -1;
			return;
		}
		int pos = -1;
		for (int i = 0; i < count; i++) {
			if (nonEmpty[i] == selectedContentSlot) {
				pos = i;
				break;
			}
		}
		pos = pos < 0 ? 0 : Math.floorMod(pos + direction, count);
		selectedContentSlot = nonEmpty[pos];
	}

	// The content stack currently selected, or EMPTY if none.
	public static ItemStack selectedStack(ItemStack shulker) {
		if (selectedContentSlot < 0 || selectedContentSlot >= CONTAINER_SIZE) {
			return ItemStack.EMPTY;
		}
		return readContents(shulker).get(selectedContentSlot);
	}

	private static NonNullList<ItemStack> readContents(ItemStack shulker) {
		ItemContainerContents contents = shulker.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);
		contents.copyInto(items);
		return items;
	}

	private static int firstNonEmptySlot(NonNullList<ItemStack> items) {
		for (int i = 0; i < CONTAINER_SIZE; i++) {
			if (!items.get(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}
}
