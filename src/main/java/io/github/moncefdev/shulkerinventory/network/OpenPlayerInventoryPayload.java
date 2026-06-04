package io.github.moncefdev.shulkerinventory.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

// Server to client: re-open the player's own inventory screen after a shulker session ends.
public record OpenPlayerInventoryPayload() implements CustomPacketPayload {
	public static final OpenPlayerInventoryPayload INSTANCE = new OpenPlayerInventoryPayload();
	public static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "open_player_inventory");
	public static final Type<OpenPlayerInventoryPayload> TYPE = new Type<>(ID);

	public static final StreamCodec<ByteBuf, OpenPlayerInventoryPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
