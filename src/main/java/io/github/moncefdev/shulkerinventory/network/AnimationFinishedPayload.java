package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client to server: the close animation for this id has finished, so the server may drop the
// transient animation marker it placed on the shulker stack.
public record AnimationFinishedPayload(long animationId) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("shulker-inventory", "animation_finished");
	public static final Type<AnimationFinishedPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, AnimationFinishedPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, AnimationFinishedPayload::animationId,
			AnimationFinishedPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
