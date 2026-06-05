package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// While in Pocket-Build mode, the held shulker's hotbar slot should show the COUNT of the SELECTED CONTENT (how many of
// the block you are about to place you have), like a normal hotbar item. Pocket-Build places BLOCKS only (the full-use
// gamerule does not exist yet), so the durability bar and cooldown overlay have no meaning here: we draw ONLY the count,
// not the rest of the vanilla decorations. The slot ICON is drawn earlier and separately, so it stays the shulker with
// the content rendered inside. We redirect Gui.extractSlot's itemDecorations call (symmetrically to how
// GuiSelectedItemNameMixin redirects the name popup); the held Pocket-Build shulker is identified by its animation_id
// marker matching the active session, so another shulker in the hotbar is unaffected. The count is scaled down about
// its OWN centre so it reads smaller without drifting toward the corner.
@Mixin(Gui.class)
public abstract class GuiSelectedContentDecorationsMixin {
	// The selected content's count is drawn at this fraction of normal hotbar size.
	private static final float SELECTED_COUNT_SCALE = 0.8f;

	@Redirect(method = "extractSlot", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V"))
	private void shulkerInventory$decorateSelectedContent(GuiGraphicsExtractor extractor, Font font, ItemStack stack, int x, int y) {
		if (PocketBuildMode.isActive()) {
			Long marker = ShulkerAnimationMarker.get(stack);
			if (marker != null && marker == PocketBuildMode.animationId()) {
				shulkerInventory$drawScaledCount(extractor, font, PocketBuildMode.selectedStack(stack), x, y);
				return;
			}
		}
		extractor.itemDecorations(font, stack, x, y);
	}

	// Draws the content's stack count (only when > 1, like vanilla), right-aligned in the slot's bottom-right, scaled to
	// SELECTED_COUNT_SCALE about its RIGHT EDGE so every count stays right-aligned (scaling about the centre would push a
	// 1-digit count further right than a 2-digit one). No durability bar, no cooldown overlay.
	private static void shulkerInventory$drawScaledCount(GuiGraphicsExtractor extractor, Font font, ItemStack content, int x, int y) {
		int count = content.getCount();
		if (content.isEmpty() || count <= 1) {
			return;
		}
		String text = String.valueOf(count);
		int width = font.width(text);
		// Vanilla count placement: right-aligned, baseline near the slot's bottom-right (x + 17 - width, y + 9).
		int textX = x + 17 - width;
		int textY = y + 9;
		// Anchor the scale at the count's RIGHT EDGE (x + 17) and vertical centre, so the right edge stays put and every
		// count remains right-aligned, just shrunk.
		float anchorX = x + 17f;
		float anchorY = textY + 4f;
		Matrix3x2fStack pose = extractor.pose();
		pose.pushMatrix();
		pose.translate(anchorX, anchorY);
		pose.scale(SELECTED_COUNT_SCALE, SELECTED_COUNT_SCALE);
		pose.translate(-anchorX, -anchorY);
		extractor.text(font, text, textX, textY, 0xFFFFFFFF, true);
		pose.popMatrix();
	}
}
