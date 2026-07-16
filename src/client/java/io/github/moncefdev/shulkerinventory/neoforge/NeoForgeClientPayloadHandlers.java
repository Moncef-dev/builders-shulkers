package io.github.moncefdev.shulkerinventory.neoforge;

import io.github.moncefdev.shulkerinventory.client.ShulkerInventoryClient;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;

// Client-only bridge for the clientbound payload handlers. The registration site (BuildersShulkersNeoForge) only
// references this class from inside lambda bodies, so it is never classloaded on a dedicated server; here it is
// safe to touch the client-only shared handlers. All calls arrive on the client thread (enqueueWork upstream).
final class NeoForgeClientPayloadHandlers {
	private NeoForgeClientPayloadHandlers() {}

	static void handleOpenPlayerInventory(OpenPlayerInventoryPayload payload) {
		ShulkerInventoryClient.handleOpenPlayerInventory();
	}

	static void handleRemoteAnimation(RemoteShulkerAnimationPayload payload) {
		ShulkerInventoryClient.handleRemoteAnimation(payload);
	}

	static void handlePocketBuildRemoteContent(PocketBuildRemoteContentPayload payload) {
		ShulkerInventoryClient.handlePocketBuildRemoteContent(payload);
	}

	static void handleGameRuleState(GameRuleStatePayload payload) {
		ShulkerInventoryClient.handleGameRuleState(payload);
	}
}
