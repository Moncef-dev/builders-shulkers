package io.github.moncefdev.shulkerinventory;

import net.minecraft.server.MinecraftServer;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

//? if >=1.21.11 {
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
//?} else {
/*import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
*///?}

// Server-authoritative feature toggles, exposed as vanilla game rules, both default true: installing the mod changes
// nothing until an admin opts out. Each rule's change pushes the full state to clients (custom rules are not synced
// automatically, and the client needs the values to gate its own interception).
//
// The game-rule API itself differs across the 1.21.x line, so the registration is the one inline-forked spot in the
// codebase: 1.21.11 (like the 26.x line) namespaces rules through GameRuleBuilder + an Identifier key, with change
// listeners registered separately via GameRuleEvents; 1.21.10 still uses the older Fabric API (GameRuleRegistry +
// GameRuleFactory, a plain string name, the change listener baked into the rule factory). Callers stay API-agnostic:
// they hand register() an "any change" broadcaster and a per-rule on-change handler, and this class wires them the way
// the active version expects. The rule NAMES differ as a result (namespaced builders-shulkers:inventory_access on
// 1.21.11, camelCase buildersShulkersInventoryAccess on 1.21.10), which only shows in the /gamerule command.
public final class ModGameRules {
	private ModGameRules() {}

	//? if >=1.21.11 {
	public static GameRule<Boolean> INVENTORY_ACCESS;
	public static GameRule<Boolean> POCKET_BUILD;
	//?} else {
	/*public static GameRules.Key<GameRules.BooleanValue> INVENTORY_ACCESS;
	public static GameRules.Key<GameRules.BooleanValue> POCKET_BUILD;
	*///?}

	// Registers both rules. onAnyChange fires on either rule's change (used to re-broadcast the state); the per-rule
	// handlers receive (newValue, server) when their own rule changes (used to force clients out of a disabled feature).
	public static void register(Consumer<MinecraftServer> onAnyChange,
			BiConsumer<Boolean, MinecraftServer> onInventoryAccessChange,
			BiConsumer<Boolean, MinecraftServer> onPocketBuildChange) {
		BiConsumer<Boolean, MinecraftServer> onInv = (value, server) -> {
			onAnyChange.accept(server);
			onInventoryAccessChange.accept(value, server);
		};
		BiConsumer<Boolean, MinecraftServer> onPb = (value, server) -> {
			onAnyChange.accept(server);
			onPocketBuildChange.accept(value, server);
		};
		//? if >=1.21.11 {
		INVENTORY_ACCESS = GameRuleBuilder.forBoolean(true)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Identifier.fromNamespaceAndPath("builders-shulkers", "inventory_access"));
		POCKET_BUILD = GameRuleBuilder.forBoolean(true)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build"));
		GameRuleEvents.changeCallback(INVENTORY_ACCESS).register((value, server) -> onInv.accept(value, server));
		GameRuleEvents.changeCallback(POCKET_BUILD).register((value, server) -> onPb.accept(value, server));
		//?} else {
		/*INVENTORY_ACCESS = GameRuleRegistry.register("buildersShulkersInventoryAccess", GameRules.Category.MISC,
				GameRuleFactory.createBooleanRule(true, (server, rule) -> onInv.accept(rule.get(), server)));
		POCKET_BUILD = GameRuleRegistry.register("buildersShulkersPocketBuild", GameRules.Category.MISC,
				GameRuleFactory.createBooleanRule(true, (server, rule) -> onPb.accept(rule.get(), server)));
		*///?}
	}

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
