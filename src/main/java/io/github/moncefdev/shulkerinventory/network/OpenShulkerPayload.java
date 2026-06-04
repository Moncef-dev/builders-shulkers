package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client to server: request to open (or, when already open, close) the shulker at the given
// player-inventory slot. animationId is allocated on the client and echoed back so the client
// can drive the matching lid animation.
public record OpenShulkerPayload(int slotIndex, long animationId) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "open_shulker");
	public static final Type<OpenShulkerPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, OpenShulkerPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenShulkerPayload::slotIndex,
			ByteBufCodecs.VAR_LONG, OpenShulkerPayload::animationId,
			OpenShulkerPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
