package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Client to server: while in Pocket-Build mode, the selected content slot changed (the player scrolled). Keeps the
// server's selected slot in sync so a placement (a normal vanilla use packet) swaps in the right content.
public record PocketBuildSelectPayload(int contentSlot) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("shulker-inventory", "pocket_build_select");
	public static final Type<PocketBuildSelectPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, PocketBuildSelectPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PocketBuildSelectPayload::contentSlot,
			PocketBuildSelectPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
