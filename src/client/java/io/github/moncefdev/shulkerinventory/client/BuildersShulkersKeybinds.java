package io.github.moncefdev.shulkerinventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.moncefdev.shulkerinventory.client.gui.BuildersShulkersSettingsScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

// Vanilla keybinds: registered through Fabric's KeyMappingHelper so they appear in Options -> Controls under our own
// "Builder's Shulkers" category (a KeyMapping.Category registered by Identifier), rebindable / unbindable like any
// vanilla bind. The two HELD bindings (Pocket-Build modifier, Peek) are polled via isDown() by PocketBuildClient at the
// moment they matter; Open Settings and Toggle Pocket-Build are edge-triggered via consumeClick(). An unbound binding's
// isDown() is always false, so unbinding the modifier disables modifier + right-click entry, and unbinding Peek hides
// the overlay - a clean per-client off switch with no extra config option.
public final class BuildersShulkersKeybinds {
	private BuildersShulkersKeybinds() {}

	private static KeyMapping openSettings;
	private static KeyMapping pocketBuildModifier;
	private static KeyMapping pocketBuildToggle;
	private static KeyMapping peek;
	// Previous-tick down-state of the toggle key, so it fires once per fresh press (rising edge), never while held.
	private static boolean toggleWasDown = false;

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath("builders-shulkers", "main"));
		// Open the settings screen (default B, in-game only).
		openSettings = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.builders-shulkers.open_settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, category));
		// Held together with right-click to enter/exit Pocket-Build (default Left Control), read via isDown() at the use.
		pocketBuildModifier = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.builders-shulkers.pocket_build_modifier", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, category));
		// Single press to enter/exit Pocket-Build (unbound by default; a one-button path for controller / mobile play).
		pocketBuildToggle = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.builders-shulkers.pocket_build_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
		// Held to show the contents-peek overlay (default Left Control), read via isDown().
		peek = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.builders-shulkers.peek", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, category));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openSettings.consumeClick()) {
				if (mc.screen == null) {
					mc.setScreen(new BuildersShulkersSettingsScreen(null));
				}
			}
			// Toggle on the rising edge of isDown() only: holding the key must NOT keep re-firing (in 26.x the keybind
			// click queue includes key-repeats, which would flip-flop the mode). Drain that queue so it cannot pile up,
			// and act once per fresh press; release and press again to toggle once more.
			while (pocketBuildToggle.consumeClick()) {
				// drained: the rising edge below is the real trigger
			}
			boolean toggleDown = pocketBuildToggle.isDown();
			if (toggleDown && !toggleWasDown) {
				PocketBuildClient.requestToggle();
			}
			toggleWasDown = toggleDown;
		});
	}

	// Whether the held Pocket-Build modifier is down right now. False when unbound, which disables modifier +
	// right-click entry (a per-client off switch). Read at the right-click moment by PocketBuildClient.
	public static boolean isPocketBuildModifierDown() {
		return pocketBuildModifier != null && pocketBuildModifier.isDown();
	}

	// Whether the held Peek binding is down right now. False when unbound, so the overlay never shows.
	public static boolean isPeekDown() {
		return peek != null && peek.isDown();
	}
}
