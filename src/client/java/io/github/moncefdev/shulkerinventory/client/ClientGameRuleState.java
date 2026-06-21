package io.github.moncefdev.shulkerinventory.client;

// Client-side cache of the server's mod game-rule state, synced via GameRuleStatePayload (custom game rules are not
// synced automatically). Defaults match the server defaults (true): before the first sync, or on a server without the
// mod, the features read as enabled - and on an unmodded server the canSend gates already prevent acting anyway.
public final class ClientGameRuleState {
	private ClientGameRuleState() {}

	private static boolean inventoryAccess = true;
	private static boolean pocketBuild = true;

	public static void set(boolean inventoryAccessValue, boolean pocketBuildValue) {
		inventoryAccess = inventoryAccessValue;
		pocketBuild = pocketBuildValue;
	}

	// Restore defaults on disconnect, so a stale value from one server never leaks into the next session.
	public static void reset() {
		inventoryAccess = true;
		pocketBuild = true;
	}

	public static boolean inventoryAccess() {
		return inventoryAccess;
	}

	public static boolean pocketBuild() {
		return pocketBuild;
	}
}
