package io.github.moncefdev.shulkerinventory;

import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.function.Consumer;

// Server-authoritative feature toggles, registered as vanilla game rules through Fabric's GameRuleBuilder. The 26.x
// game-rule system is namespaced (Identifier-based), so these are builders-shulkers:inventory_access and
// builders-shulkers:pocket_build (collision-free), both default true - installing the mod changes nothing until an
// admin opts out. Each rule's change event re-broadcasts the full state to clients (GameRuleStatePayload), because
// custom rules are NOT synced to the client automatically and the client needs the values to gate its own
// interception cleanly. The API is identical on 26.1.2 and 26.2, so no per-version handling is needed.
public final class ModGameRules {
	private ModGameRules() {}

	public static GameRule<Boolean> INVENTORY_ACCESS;
	public static GameRule<Boolean> POCKET_BUILD;

	// Set by register(): invoked (with the server) whenever a rule changes, to push the new state to all clients.
	private static Consumer<MinecraftServer> changeBroadcaster = server -> {};

	public static void register(Consumer<MinecraftServer> onChanged) {
		changeBroadcaster = onChanged;
		INVENTORY_ACCESS = GameRuleBuilder.forBoolean(true)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Identifier.fromNamespaceAndPath("builders-shulkers", "inventory_access"));
		POCKET_BUILD = GameRuleBuilder.forBoolean(true)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build"));
		GameRuleEvents.changeCallback(INVENTORY_ACCESS).register((value, server) -> changeBroadcaster.accept(server));
		GameRuleEvents.changeCallback(POCKET_BUILD).register((value, server) -> changeBroadcaster.accept(server));
	}

	public static boolean inventoryAccess(MinecraftServer server) {
		return server.getGameRules().get(INVENTORY_ACCESS);
	}

	public static boolean pocketBuild(MinecraftServer server) {
		return server.getGameRules().get(POCKET_BUILD);
	}
}
