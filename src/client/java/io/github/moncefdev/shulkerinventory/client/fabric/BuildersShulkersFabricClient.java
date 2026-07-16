package io.github.moncefdev.shulkerinventory.client.fabric;

import io.github.moncefdev.shulkerinventory.client.BuildersShulkersKeybinds;
import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildOverlay;
import io.github.moncefdev.shulkerinventory.client.ShulkerInventoryClient;
import io.github.moncefdev.shulkerinventory.client.compat.LitematicaPickBlockCompat;
import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Fabric client entrypoint: installs the Fabric client networking implementation and the config directory, then
// wires the shared client-side logic into Fabric's receivers, connection events, tick events, use events, HUD
// element registry, and keybind API. Receivers hop to the client thread (context.client().execute), matching the
// shared handlers' threading contract.
public final class BuildersShulkersFabricClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Install the loader services FIRST, so every later step (and anything it triggers) can use them.
		ClientPlatform.setConfigDir(FabricLoader.getInstance().getConfigDir());
		ClientPlatform.setNetwork(new ClientPlatform.Network() {
			@Override
			public void send(CustomPacketPayload payload) {
				ClientPlayNetworking.send(payload);
			}

			@Override
			public boolean canSend(CustomPacketPayload.Type<?> type) {
				return ClientPlayNetworking.canSend(type);
			}
		});

		// Load the client cosmetic settings (animations, dissolve, sounds, ...) from the config directory.
		ClientConfig.load();

		ClientPlayNetworking.registerGlobalReceiver(OpenPlayerInventoryPayload.TYPE, (payload, context) ->
				context.client().execute(ShulkerInventoryClient::handleOpenPlayerInventory));
		ClientPlayNetworking.registerGlobalReceiver(RemoteShulkerAnimationPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ShulkerInventoryClient.handleRemoteAnimation(payload)));
		ClientPlayNetworking.registerGlobalReceiver(PocketBuildRemoteContentPayload.TYPE, (payload, context) ->
				context.client().execute(() -> ShulkerInventoryClient.handlePocketBuildRemoteContent(payload)));
		ClientPlayNetworking.registerGlobalReceiver(GameRuleStatePayload.TYPE, (payload, context) ->
				context.client().execute(() -> ShulkerInventoryClient.handleGameRuleState(payload)));

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ShulkerInventoryClient.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ShulkerInventoryClient.onDisconnect());

		// END-tick handlers keep the original registration order: lid animations, Pocket-Build exit checks,
		// keybind handling, then the deferred server-mod check.
		ClientTickEvents.END_CLIENT_TICK.register(ShulkerInventoryClient::tickAnimations);

		// Pocket-Build. A right-click routes through one of three use events depending on the target: a block fires
		// UseBlockCallback, air fires UseItemCallback, an entity (e.g. an item frame) fires UseEntityCallback. All
		// three feed the SAME toggle handler so the modifier + right-click always enters/exits Pocket-Build, never
		// the vanilla action; the target itself is unused there (a plain right-click is PASSed to vanilla, which
		// carries its own target).
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> PocketBuildClient.handleUse(player, world, hand));
		UseItemCallback.EVENT.register((player, world, hand) -> PocketBuildClient.handleUse(player, world, hand));
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> PocketBuildClient.handleUse(player, world, hand));

		// Peek overlay: draw the shulker contents grid right after the vanilla hotbar (shown while Peek is held).
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build_overlay"),
				PocketBuildOverlay::render);

		ClientTickEvents.START_CLIENT_TICK.register(PocketBuildClient::startClientTick);
		ClientTickEvents.END_CLIENT_TICK.register(PocketBuildClient::endClientTick);

		// Settings keybind (Options -> Controls -> Builder's Shulkers): default B, opens the settings screen in-game.
		for (KeyMapping mapping : BuildersShulkersKeybinds.createKeyMappings()) {
			KeyMappingHelper.registerKeyMapping(mapping);
		}
		ClientTickEvents.END_CLIENT_TICK.register(BuildersShulkersKeybinds::endClientTick);

		// Optional Litematica interop (Litematica is a Fabric mod, so this stays in the Fabric entrypoint):
		// middle-clicking a schematic ghost block while pocket-building selects the matching content from the held
		// shulker instead of letting Litematica swap the shulker away. Gated on Litematica being installed, so the
		// compat class (and its Litematica references) is only loaded then.
		if (FabricLoader.getInstance().isModLoaded("litematica")) {
			LitematicaPickBlockCompat.register();
		}

		ClientTickEvents.END_CLIENT_TICK.register(ShulkerInventoryClient::tickServerModCheck);
	}
}
