package io.github.moncefdev.shulkerinventory.neoforge;

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
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
//? if >=1.21.11 {
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.gamerules.GameRules;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
//?} else {
/*import net.minecraft.world.level.GameRules;
*///?}
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// NeoForge entrypoint: installs the NeoForge networking implementation, then wires the shared server-side logic
// (ShulkerInventory) into NeoForge's payload registration, game events, and game rules. Payload handlers hop
// to the game thread (context.enqueueWork), matching the shared handlers' threading contract.
// The mod id is builders_shulkers (NeoForge ids allow no hyphen); every Identifier namespace stays
// builders-shulkers, so payloads and game rules are wire- and save-compatible with the fabric jar.
// The game-rule wiring is version-forked on this branch: 1.21.11 uses the namespaced registry system (vanilla's
// registerBoolean helper, opened by our access transformer, during the GAME_RULE RegisterEvent) with a per-tick
// change poller (no vanilla change hook); 1.21.10 uses the older static system, whose type factory takes the
// change callback DIRECTLY (create(default, callback), both members opened by the access transformer), so no
// registry event and no poller are needed there.
@Mod(BuildersShulkersNeoForge.MOD_ID)
public final class BuildersShulkersNeoForge {
	public static final String MOD_ID = "builders_shulkers";

	public BuildersShulkersNeoForge(IEventBus modBus) {
		// Install the loader services FIRST, so every later step (and anything it triggers) can use them.
		Platform.setNetwork(new PlatformNetwork() {
			@Override
			public void send(ServerPlayer player, CustomPacketPayload payload) {
				PacketDistributor.sendToPlayer(player, payload);
			}

			@Override
			public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
				return player.connection.hasChannel(type);
			}

			@Override
			public Collection<ServerPlayer> tracking(ServerPlayer player) {
				// The vanilla per-entity tracking set (who can see this player), the same source Fabric API's
				// PlayerLookup.tracking reads; opened by our access transformer.
				ChunkMap chunkMap = ((ServerChunkCache) player.level().getChunkSource()).chunkMap;
				ChunkMap.TrackedEntity tracked = chunkMap.entityMap.get(player.getId());
				if (tracked == null) {
					return List.of();
				}
				List<ServerPlayer> viewers = new ArrayList<>(tracked.seenBy.size());
				for (ServerPlayerConnection connection : tracked.seenBy) {
					viewers.add(connection.getPlayer());
				}
				return viewers;
			}
		});

		modBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
			// optional(): connections without the channel (vanilla clients, servers without the mod) stay allowed;
			// senders gate on canSend/hasChannel exactly like on fabric, so the compat behavior is identical.
			PayloadRegistrar registrar = event.registrar("1").optional();
			registrar.playToServer(OpenShulkerPayload.TYPE, OpenShulkerPayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							ShulkerInventory.handleOpenShulker(payload, (ServerPlayer) context.player())));
			registrar.playToServer(PocketBuildModePayload.TYPE, PocketBuildModePayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							ShulkerInventory.handlePocketBuildMode(payload, (ServerPlayer) context.player())));
			registrar.playToServer(PocketBuildSelectPayload.TYPE, PocketBuildSelectPayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							ShulkerInventory.handlePocketBuildSelect(payload, (ServerPlayer) context.player())));
			// Clientbound handlers are LAMBDAS (not method references) delegating into the client-only handler
			// class: registration also runs on the dedicated server, and a method reference would load the client
			// class there; a lambda only loads it when actually invoked, which happens on the client alone.
			registrar.playToClient(OpenPlayerInventoryPayload.TYPE, OpenPlayerInventoryPayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							NeoForgeClientPayloadHandlers.handleOpenPlayerInventory(payload)));
			registrar.playToClient(RemoteShulkerAnimationPayload.TYPE, RemoteShulkerAnimationPayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							NeoForgeClientPayloadHandlers.handleRemoteAnimation(payload)));
			registrar.playToClient(PocketBuildRemoteContentPayload.TYPE, PocketBuildRemoteContentPayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							NeoForgeClientPayloadHandlers.handlePocketBuildRemoteContent(payload)));
			registrar.playToClient(GameRuleStatePayload.TYPE, GameRuleStatePayload.STREAM_CODEC,
					(payload, context) -> context.enqueueWork(() ->
							NeoForgeClientPayloadHandlers.handleGameRuleState(payload)));
		});

		// Feature toggles exposed as vanilla game rules (default true). See the class comment for the per-version
		// registration split.
		//? if >=1.21.11 {
		modBus.addListener(RegisterEvent.class, event -> {
			if (event.getRegistryKey() == Registries.GAME_RULE) {
				ModGameRules.INVENTORY_ACCESS = GameRules.registerBoolean(
						ModGameRules.INVENTORY_ACCESS_ID.toString(), GameRuleCategory.MISC, true);
				ModGameRules.POCKET_BUILD = GameRules.registerBoolean(
						ModGameRules.POCKET_BUILD_ID.toString(), GameRuleCategory.MISC, true);
			}
		});
		// Game-rule change detection: no change hook in this version's system (Fabric's changeCallback is Fabric
		// API, NeoForge exposes none), so poll the two booleans once per server tick and fire the shared
		// rule-changed handlers on a delta: two map reads per tick, at most one tick of latency.
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class,
				event -> GameRuleChangePoller.tick(event.getServer()));
		//?} else {
		/*ModGameRules.INVENTORY_ACCESS = GameRules.register(ModGameRules.INVENTORY_ACCESS_NAME,
				GameRules.Category.MISC, GameRules.BooleanValue.create(true,
						(server, value) -> ShulkerInventory.inventoryAccessRuleChanged(value.get(), server)));
		ModGameRules.POCKET_BUILD = GameRules.register(ModGameRules.POCKET_BUILD_NAME,
				GameRules.Category.MISC, GameRules.BooleanValue.create(true,
						(server, value) -> ShulkerInventory.pocketBuildRuleChanged(value.get(), server)));
		*///?}

		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ShulkerInventory.onPlayerJoin(player, player.level().getServer());
			}
		});
		NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
			if (event.getEntity() instanceof ServerPlayer player) {
				ShulkerInventory.onPlayerDisconnect(player);
			}
		});

		ShulkerInventory.LOGGER.info("Builder's Shulkers initialized");
	}
}
