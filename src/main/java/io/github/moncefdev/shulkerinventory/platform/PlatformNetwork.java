package io.github.moncefdev.shulkerinventory.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

// The server-side networking operations the shared code needs from the mod loader. One implementation per loader,
// installed by that loader's entrypoint (see Platform) before any shared code can run.
public interface PlatformNetwork {
	// Sends a payload to this player. The payload type must have been registered clientbound at init.
	void send(ServerPlayer player, CustomPacketPayload payload);

	// Whether this player's client declared it can receive the payload type. False for clients without the mod and
	// for clients still mid-handshake; senders use this to skip them (never push a payload a client cannot handle).
	boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);

	// The players currently tracking (able to see) this player. May include the player itself; callers filter.
	Collection<ServerPlayer> tracking(ServerPlayer player);
}
