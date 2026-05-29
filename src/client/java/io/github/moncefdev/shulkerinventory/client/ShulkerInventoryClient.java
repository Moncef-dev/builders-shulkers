package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

public class ShulkerInventoryClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// When the server ends a shulker session, it asks us to restore the player's own inventory screen.
		ClientPlayNetworking.registerGlobalReceiver(OpenPlayerInventoryPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				var player = context.client().player;
				if (player != null) {
					player.containerMenu = player.inventoryMenu;
					context.client().setScreen(new InventoryScreen(player));
				}
			});
		});

		// Advance lid animations once per client tick.
		ClientTickEvents.END_CLIENT_TICK.register(client -> ClientShulkerSession.tick());
	}
}
