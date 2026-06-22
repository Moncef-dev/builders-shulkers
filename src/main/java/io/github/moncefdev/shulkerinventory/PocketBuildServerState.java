package io.github.moncefdev.shulkerinventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Server-side Pocket-Build state: for each player currently in the mode, which content slot of their held shulker is
// selected AND the animation marker id of the box the mode entered with. The use-path mixins read the slot to briefly
// swap the selected content into the player's hand so the vanilla place/interact flow runs on the content (the shulker
// is never the item used), and read the marker id to verify the held shulker is STILL that box: an in-place item swap
// (Item Swapper's R menu, a creative set-slot) replaces the held stack without changing the selected slot, so the
// marker discriminates the original box from a swapped-in one. Cleared on exit and on disconnect.
public final class PocketBuildServerState {
	private PocketBuildServerState() {}

	// selected content slot (0..26, or -1 when nothing is selectable) + the animation marker id of the entered box.
	private record Session(int contentSlot, long animationId) {}

	// Present == in Pocket-Build mode.
	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	public static void enter(UUID player, int contentSlot, long animationId) {
		SESSIONS.put(player, new Session(contentSlot, animationId));
	}

	public static void select(UUID player, int contentSlot) {
		// Only update if already in the mode (an out-of-order select must not silently start the mode). The entered
		// box's marker id is preserved.
		SESSIONS.computeIfPresent(player, (k, v) -> new Session(contentSlot, v.animationId()));
	}

	public static void exit(UUID player) {
		SESSIONS.remove(player);
	}

	// Whether the player is currently in Pocket-Build mode. Distinct from selectedSlot() >= 0: a player can be in the
	// mode with NO content selected (an empty shulker yields slot -1), and in that case a use must place NOTHING, never
	// fall through to placing the shulker itself.
	public static boolean isActive(UUID player) {
		return SESSIONS.containsKey(player);
	}

	// The selected content slot, or -1 if the player is not in the mode OR is in the mode with nothing to place.
	public static int selectedSlot(UUID player) {
		Session s = SESSIONS.get(player);
		return s == null ? -1 : s.contentSlot();
	}

	// Whether the player's held shulker is still the exact box the mode entered with: its animation marker (passed in,
	// read from the held stack by the caller) matches the id stored at entry. Used fail-closed by the content-swap
	// path, so a use after an in-place swap places NOTHING from the wrong box. The server sets the marker synchronously
	// on entry, so there is no sync delay here (unlike the client). Returns false when not in the mode.
	public static boolean matchesModeBox(UUID player, Long heldMarker) {
		Session s = SESSIONS.get(player);
		return s != null && heldMarker != null && heldMarker == s.animationId();
	}
}
