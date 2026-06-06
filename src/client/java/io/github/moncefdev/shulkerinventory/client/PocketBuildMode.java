package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.ShulkerContents;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

// Client-side state for Pocket-Build: whether the mode is active, which hotbar slot holds the source
// shulker, which content slot is selected, and the animation id allocated for this session. The lid animation runs
// through the shared marker machinery (the server tags the held shulker, the client drives the animation by that
// id), so this class no longer computes openness itself.
public final class PocketBuildMode {
	private PocketBuildMode() {}

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

	// Move the selection to the FIRST stack of the next (scroll forward, direction >= 0) or previous (direction < 0)
	// DIFFERENT item, always skipping empty slots. Consecutive non-empty stacks of the same item (same item AND
	// components, like the middle-click pick) form one run, so a scroll jumps a whole run of duplicates in a single
	// step and lands on the START of the next/previous run - a shorter, more memorable palette for blind building.
	// Backward deliberately lands on the FIRST stack of the previous run, not the last one reached, so forward then
	// backward returns where you were. Empty slots never split a run; identical items separated by a different item
	// are distinct runs. With a single distinct item the selection snaps to its first stack; with none it clears.
	// After a placement empties the selected slot, the selection sits "before" the rebuilt run, so the next forward
	// picks the nearest remaining stack and backward wraps to the last run.
	public static void cycle(int direction, ItemStack shulker) {
		NonNullList<ItemStack> items = readContents(shulker);
		int size = ShulkerContents.SIZE;
		// First slot of each run of consecutive same-item stacks (empties ignored, so they do not split a run).
		int[] runFirst = new int[size];
		int runCount = 0;
		int lastNonEmpty = -1;
		for (int i = 0; i < size; i++) {
			ItemStack s = items.get(i);
			if (s.isEmpty()) {
				continue;
			}
			if (lastNonEmpty < 0 || !ItemStack.isSameItemSameComponents(s, items.get(lastNonEmpty))) {
				runFirst[runCount++] = i;
			}
			lastNonEmpty = i;
		}
		if (runCount == 0) {
			selectedContentSlot = -1;
			return;
		}
		if (runCount == 1) {
			selectedContentSlot = runFirst[0];
			return;
		}
		// The run the selection currently sits in: the last run starting at or before the selected slot, or none
		// (-1) when the selection is before the first run (no selection, or a just-emptied slot).
		int curRun = -1;
		for (int k = 0; k < runCount; k++) {
			if (runFirst[k] <= selectedContentSlot) {
				curRun = k;
			} else {
				break;
			}
		}
		int target;
		if (direction >= 0) {
			target = Math.floorMod(curRun + 1, runCount); // curRun == -1 -> first run
		} else {
			target = curRun <= 0 ? runCount - 1 : curRun - 1; // before-first or first run -> wrap to last
		}
		selectedContentSlot = runFirst[target];
	}

	// A snapshot of the held shulker's content stacks (for the peek overlay rendering).
	public static NonNullList<ItemStack> snapshotContents(ItemStack shulker) {
		return readContents(shulker);
	}

	// The content stack currently selected, or EMPTY if none.
	public static ItemStack selectedStack(ItemStack shulker) {
		if (selectedContentSlot < 0 || selectedContentSlot >= ShulkerContents.SIZE) {
			return ItemStack.EMPTY;
		}
		return readContents(shulker).get(selectedContentSlot);
	}

	// Select the FIRST content slot holding an item that matches `pick` (same item + components, exactly the criterion
	// and first-match order vanilla's Inventory.findSlotMatchingItem uses for the inventory pick-block), and return true;
	// or leave the selection unchanged and return false if none matches. Used by the Pocket-Build pick-block, so
	// middle-clicking a block you already have in the box selects it instead of leaving the mode.
	public static boolean selectMatchingSlot(ItemStack shulker, ItemStack pick) {
		if (pick == null || pick.isEmpty()) {
			return false;
		}
		NonNullList<ItemStack> items = readContents(shulker);
		for (int i = 0; i < ShulkerContents.SIZE; i++) {
			if (ItemStack.isSameItemSameComponents(items.get(i), pick)) {
				selectedContentSlot = i;
				return true;
			}
		}
		return false;
	}

	private static NonNullList<ItemStack> readContents(ItemStack shulker) {
		return ShulkerContents.read(shulker);
	}

	private static int firstNonEmptySlot(NonNullList<ItemStack> items) {
		for (int i = 0; i < ShulkerContents.SIZE; i++) {
			if (!items.get(i).isEmpty()) {
				return i;
			}
		}
		return -1;
	}
}
