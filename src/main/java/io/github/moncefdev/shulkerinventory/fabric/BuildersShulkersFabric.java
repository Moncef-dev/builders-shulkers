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
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.21.11 {
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleEvents;
import net.minecraft.world.level.gamerules.GameRuleCategory;
//?} else {
/*import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.minecraft.world.level.GameRules;
*///?}

import java.util.Collection;

// Fabric entrypoint: installs the Fabric networking implementation, then wires the shared server-side logic
// (ShulkerInventory) into Fabric's payload registry, receivers, connection events, and game-rule API. Fabric's play
// payload receivers already run on the server thread, matching the shared handlers' threading contract. The game-rule
// registration is version-forked on this branch: 1.21.11 uses the namespaced builder + separate change events (like
// the 26.x line), 1.21.10 the older Fabric API with the change listener baked into the rule factory.
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

		PayloadTypeRegistry.playC2S().register(OpenShulkerPayload.TYPE, OpenShulkerPayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(PocketBuildModePayload.TYPE, PocketBuildModePayload.STREAM_CODEC);
		PayloadTypeRegistry.playC2S().register(PocketBuildSelectPayload.TYPE, PocketBuildSelectPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(OpenPlayerInventoryPayload.TYPE, OpenPlayerInventoryPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(RemoteShulkerAnimationPayload.TYPE, RemoteShulkerAnimationPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(PocketBuildRemoteContentPayload.TYPE, PocketBuildRemoteContentPayload.STREAM_CODEC);
		PayloadTypeRegistry.playS2C().register(GameRuleStatePayload.TYPE, GameRuleStatePayload.STREAM_CODEC);

		// Feature toggles exposed as vanilla game rules (default true). Each change re-broadcasts the state to
		// clients and enforces the OFF transition (see the shared handlers).
		//? if >=1.21.11 {
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
		//?} else {
		/*ModGameRules.INVENTORY_ACCESS = GameRuleRegistry.register(ModGameRules.INVENTORY_ACCESS_NAME,
				GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true,
						(server, rule) -> ShulkerInventory.inventoryAccessRuleChanged(rule.get(), server)));
		ModGameRules.POCKET_BUILD = GameRuleRegistry.register(ModGameRules.POCKET_BUILD_NAME,
				GameRules.Category.MISC, GameRuleFactory.createBooleanRule(true,
						(server, rule) -> ShulkerInventory.pocketBuildRuleChanged(rule.get(), server)));
		*///?}

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
