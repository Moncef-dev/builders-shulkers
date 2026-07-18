package io.github.moncefdev.shulkerinventory.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Client-side COSMETIC settings, persisted as JSON in the config directory and edited through the settings screen.
// Server-authoritative behaviour, anti-duplication, and feature availability are NEVER configured here (those are the
// server game rules); this only controls local visuals and sounds. All fields default to the current rich experience.
public final class ClientConfig {
	// How the Pocket-Build lid clears to reveal the contents. NONE keeps it solid (lifts or stays per the animation
	// setting); DISSOLVE uses the order-independent cutout dissolve; DISAPPEAR draws it only while closed and skips it
	// entirely once opening starts (instant vanish, no draw call). None of these ever uses a translucent/blended pass.
	public enum LidEffect { NONE, DISSOLVE, DISAPPEAR }

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance = new ClientConfig();

	// Resolved lazily: the config directory comes from the loader (via ClientPlatform), which the entrypoint
	// installs before calling load().
	private static Path path() {
		return ClientPlatform.configDir().resolve("builders-shulkers.json");
	}

	public boolean inventoryAnimation = true;
	public boolean pocketBuildAnimation = true;
	public LidEffect lidEffect = LidEffect.DISSOLVE;
	public boolean inBoxContent = true;
	public boolean openCloseSounds = true;
	public boolean otherPlayerAnimations = true;
	// When the matching animation is OFF, whether the lid rests open (true) or closed (false).
	public boolean inventoryLidOpenWhenAnimationOff = true;
	public boolean pocketBuildLidOpenWhenAnimationOff = true;
	// Whether to draw the selected-content count on the held Pocket-Build slot at all.
	public boolean showItemCount = true;
	// Scale of the selected-content count drawn on the held Pocket-Build slot (1.0 = vanilla size).
	public double itemCountSize = 0.8;

	public static ClientConfig get() {
		return instance;
	}

	public static void load() {
		if (!Files.exists(path())) {
			save();
			return;
		}
		try {
			ClientConfig loaded = GSON.fromJson(Files.readString(path()), ClientConfig.class);
			if (loaded != null) {
				instance = loaded;
			}
		} catch (Exception e) {
			ShulkerInventory.LOGGER.warn("Could not read Builder's Shulkers config, using defaults", e);
		}
	}

	public static void save() {
		try {
			Files.writeString(path(), GSON.toJson(instance));
		} catch (IOException e) {
			ShulkerInventory.LOGGER.warn("Could not save Builder's Shulkers config", e);
		}
	}
}
