package io.github.moncefdev.shulkerinventory.client.compat;

import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildClientSwap;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

// Decision logic behind the Litematica easy place interop (EasyPlaceUtilsMixin), kept out of the mixin so nothing but
// the two hooks is merged into Litematica's class. Deliberately free of any fi.dy.masa.* reference: only the mixin
// itself (which is gated on Litematica being installed) links against Litematica.
//
// Easy place looks up the item its schematic needs, tries to put it in hand from the INVENTORY, then checks the hand
// and gives up when nothing matches. A shulker box never matches, so pocket-building always failed with
// "Action prevented by the Easy Place mode". These helpers let the held box answer instead: the required item becomes
// the Pocket-Build selection, and easy place goes on to call useItemOn, which is the very method the content swap
// already wraps. The placement, its hit vector (so the block orientation easy place computed), the packet and the
// server-authoritative swap are all unchanged.
public final class LitematicaEasyPlaceCompat {
	private LitematicaEasyPlaceCompat() {}

	// The two decisions the mixins hand over, shared by BOTH easy place implementations Litematica ships (the historic
	// WorldUtils one and the EasyPlaceUtils rewrite, selected by its easyPlacePostRewrite setting; each has the same
	// two call sites, so each gets the same pair of hooks).

	// Whether WE answer the "get me that item" step, so Litematica's inventory pick is skipped. True for the whole
	// duration of the mode, even when the box cannot serve the item: the pick would otherwise swap the box out of its
	// locked hotbar slot (or overwrite it in creative) as soon as the schematic asks for something else.
	public static boolean handlesPick(ItemStack required) {
		if (!isPocketBuilding()) {
			return false;
		}
		serveFromBox(required);
		return true;
	}

	// The hand easy place should use: whatever vanilla found, else MAIN_HAND once the selection serves the item.
	public static InteractionHand handFor(ItemStack required, InteractionHand vanillaHand) {
		if (vanillaHand != null) {
			return vanillaHand;
		}
		// Only claim the hand when the content swap has NOT already happened. The rewrite implementation hooks itself
		// into useItemOn, so it can run INSIDE the swap, where the hand already holds the content selected when the
		// swap started: a selection changed now would not reach the nested placement, which would place the wrong
		// block. Deferring costs one click instead. The historic implementation runs from the input/tick layer, before
		// any swap, so it is unaffected.
		if (PocketBuildClientSwap.isSwapping()) {
			return null;
		}
		return serveFromBox(required) ? InteractionHand.MAIN_HAND : null;
	}

	// The box Pocket-Build is currently building from, or EMPTY when the mode is off. While the mode is on, its hotbar
	// slot is locked, which is why the mixin keeps Litematica's inventory pick away from it.
	public static ItemStack modeBox() {
		Minecraft mc = Minecraft.getInstance();
		if (!PocketBuildMode.isActive() || mc.player == null) {
			return ItemStack.EMPTY;
		}
		ItemStack box = PocketBuildClient.currentBox(mc.player);
		return ShulkerContents.isShulker(box) ? box : ItemStack.EMPTY;
	}

	public static boolean isPocketBuilding() {
		return !modeBox().isEmpty();
	}

	// Point the Pocket-Build selection at the item easy place asked for, and report whether the box can serve it.
	// Idempotent: an already-matching selection answers true without re-syncing, so the two hooks can both call this
	// within one easy place run. The selection is synced to the server exactly like a scroll, over the same connection
	// as the use-on packet that follows, so the server has it before it swaps the content in.
	public static boolean serveFromBox(ItemStack required) {
		ItemStack box = modeBox();
		if (box.isEmpty() || required == null || required.isEmpty()) {
			return false;
		}
		if (ItemStack.isSameItemSameComponents(PocketBuildMode.selectedStack(box), required)) {
			return true;
		}
		return PocketBuildClient.selectContentMatching(box, required);
	}
}
