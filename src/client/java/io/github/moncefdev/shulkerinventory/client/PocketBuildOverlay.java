package io.github.moncefdev.shulkerinventory.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import net.minecraft.core.NonNullList;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

// Peek overlay: while in Pocket-Build mode AND Ctrl is held, draw the held shulker's contents as the actual vanilla
// shulker-box container interface (same texture/slots as opening the box), cropped to the box itself (title bar + the
// 3 content rows + a clean bottom border, NOT the player inventory which is redundant on the HUD). The selected slot
// uses the vanilla container HOVER highlight (the white wash you get pointing at a slot). Rendered through the 26.1.2
// GuiGraphicsExtractor. Ctrl is a pure visual peek; the scroll/selection works with or without it.
public final class PocketBuildOverlay {
	private PocketBuildOverlay() {}

	// The real shulker-box GUI texture (176x166 art on a 256x256 sheet), as used by ShulkerBoxScreen.
	private static final Identifier CONTAINER_TEXTURE =
			Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");
	private static final int TEX = 256;
	private static final int PANEL_W = 176;
	// Top crop: title bar + the 3 shulker rows (player inventory begins just below this).
	private static final int TOP_H = 72;
	// Bottom border strip taken from the very bottom of the texture, to close the box cleanly under the 3 rows.
	private static final int BORDER_SRC_Y = 159;
	private static final int BORDER_H = 7;

	private static final int COLS = 9;
	private static final int ROWS = 3;
	private static final int SLOT_PITCH = 18;
	private static final int FIRST_SLOT_X = 8;
	private static final int FIRST_SLOT_Y = 18;
	private static final int ITEM = 16;

	// Keep the panel bottom above the vanilla selected-item-name popup (drawn at guiHeight - 59).
	private static final int BOTTOM_MARGIN = 68;
	private static final int TITLE_COLOR = 0xFF404040;
	private static final int HOVER_COLOR = 0x80FFFFFF;

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		// Ctrl-peek with debounce (so a quick Ctrl + right-click toggle does not flash the overlay).
		if (!PocketBuildClient.peekVisible()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.screen != null) {
			return;
		}
		ItemStack shulker = mc.player.getMainHandItem();
		if (!ShulkerContents.isShulker(shulker)) {
			return;
		}
		NonNullList<ItemStack> items = PocketBuildMode.snapshotContents(shulker);
		int selected = PocketBuildMode.selectedContentSlot();

		int panelH = TOP_H + BORDER_H;
		int x0 = (mc.getWindow().getGuiScaledWidth() - PANEL_W) / 2;
		int y0 = mc.getWindow().getGuiScaledHeight() - panelH - BOTTOM_MARGIN;

		// Container background: top (title bar + 3 rows) then the bottom border strip.
		g.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, x0, y0, 0f, 0f, PANEL_W, TOP_H, TEX, TEX);
		g.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, x0, y0 + TOP_H, 0f, (float) BORDER_SRC_Y,
				PANEL_W, BORDER_H, TEX, TEX);

		// Title (the held shulker's name), like the real container header.
		g.text(mc.font, shulker.getHoverName(), x0 + FIRST_SLOT_X, y0 + 6, TITLE_COLOR, false);

		// Items, rendered exactly like a vanilla container slot: the icon plus the full vanilla decorations (count,
		// durability bar, cooldown overlay). Using vanilla's own itemDecorations means every subtlety emerges - and
		// anything we would forget by drawing it by hand is already covered.
		for (int i = 0; i < COLS * ROWS; i++) {
			ItemStack stack = items.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			int ix = itemX(x0, i);
			int iy = itemY(y0, i);
			g.item(stack, ix, iy);
			g.itemDecorations(mc.font, stack, ix, iy);
		}

		// Selected slot: vanilla container hover wash.
		if (selected >= 0) {
			int ix = itemX(x0, selected);
			int iy = itemY(y0, selected);
			g.fill(ix, iy, ix + ITEM, iy + ITEM, HOVER_COLOR);
		}
	}

	private static int itemX(int x0, int index) {
		return x0 + FIRST_SLOT_X + (index % COLS) * SLOT_PITCH;
	}

	private static int itemY(int y0, int index) {
		return y0 + FIRST_SLOT_Y + (index / COLS) * SLOT_PITCH;
	}
}
