package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client to server: the player entered (entering=true) or left (false) Pocket-Build mode with the shulker selected
// at hotbarSlot. animationId is allocated on the client and echoed so the server can drive the held-shulker lid
// animation, exactly like an inventory open but without a menu: tag the held shulker so every render context
// animates it, and broadcast the open/close to the players who can see the holder.
public record PocketBuildModePayload(boolean entering, int hotbarSlot, long animationId, int contentSlot)
		implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build_mode");
	public static final Type<PocketBuildModePayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, PocketBuildModePayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, PocketBuildModePayload::entering,
			ByteBufCodecs.VAR_INT, PocketBuildModePayload::hotbarSlot,
			ByteBufCodecs.VAR_LONG, PocketBuildModePayload::animationId,
			ByteBufCodecs.VAR_INT, PocketBuildModePayload::contentSlot,
			PocketBuildModePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
