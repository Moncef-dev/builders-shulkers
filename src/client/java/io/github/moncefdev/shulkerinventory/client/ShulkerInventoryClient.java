package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public class ShulkerInventoryClient implements ClientModInitializer {
	// ~1s after join, enough for Fabric to finish channel negotiation before we trust canSend.
	private static final int SERVER_MOD_CHECK_DELAY_TICKS = 20;
	private static final Component SERVER_MISSING_MOD_MESSAGE = Component.literal(
			"Builder's Shulkers: this server doesn't have the mod installed, so the features of the Builder's "
					+ "Shulkers mod will not work on the server. Both the server and your client need the mod "
					+ "installed to enable its functionality.");
	// Ticks remaining before the deferred server-mod check; -1 means idle (no check pending).
	private int ticksUntilServerModCheck = -1;

	@Override
	public void onInitializeClient() {
		// Load the client cosmetic settings (animations, dissolve, sounds, ...) from the config directory.
		ClientConfig.load();

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

		// Mirror another player's lid animation AND sound (broadcast by the server only to players who can see the
		// holder, and only for a HELD shulker), so we see the lid move and hear it on the shulker held by that
		// other player. The sound plays at the holder's position to match the visible animation.
		ClientPlayNetworking.registerGlobalReceiver(RemoteShulkerAnimationPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				if (payload.opening()) {
					if (ClientConfig.get().otherPlayerAnimations) {
						ClientShulkerSession.startOpeningRemote(payload.animationId(), payload.holderEntityId());
					}
				} else {
					ClientShulkerSession.startClosing(payload.animationId());
				}
				// Drain the animation either way, but only play the sound when the broadcast asked for it (a close
				// caused by disturbing the source shulker is silent: the box just vanished for this viewer).
				if (payload.playSound() && ClientConfig.get().otherPlayerAnimations) {
					playRemoteSound(context.client(), payload.holderEntityId(), payload.opening());
				}
			});
		});

		// Mirror another player's Pocket-Build content: draw their selected block inside the held box and dissolve its
		// lid, matching what the holder sees, instead of only the plain lid animation. Broadcast by the server on enter
		// and on every scroll. markPocketBuild / setPocketBuildContent are no-ops until the mirror animation exists, but
		// the server sends the open broadcast (which creates it) before this, so by now it does.
		ClientPlayNetworking.registerGlobalReceiver(PocketBuildRemoteContentPayload.TYPE, (payload, context) -> {
			context.client().execute(() -> {
				ClientShulkerSession.markPocketBuild(payload.animationId());
				ClientShulkerSession.setPocketBuildContent(payload.animationId(), payload.content());
			});
		});

		// Cache the server's mod game-rule state (custom rules are not synced automatically), so the client can gate its
		// own interception in step with the server (see ClientGameRuleState). Sent on join and on every change.
		ClientPlayNetworking.registerGlobalReceiver(GameRuleStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> {
					ClientGameRuleState.set(payload.inventoryAccess(), payload.pocketBuild());
					// If Pocket-Build was just disabled server-side, leave the mode locally (the held shulker has no menu
					// for the server to close; the open-inventory case is handled by the server closing the menu).
					if (!payload.pocketBuild() && PocketBuildMode.isActive()) {
						PocketBuildClient.exitMode();
					}
				}));

		// When joining a server that does NOT run this mod, tell the player once why opening shulker boxes from the
		// inventory will not work here, so a dead right-click does not leave them confused. The check is deferred a
		// short while after join: Fabric negotiates which custom channels the server can receive slightly AFTER the
		// play phase begins, so testing canSend at the exact join instant could wrongly report a modded server as
		// lacking the mod. Singleplayer and modded servers register our channel (canSend true, nothing shown); an
		// unmodded server never does, so the notice fires exactly once per connection.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ticksUntilServerModCheck = SERVER_MOD_CHECK_DELAY_TICKS);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			ticksUntilServerModCheck = -1;
			// Drop all animation state, so orphaned animations and their per-id box models never carry across sessions.
			ClientShulkerSession.clearAll();
			// Reset cached game-rule state, so a value from one server never leaks into the next session.
			ClientGameRuleState.reset();
		});

		// Advance lid animations once per client tick, but NOT while the game is paused (singleplayer pause menu): the
		// world is frozen there, so the lids must freeze too for consistency. isPaused() is false in multiplayer, where
		// the world keeps running and so should the animations.
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!client.isPaused()) {
				ClientShulkerSession.tick();
			}
		});

		// Pocket-Build: Ctrl + right-click a held shulker to enter/exit the block-placement mode.
		PocketBuildClient.register();

		// Settings keybind (Options -> Controls -> Builder's Shulkers): default B, opens the settings screen in-game.
		BuildersShulkersKeybinds.register();

		// Run the deferred server-mod check once its delay elapses (armed at JOIN above).
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (ticksUntilServerModCheck < 0) return;
			if (--ticksUntilServerModCheck > 0) return;
			ticksUntilServerModCheck = -1;
			if (client.player != null && !ClientPlayNetworking.canSend(OpenShulkerPayload.TYPE)) {
				client.player.displayClientMessage(SERVER_MISSING_MOD_MESSAGE, false);
			}
		});
	}

	private static void playRemoteSound(Minecraft client, int holderEntityId, boolean opening) {
		if (client.level == null) return;
		Entity holder = client.level.getEntity(holderEntityId);
		if (holder == null) return;
		ClientShulkerSession.playShulkerSound(client.level, holder, opening);
	}
}
