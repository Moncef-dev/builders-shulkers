package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

// Shared client-side logic. Each loader's client entrypoint wires these handlers into its own networking and event
// plumbing (payload receivers, connection events, tick events); everything here is loader-agnostic. Every handler
// expects to be called on the CLIENT thread.
public final class ShulkerInventoryClient {
	private ShulkerInventoryClient() {}

	// ~1s after join, enough for the loader to finish channel negotiation before we trust canSend.
	private static final int SERVER_MOD_CHECK_DELAY_TICKS = 20;
	private static final Component SERVER_MISSING_MOD_MESSAGE = Component.literal(
			"Builder's Shulkers: this server doesn't have the mod installed, so the features of the Builder's "
					+ "Shulkers mod will not work on the server. Both the server and your client need the mod "
					+ "installed to enable its functionality.");
	// Ticks remaining before the deferred server-mod check; -1 means idle (no check pending).
	private static int ticksUntilServerModCheck = -1;

	// When the server ends a shulker session, it asks us to restore the player's own inventory screen.
	// The local MUST be named mc: the Stonecutter replacement that retargets setScreen on older versions
	// matches the literal "mc.setScreen(".
	public static void handleOpenPlayerInventory() {
		Minecraft mc = Minecraft.getInstance();
		var player = mc.player;
		if (player != null) {
			player.containerMenu = player.inventoryMenu;
			mc.setScreen(new InventoryScreen(player));
		}
	}

	// Mirror another player's lid animation AND sound (broadcast by the server only to players who can see the
	// holder, and only for a HELD shulker), so we see the lid move and hear it on the shulker held by that
	// other player. The sound plays at the holder's position to match the visible animation.
	public static void handleRemoteAnimation(RemoteShulkerAnimationPayload payload) {
		Minecraft client = Minecraft.getInstance();
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
			playRemoteSound(client, payload.holderEntityId(), payload.opening());
		}
	}

	// Mirror another player's Pocket-Build content: draw their selected block inside the held box and dissolve its
	// lid, matching what the holder sees, instead of only the plain lid animation. Broadcast by the server on enter
	// and on every scroll. markPocketBuild / setPocketBuildContent are no-ops until the mirror animation exists, but
	// the server sends the open broadcast (which creates it) before this, so by now it does.
	public static void handlePocketBuildRemoteContent(PocketBuildRemoteContentPayload payload) {
		ClientShulkerSession.markPocketBuild(payload.animationId());
		ClientShulkerSession.setPocketBuildContent(payload.animationId(), payload.content());
	}

	// Cache the server's mod game-rule state (custom rules are not synced automatically), so the client can gate its
	// own interception in step with the server (see ClientGameRuleState). Sent on join and on every change.
	public static void handleGameRuleState(GameRuleStatePayload payload) {
		ClientGameRuleState.set(payload.inventoryAccess(), payload.pocketBuild());
		// If Pocket-Build was just disabled server-side, leave the mode locally (the held shulker has no menu
		// for the server to close; the open-inventory case is handled by the server closing the menu).
		if (!payload.pocketBuild() && PocketBuildMode.isActive()) {
			PocketBuildClient.exitMode();
		}
	}

	// Arm the deferred server-mod check (see tickServerModCheck). Deferred a short while after join: the loader
	// negotiates which custom channels the server can receive slightly AFTER the play phase begins, so testing
	// canSend at the exact join instant could wrongly report a modded server as lacking the mod.
	public static void onJoin() {
		ticksUntilServerModCheck = SERVER_MOD_CHECK_DELAY_TICKS;
	}

	public static void onDisconnect() {
		ticksUntilServerModCheck = -1;
		// Drop all animation state, so orphaned animations and their per-id box models never carry across sessions.
		ClientShulkerSession.clearAll();
		// Reset cached game-rule state, so a value from one server never leaks into the next session.
		ClientGameRuleState.reset();
	}

	// Advance lid animations once per client tick, but NOT while the game is paused (singleplayer pause menu): the
	// world is frozen there, so the lids must freeze too for consistency. isPaused() is false in multiplayer, where
	// the world keeps running and so should the animations.
	public static void tickAnimations(Minecraft client) {
		if (!client.isPaused()) {
			ClientShulkerSession.tick();
		}
	}

	// When joining a server that does NOT run this mod, tell the player once why opening shulker boxes from the
	// inventory will not work here, so a dead right-click does not leave them confused. Singleplayer and modded
	// servers register our channel (canSend true, nothing shown); an unmodded server never does, so the notice
	// fires exactly once per connection.
	public static void tickServerModCheck(Minecraft client) {
		if (ticksUntilServerModCheck < 0) return;
		if (--ticksUntilServerModCheck > 0) return;
		ticksUntilServerModCheck = -1;
		if (client.player != null && !ClientPlatform.network().canSend(OpenShulkerPayload.TYPE)) {
			client.player.displayClientMessage(SERVER_MISSING_MOD_MESSAGE, false);
		}
	}

	private static void playRemoteSound(Minecraft client, int holderEntityId, boolean opening) {
		if (client.level == null) return;
		Entity holder = client.level.getEntity(holderEntityId);
		if (holder == null) return;
		ClientShulkerSession.playShulkerSound(client.level, holder, opening);
	}
}
