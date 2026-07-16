package io.github.moncefdev.shulkerinventory;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;

// Server-authoritative feature toggles, registered as vanilla game rules. The 26.x game-rule system is namespaced
// (Identifier-based), so these are builders-shulkers:inventory_access and builders-shulkers:pocket_build
// (collision-free), both default true - installing the mod changes nothing until an admin opts out. The rule
// INSTANCES are vanilla GameRule objects, but the registration API is loader-specific, so each loader's entrypoint
// registers them (assigning the fields below) and wires their change events to ShulkerInventory's rule-changed
// handlers, which re-broadcast the full state to clients (custom rules are NOT synced automatically and the client
// needs the values to gate its own interception cleanly).
public final class ModGameRules {
	private ModGameRules() {}

	public static final Identifier INVENTORY_ACCESS_ID = Identifier.fromNamespaceAndPath("builders-shulkers", "inventory_access");
	public static final Identifier POCKET_BUILD_ID = Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build");

	// Assigned by the loader entrypoint at registration, before anything can read them.
	public static GameRule<Boolean> INVENTORY_ACCESS;
	public static GameRule<Boolean> POCKET_BUILD;

	public static boolean inventoryAccess(MinecraftServer server) {
		return server.getGameRules().get(INVENTORY_ACCESS);
	}

	public static boolean pocketBuild(MinecraftServer server) {
		return server.getGameRules().get(POCKET_BUILD);
	}
}
