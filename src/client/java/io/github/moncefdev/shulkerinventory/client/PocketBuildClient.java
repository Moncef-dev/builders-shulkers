package io.github.moncefdev.shulkerinventory.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.network.PocketBuildModePayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;

// Pocket-Build: place a shulker's selected content from the hotbar, without ever placing the shulker.
//
// Ctrl + right-click with a shulker selected in the hotbar and every menu closed enters the mode INSTEAD of placing
// the shulker (works on a block, on air, or on an entity, so all three use events route through here). While in the
// mode, a PLAIN right-click runs the vanilla use/interact flow on the SELECTED CONTENT (the content-swap mixins put
// it briefly in hand), server-authoritative so nothing can be duplicated. The mouse wheel cycles the selected slot.
// Ctrl + right-click again exits; so does opening a container, dropping the shulker, or moving the hotbar selection.
public final class PocketBuildClient {
	private PocketBuildClient() {}

	// The Ctrl + right-click toggle re-arms only after the right-click has been physically released, so holding it
	// down cannot flip-flop the mode.
	private static boolean rightClickReleased = true;
	// The Ctrl peek contents overlay only appears after Ctrl has been held this many ticks, so a quick Ctrl +
	// right-click toggle (which holds Ctrl briefly) never flashes the overlay before the mode opens/closes.
	private static final int PEEK_DELAY_TICKS = 5;
	private static int ctrlHeldTicks = 0;

	// Whether the Ctrl-peek contents overlay should be shown right now: in Pocket-Build mode with Ctrl held past the
	// debounce window. Read by PocketBuildOverlay.
	public static boolean peekVisible() {
		return PocketBuildMode.isActive() && ctrlHeldTicks >= PEEK_DELAY_TICKS;
	}

