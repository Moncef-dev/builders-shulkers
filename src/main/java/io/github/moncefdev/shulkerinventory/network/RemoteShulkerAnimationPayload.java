package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client (broadcast to players who can see the holder): mirror a shulker lid animation that another
// player triggered, so it is visible on the shulker held by that other player. `opening` true starts the open
// animation, false starts the close. The initiator animates locally and does not need this; it is sent only to
// the OTHER tracking players. Purely cosmetic. animationId is the (globally unique) marker stamped on the stack.
public record RemoteShulkerAnimationPayload(long animationId, boolean opening, int holderEntityId) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("shulker-inventory", "remote_shulker_animation");
	public static final Type<RemoteShulkerAnimationPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, RemoteShulkerAnimationPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, RemoteShulkerAnimationPayload::animationId,
			ByteBufCodecs.BOOL, RemoteShulkerAnimationPayload::opening,
			ByteBufCodecs.VAR_INT, RemoteShulkerAnimationPayload::holderEntityId,
			RemoteShulkerAnimationPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
