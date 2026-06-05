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

	// Advance the selection to the next (direction +1) or previous (direction -1) NON-EMPTY content slot, wrapping
	// around. With no non-empty slot the selection clears. Empty slots are skipped so a blind scroll never lands on
	// nothing during a build.
	public static void cycle(int direction, ItemStack shulker) {
		NonNullList<ItemStack> items = readContents(shulker);
		int[] nonEmpty = new int[ShulkerContents.SIZE];
		int count = 0;
		for (int i = 0; i < ShulkerContents.SIZE; i++) {
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
		if (pos >= 0) {
			// Still on a non-empty slot: step to the next (+1) or previous (-1) non-empty slot, wrapping.
			selectedContentSlot = nonEmpty[Math.floorMod(pos + direction, count)];
			return;
		}
		// The previously selected slot was EMPTIED (it is no longer in nonEmpty). Step to the non-empty slot that is the
		// next (direction >= 0) or previous (direction < 0) BY SLOT INDEX, relative to where the selection was, wrapping -
		// instead of snapping back to the first non-empty (what happened when pos fell through to 0).
		if (direction >= 0) {
			int chosen = nonEmpty[0];
			for (int i = 0; i < count; i++) {
				if (nonEmpty[i] > selectedContentSlot) {
					chosen = nonEmpty[i];
					break;
				}
			}
			selectedContentSlot = chosen;
		} else {
			int chosen = nonEmpty[count - 1];
			for (int i = count - 1; i >= 0; i--) {
				if (nonEmpty[i] < selectedContentSlot) {
					chosen = nonEmpty[i];
					break;
				}
			}
			selectedContentSlot = chosen;
		}
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
