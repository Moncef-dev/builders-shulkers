package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

public class ShulkerInventoryClient implements ClientModInitializer {
	// ~1s after join, enough for Fabric to finish channel negotiation before we trust canSend.
	private static final int SERVER_MOD_CHECK_DELAY_TICKS = 20;
	private static final Component SERVER_MISSING_MOD_MESSAGE = Component.literal(
			"Shulker Inventory: this server doesn't have the mod installed, so the features of the Shulker "
					+ "Inventory mod will not work on the server. Both the server and your client need the mod "
					+ "installed to enable its functionality.");
	// Ticks remaining before the deferred server-mod check; -1 means idle (no check pending).
	private int ticksUntilServerModCheck = -1;

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

		// Mirror another player's lid animation (broadcast by the server to players who can see the holder), so we
		// see the lid move on the shulker held by that other player.
		ClientPlayNetworking.registerGlobalReceiver(RemoteShulkerAnimationPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (payload.opening()) {
					ClientShulkerSession.startOpeningRemote(payload.animationId());
				} else {
					ClientShulkerSession.startClosing(payload.animationId());
				}
			});
		});

		// When joining a server that does NOT run this mod, tell the player once why opening shulker boxes from the
		// inventory will not work here, so a dead right-click does not leave them confused. The check is deferred a
		// short while after join: Fabric negotiates which custom channels the server can receive slightly AFTER the
		// play phase begins, so testing canSend at the exact join instant could wrongly report a modded server as
		// lacking the mod. Singleplayer and modded servers register our channel (canSend true, nothing shown); an
		// unmodded server never does, so the notice fires exactly once per connection.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ticksUntilServerModCheck = SERVER_MOD_CHECK_DELAY_TICKS);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ticksUntilServerModCheck = -1);

		// Advance lid animations once per client tick.
		ClientTickEvents.END_CLIENT_TICK.register(client -> ClientShulkerSession.tick());

		// Run the deferred server-mod check once its delay elapses (armed at JOIN above).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticksUntilServerModCheck < 0) return;
			if (--ticksUntilServerModCheck > 0) return;
			ticksUntilServerModCheck = -1;
			if (client.player != null && !ClientPlayNetworking.canSend(OpenShulkerPayload.TYPE)) {
				client.player.sendSystemMessage(SERVER_MISSING_MOD_MESSAGE);
			}
		});
	}
}
