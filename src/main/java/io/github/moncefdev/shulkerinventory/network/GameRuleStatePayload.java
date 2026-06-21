package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: the current state of the mod's game rules, sent on join and whenever a rule changes. Custom game
// rules are not synced to the client automatically, so the client caches these (ClientGameRuleState) to decide whether
// to intercept a shulker right-click / enter Pocket-Build, keeping the client prediction in step with the server.
public record GameRuleStatePayload(boolean inventoryAccess, boolean pocketBuild) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "game_rule_state");
	public static final Type<GameRuleStatePayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, GameRuleStatePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, GameRuleStatePayload::inventoryAccess,
			ByteBufCodecs.BOOL, GameRuleStatePayload::pocketBuild,
			GameRuleStatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
