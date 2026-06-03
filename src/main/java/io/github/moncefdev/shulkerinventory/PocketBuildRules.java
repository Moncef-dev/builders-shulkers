package io.github.moncefdev.shulkerinventory;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

// The rule that bounds what a shulker's selected content may do in Pocket-Build mode. Today: only block items are
// usable - Pocket-Build is a pocket of placeable BLOCKS, not a tool/interaction handler. This keeps the feature
// coherent (no half-working tools: you can't right-click a shovel into a path from a shulker any more than you can
// mine with it), and it leans on Mojang's own BlockItem typing rather than a hand-rolled list. This is the single
// place the planned "use anything, exactly like in hand" gamerule (off by default) will widen.
public final class PocketBuildRules {
	private PocketBuildRules() {}

	// Whether the selected content may be used (placed) via Pocket-Build right now. Used by the placement gate (server
	// authority + client prediction). The content render decides 3D-vs-GUI separately on item TYPE (BlockItem), which
	// is about the held transform fitting the box, not about usability - the two coincide today but must not be merged,
	// or the future gamerule would render an overflowing held tool when it widens this rule.
	public static boolean isUsable(ItemStack content) {
		return content.getItem() instanceof BlockItem;
	}
}
