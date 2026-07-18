package io.github.moncefdev.shulkerinventory.platform;

// Holder for the loader-specific services the shared code calls at runtime. Each loader's entrypoint installs its
// implementation as the very first thing it does, so shared code can use these unconditionally.
public final class Platform {
	private Platform() {}

	private static PlatformNetwork network;

	public static void setNetwork(PlatformNetwork implementation) {
		network = implementation;
	}

	public static PlatformNetwork network() {
		return network;
	}
}
