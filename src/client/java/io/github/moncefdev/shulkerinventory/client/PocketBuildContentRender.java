package io.github.moncefdev.shulkerinventory.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

// Tells whether a Pocket-Build content item is rendered FLAT (2D) or as a shaded 3D block. The model's own block-light
// flag (ItemStackRenderState.usesBlockLight) is the exact vanilla signal: true for a 3D cube, false for a flat sprite.
// instanceof BlockItem does NOT capture this - a sapling is a BlockItem yet renders as a flat cross - which is why the
// render path keys on this flag, not on the item type. Flat content takes the 2D path (its own flat lighting in the
// slot, re-centred in the held box); a 3D block keeps its layer untouched. Cached per Item, the flag being a stable
// property of the item's model. NB: this is purely a RENDER classification; placement legality stays a separate
// BlockItem test (PocketBuildRules).
public final class PocketBuildContentRender {
	private PocketBuildContentRender() {}

	private static final Map<Item, Boolean> FLAT_BY_ITEM = new ConcurrentHashMap<>();

	public static boolean isFlat(ItemStack content, @Nullable Level level) {
		return FLAT_BY_ITEM.computeIfAbsent(content.getItem(), item -> {
			ItemStackRenderState probe = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver()
					.updateForTopItem(probe, content, ItemDisplayContext.GUI, level, null, 0);
			return !probe.usesBlockLight();
		});
	}
}