	public static void register() {
		// A right-click aimed at a block fires UseBlockCallback (carries the target); one aimed at air fires
		// UseItemCallback (no target). Route both through one handler; placement needs the block target.
		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> onRightClick(player, world, hand, hitResult));
		UseItemCallback.EVENT.register((player, world, hand) -> onRightClick(player, world, hand, null));
		// Right-clicking an ENTITY (e.g. an item frame) goes through neither callback above. Route it here too so the
		// mode toggle is consistent: Ctrl + right-click always enters/exits Pocket-Build, never the entity action.
		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> onUseEntity(player, world, hand));

		// Peek overlay: draw the shulker contents grid right after the vanilla hotbar (shown while Ctrl is held).
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR,
				Identifier.fromNamespaceAndPath("shulker-inventory", "pocket_build_overlay"),
				PocketBuildOverlay::render);

		// Block the off-hand swap key (F) while the mode is active, before vanilla's keybind handling reads it.
		ClientTickEvents.START_CLIENT_TICK.register(mc -> {
			if (!PocketBuildMode.isActive()) {
				return;
			}
			while (mc.options.keySwapOffhand.consumeClick()) {
				// discard
			}
		});

		// Re-arm the toggle, then apply the exit conditions while the mode is active.
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (!mc.options.keyUse.isDown()) {
				rightClickReleased = true;
			}
			if (!PocketBuildMode.isActive()) {
				return;
			}
			if (mc.player == null) {
				exitMode();
				return;
			}
			ItemStack held = mc.player.getMainHandItem();
			boolean stillShulker = ShulkerContents.isShulker(held);
			// Only opening a container UI (the player's own inventory, a chest, any container) ends the mode, like
			// the chest GUI flow. The pause menu, options, chat, etc. leave the mode running.
			if (mc.screen instanceof AbstractContainerScreen || !stillShulker
					|| mc.player.getInventory().getSelectedSlot() != PocketBuildMode.sourceHotbarSlot()) {
				exitMode();
				return;
			}
			// Debounce the Ctrl peek: count how long Ctrl has been held continuously. A quick Ctrl + right-click
			// toggle holds Ctrl only briefly, so it never reaches the threshold and the contents overlay never
			// flashes before the mode closes; a deliberate peek (sustained Ctrl) does show it.
			if (InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
				ctrlHeldTicks++;
			} else {
				ctrlHeldTicks = 0;
			}
		});
	}

	private static InteractionResult onRightClick(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
		Minecraft mc = Minecraft.getInstance();
		if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND || player != mc.player) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		boolean shulker = ShulkerContents.isShulker(held);
		boolean ctrl = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL);

		if (PocketBuildMode.isActive()) {
			// Ctrl + right-click exits the mode (and cancels the use). A PLAIN right-click is a placement: we PASS so
			// vanilla runs it; MultiPlayerGameModeMixin briefly swaps the selected content into hand, so the whole
			// vanilla flow (place / interact / sound / adventure / orientation) applies to the content, never the shulker.
			if (ctrl) {
				if (rightClickReleased) {
					exitMode();
					rightClickReleased = false;
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}

		// Enter only on a fresh Ctrl + right-click with a shulker in hand, every menu closed, and a server that runs
		// the mod (so it can validate the placement server-authoritatively). Otherwise leave vanilla alone.
		if (ctrl && shulker && mc.screen == null && ClientPlayNetworking.canSend(PocketBuildModePayload.TYPE)) {
			if (rightClickReleased) {
				enterMode(player, held);
				rightClickReleased = false;
			}
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult onUseEntity(Player player, Level world, InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		if (!world.isClientSide() || hand != InteractionHand.MAIN_HAND || player != mc.player) {
			return InteractionResult.PASS;
		}
		boolean ctrl = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL);
		if (PocketBuildMode.isActive()) {
			// Ctrl + right-click exits (and cancels). A PLAIN right-click is a content interaction: PASS so vanilla
			// runs it; the interact mixins briefly swap the selected content into hand, so the whole vanilla entity
			// flow (e.g. placing the content into an item frame, its sound, survival-only consume) applies to the
			// content, never the shulker.
			if (ctrl) {
				if (rightClickReleased) {
					exitMode();
					rightClickReleased = false;
				}
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		if (ctrl && ShulkerContents.isShulker(held)
				&& mc.screen == null && ClientPlayNetworking.canSend(PocketBuildModePayload.TYPE)) {
			if (rightClickReleased) {
				enterMode(player, held);
				rightClickReleased = false;
			}
			return InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}

	private static void enterMode(Player player, ItemStack held) {
		// Restart the Ctrl-peek debounce from zero, so opening the mode (a Ctrl + right-click that holds Ctrl briefly)
		// does not immediately flash the contents overlay, just like the exit case.
		ctrlHeldTicks = 0;
		int slot = player.getInventory().getSelectedSlot();
		// Begin the lid opening, resuming from the shulker's current openness (shared with the inventory open mode via
		// ClientShulkerSession.beginHeldOpening), so a quick re-enter continues from where the closing lid is instead
		// of snapping shut. No screen here, so armScreen=false. The server tags the held stack with this id (synced
		// back) so the render resolves the marker to this animation across every context.
		long id = ClientShulkerSession.beginHeldOpening(held, false);
		PocketBuildMode.enter(slot, held, id);
		ClientPlayNetworking.send(new PocketBuildModePayload(true, slot, id, PocketBuildMode.selectedContentSlot()));
		ClientShulkerSession.playOwnSound(true);
	}

	private static void exitMode() {
		long id = PocketBuildMode.animationId();
		int slot = PocketBuildMode.sourceHotbarSlot();
		PocketBuildMode.exit();
		if (ClientPlayNetworking.canSend(PocketBuildModePayload.TYPE)) {
			ClientPlayNetworking.send(new PocketBuildModePayload(false, slot, id, -1));
		}
		ClientShulkerSession.startClosing(id);
		ClientShulkerSession.playOwnSound(false);
	}
}
