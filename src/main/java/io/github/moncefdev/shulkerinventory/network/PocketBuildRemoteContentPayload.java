package io.github.moncefdev.shulkerinventory.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Server to client (broadcast to players who can see the holder): the Pocket-Build selected content to draw INSIDE a
// held shulker, so other players also see the dissolving lid AND the selected block inside the box, not just the plain
// lid animation. Sent on enter and on every selection change (scroll). `content` is the selected stack, EMPTY for an
// empty selection (the dissolve still applies, just with nothing drawn inside). The holder draws its own content
// locally and is excluded. Purely cosmetic; the animation marker stamped on the held stack (synced like any component)
// ties this to the right animation across every render context. animationId is the globally-unique marker.
public record PocketBuildRemoteContentPayload(long animationId, int holderEntityId, ItemStack content) implements CustomPacketPayload {
	public static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build_remote_content");
	public static final Type<PocketBuildRemoteContentPayload> TYPE = new Type<>(ID);

	// Uses ItemStack.OPTIONAL_STREAM_CODEC (allows EMPTY), so it needs a RegistryFriendlyByteBuf (item components need
	// registry access to encode). The VAR_LONG / VAR_INT codecs accept any ByteBuf, so they compose onto it fine.
	public static final StreamCodec<RegistryFriendlyByteBuf, PocketBuildRemoteContentPayload> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, PocketBuildRemoteContentPayload::animationId,
			ByteBufCodecs.VAR_INT, PocketBuildRemoteContentPayload::holderEntityId,
			ItemStack.OPTIONAL_STREAM_CODEC, PocketBuildRemoteContentPayload::content,
			PocketBuildRemoteContentPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
