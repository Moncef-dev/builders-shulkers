package io.github.moncefdev.shulkerinventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Server-side Pocket-Build state: for each player currently in the mode, which content slot of their held shulker is
// selected. The use-path mixin reads this to briefly swap the selected content into the player's hand so the vanilla
// place/interact flow runs on the content (the shulker is never the item used). Cleared on exit and on disconnect.
public final class PocketBuildServerState {
	private PocketBuildServerState() {}

	// Present == in Pocket-Build mode; value == selected content slot (0..26).
	private static final Map<UUID, Integer> SELECTED = new ConcurrentHashMap<>();

	public static void enter(UUID player, int contentSlot) {
		SELECTED.put(player, contentSlot);
	}

	public static void select(UUID player, int contentSlot) {
		// Only update if already in the mode (an out-of-order select must not silently start the mode).
		SELECTED.computeIfPresent(player, (k, v) -> contentSlot);
	}

	public static void exit(UUID player) {
		SELECTED.remove(player);
	}

	// Whether the player is currently in Pocket-Build mode. Distinct from selectedSlot() >= 0: a player can be in the
	// mode with NO content selected (an empty shulker yields slot -1), and in that case a use must place NOTHING, never
	// fall through to placing the shulker itself.
	public static boolean isActive(UUID player) {
		return SELECTED.containsKey(player);
	}

	// The selected content slot, or -1 if the player is not in the mode OR is in the mode with nothing to place.
	public static int selectedSlot(UUID player) {
		Integer slot = SELECTED.get(player);
		return slot == null ? -1 : slot;
	}
}
