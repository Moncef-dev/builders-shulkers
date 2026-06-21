package io.github.moncefdev.shulkerinventory.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// Client-side COSMETIC settings, persisted as JSON in the config directory and edited through the settings screen.
// Server-authoritative behaviour, anti-duplication, and feature availability are NEVER configured here (those are the
// server game rules); this only controls local visuals and sounds. All fields default to the current rich experience.
public final class ClientConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("builders-shulkers.json");
	private static ClientConfig instance = new ClientConfig();

	public boolean inventoryAnimation = true;
	public boolean pocketBuildAnimation = true;
	public boolean lidDissolve = true;
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
		if (!Files.exists(PATH)) {
			save();
			return;
		}
		try {
			ClientConfig loaded = GSON.fromJson(Files.readString(PATH), ClientConfig.class);
			if (loaded != null) {
				instance = loaded;
			}
		} catch (Exception e) {
			ShulkerInventory.LOGGER.warn("Could not read Builder's Shulkers config, using defaults", e);
		}
	}

	public static void save() {
		try {
			Files.writeString(PATH, GSON.toJson(instance));
		} catch (IOException e) {
			ShulkerInventory.LOGGER.warn("Could not save Builder's Shulkers config", e);
		}
	}
}
