package io.github.moncefdev.shulkerinventory.fabric;

import io.github.moncefdev.shulkerinventory.ModGameRules;
import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import io.github.moncefdev.shulkerinventory.network.GameRuleStatePayload;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.OpenShulkerPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildModePayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import io.github.moncefdev.shulkerinventory.platform.Platform;
import io.github.moncefdev.shulkerinventory.platform.PlatformNetwork;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.Collection;

// Fabric entrypoint: installs the Fabric networking implementation, then wires the shared server-side logic
// (ShulkerInventory) into Fabric's payload registry, receivers, connection events, and game-rule API. Fabric's play
// payload receivers already run on the server thread, matching the shared handlers' threading contract.
public final class BuildersShulkersFabric implements ModInitializer {
	@Override
	public void onInitialize() {
		// Install the loader services FIRST, so every later step (and anything it triggers) can use them.
		Platform.setNetwork(new PlatformNetwork() {
			@Override
			public void send(ServerPlayer player, CustomPacketPayload payload) {
				ServerPlayNetworking.send(player, payload);
			}

			@Override
			public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
				return ServerPlayNetworking.canSend(player, type);
			}

			@Override
			public Collection<ServerPlayer> tracking(ServerPlayer player) {
				return PlayerLookup.tracking(player);
			}
		});

		PayloadTypeRegistry.serverboundPlay().register(OpenShulkerPayload.TYPE, OpenShulkerPayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketBuildModePayload.TYPE, PocketBuildModePayload.STREAM_CODEC);
		PayloadTypeRegistry.serverboundPlay().register(PocketBuildSelectPayload.TYPE, PocketBuildSelectPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenPlayerInventoryPayload.TYPE, OpenPlayerInventoryPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RemoteShulkerAnimationPayload.TYPE, RemoteShulkerAnimationPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(PocketBuildRemoteContentPayload.TYPE, PocketBuildRemoteContentPayload.STREAM_CODEC);
		PayloadTypeRegistry.clientboundPlay().register(GameRuleStatePayload.TYPE, GameRuleStatePayload.STREAM_CODEC);

		// Feature toggles exposed as vanilla game rules (default true), registered through Fabric's builder. Each
		// change re-broadcasts the state to clients and enforces the OFF transition (see the shared handlers).
		ModGameRules.INVENTORY_ACCESS = GameRuleBuilder.forBoolean(true)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(ModGameRules.INVENTORY_ACCESS_ID);
		ModGameRules.POCKET_BUILD = GameRuleBuilder.forBoolean(true)
				.category(GameRuleCategory.MISC)
				.buildAndRegister(ModGameRules.POCKET_BUILD_ID);
		GameRuleEvents.changeCallback(ModGameRules.INVENTORY_ACCESS)
				.register((value, server) -> ShulkerInventory.inventoryAccessRuleChanged(value, server));
		GameRuleEvents.changeCallback(ModGameRules.POCKET_BUILD)
				.register((value, server) -> ShulkerInventory.pocketBuildRuleChanged(value, server));

		ServerPlayNetworking.registerGlobalReceiver(OpenShulkerPayload.TYPE,
				(payload, context) -> ShulkerInventory.handleOpenShulker(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(PocketBuildModePayload.TYPE,
				(payload, context) -> ShulkerInventory.handlePocketBuildMode(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(PocketBuildSelectPayload.TYPE,
				(payload, context) -> ShulkerInventory.handlePocketBuildSelect(payload, context.player()));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
				ShulkerInventory.onPlayerJoin(handler.player, server));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				ShulkerInventory.onPlayerDisconnect(handler.player));

		ShulkerInventory.LOGGER.info("Builder's Shulkers initialized");
	}
}
