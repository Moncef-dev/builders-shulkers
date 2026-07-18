package io.github.moncefdev.shulkerinventory.client.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.file.Path;

// Holder for the loader-specific client services the shared client code calls at runtime. Each loader's client
// entrypoint installs its implementation as the very first thing it does, so shared code can use these
// unconditionally.
public final class ClientPlatform {
	private ClientPlatform() {}

	// The client-side networking operations the shared code needs from the mod loader.
	public interface Network {
		// Sends a payload to the server. The payload type must have been registered serverbound at init.
		void send(CustomPacketPayload payload);

		// Whether the connected server declared it can receive the payload type. False on servers without the mod;
		// callers use this to fall back to vanilla behavior there (no dead clicks, no bogus sends).
		boolean canSend(CustomPacketPayload.Type<?> type);
	}

	private static Network network;
	private static Path configDir;

	public static void setNetwork(Network implementation) {
		network = implementation;
	}

	public static Network network() {
		return network;
	}

	public static void setConfigDir(Path dir) {
		configDir = dir;
	}

	public static Path configDir() {
		return configDir;
	}
}
