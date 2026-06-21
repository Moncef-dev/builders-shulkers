package io.github.moncefdev.shulkerinventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.moncefdev.shulkerinventory.client.gui.BuildersShulkersSettingsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

// Vanilla keybind: registered through Fabric's KeyMappingHelper so it appears in Options -> Controls under our own
// "Builder's Shulkers" category (a KeyMapping.Category registered by Identifier) and is rebindable / unbindable like
// any vanilla bind. Default B; opens the mod's settings screen, in-game only.
public final class BuildersShulkersKeybinds {
	private BuildersShulkersKeybinds() {}

	private static KeyMapping openSettings;

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("builders-shulkers", "main"));
		openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.builders-shulkers.open_settings",
				InputConstants.Type.KEYSYM,
				GLFW.GLFW_KEY_B,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openSettings.consumeClick()) {
				if (mc.gui.screen() == null) {
					mc.gui.setScreen(new BuildersShulkersSettingsScreen(null));
				}
			}
		});
	}
}
