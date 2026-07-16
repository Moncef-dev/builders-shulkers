package io.github.moncefdev.shulkerinventory.neoforge;

import io.github.moncefdev.shulkerinventory.ModGameRules;
import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import net.minecraft.server.MinecraftServer;

// Detects our game rules changing by comparing their values across server ticks (vanilla 26.x exposes no change
// hook; see the registration site). Keyed on the server instance, so a new server in the same JVM (singleplayer
// world switch) re-seeds instead of firing spurious "changes" against the previous world's values.
final class GameRuleChangePoller {
	private GameRuleChangePoller() {}

	private static MinecraftServer lastServer;
	private static boolean lastInventoryAccess;
	private static boolean lastPocketBuild;

	static void tick(MinecraftServer server) {
		boolean inventoryAccess = ModGameRules.inventoryAccess(server);
		boolean pocketBuild = ModGameRules.pocketBuild(server);
		if (server == lastServer) {
			if (inventoryAccess != lastInventoryAccess) {
				ShulkerInventory.inventoryAccessRuleChanged(inventoryAccess, server);
			}
			if (pocketBuild != lastPocketBuild) {
				ShulkerInventory.pocketBuildRuleChanged(pocketBuild, server);
			}
		}
		lastServer = server;
		lastInventoryAccess = inventoryAccess;
		lastPocketBuild = pocketBuild;
	}
}
