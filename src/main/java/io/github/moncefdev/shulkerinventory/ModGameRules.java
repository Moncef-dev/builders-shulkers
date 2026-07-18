package io.github.moncefdev.shulkerinventory;

import net.minecraft.server.MinecraftServer;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
//?} else {
/*import net.minecraft.world.level.GameRules;
*///?}

// Server-authoritative feature toggles, registered as vanilla game rules, both default true: installing the mod
// changes nothing until an admin opts out. The registration API is loader-specific (and, on this branch, also
// version-forked: 1.21.11 uses the namespaced game-rule system like the 26.x line, 1.21.10 the older static one),
// so each loader's entrypoint registers the rules (assigning the fields below) and wires their change events to
// ShulkerInventory's rule-changed handlers, which re-broadcast the full state to clients (custom rules are NOT
// synced automatically and the client needs the values to gate its own interception cleanly). The rule NAMES
// differ per version as a result (namespaced builders-shulkers:inventory_access on 1.21.11, camelCase
// buildersShulkersInventoryAccess on 1.21.10), which only shows in the /gamerule command.
public final class ModGameRules {
	private ModGameRules() {}

	//? if >=1.21.11 {
	public static final Identifier INVENTORY_ACCESS_ID = Identifier.fromNamespaceAndPath("builders-shulkers", "inventory_access");
	public static final Identifier POCKET_BUILD_ID = Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build");

	// Assigned by the loader entrypoint at registration, before anything can read them.
	public static GameRule<Boolean> INVENTORY_ACCESS;
	public static GameRule<Boolean> POCKET_BUILD;
	//?} else {
	/*public static final String INVENTORY_ACCESS_NAME = "buildersShulkersInventoryAccess";
	public static final String POCKET_BUILD_NAME = "buildersShulkersPocketBuild";

	// Assigned by the loader entrypoint at registration, before anything can read them.
	public static GameRules.Key<GameRules.BooleanValue> INVENTORY_ACCESS;
	public static GameRules.Key<GameRules.BooleanValue> POCKET_BUILD;
	*///?}

	public static boolean inventoryAccess(MinecraftServer server) {
		//? if >=1.21.11 {
		return server.overworld().getGameRules().get(INVENTORY_ACCESS);
		//?} else {
		/*return server.overworld().getGameRules().getBoolean(INVENTORY_ACCESS);
		*///?}
	}

	public static boolean pocketBuild(MinecraftServer server) {
		//? if >=1.21.11 {
		return server.overworld().getGameRules().get(POCKET_BUILD);
		//?} else {
		/*return server.overworld().getGameRules().getBoolean(POCKET_BUILD);
		*///?}
	}
}
