package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.client.mixin.ItemStackRenderStateAccessor;
import io.github.moncefdev.shulkerinventory.client.mixin.LayerRenderStateAccessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.block.model.ItemTransform;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3fc;
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
	private static final Map<Item, Boolean> FACEON_BY_ITEM = new ConcurrentHashMap<>();
	private static final Map<Item, Boolean> SPECIAL_BY_ITEM = new ConcurrentHashMap<>();
	private static final Map<Item, float[]> BOX_GUI_REF = new ConcurrentHashMap<>();

	public static boolean isFlat(ItemStack content, @Nullable Level level) {
		return FLAT_BY_ITEM.computeIfAbsent(content.getItem(), item -> {
			ItemStackRenderState probe = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver()
					.updateForTopItem(probe, content, ItemDisplayContext.GUI, level, null, 0);
			return !probe.usesBlockLight();
		});
	}

	// Whether the content's NATIVE GUI display is face-on (rotation ~0), i.e. drawn as a flat 2D icon, vs already turned
	// into an isometric 3D shape. This separates the two "reports flat lighting but is really 3D" cases: decorated_pot
	// reports flat AND is face-on (its inventory iso comes from the oversized PIP we bypass) so it needs the iso
	// substitution, whereas calibrated_sculk_sensor / banners / a tilted shield report flat but are ALREADY iso-rotated
	// natively, so they belong on the unified 3D path as-is. Cached per Item.
	public static boolean isFaceOnGui(ItemStack content, @Nullable Level level) {
		return FACEON_BY_ITEM.computeIfAbsent(content.getItem(), item -> {
			ItemStackRenderState probe = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver()
					.updateForTopItem(probe, content, ItemDisplayContext.GUI, level, null, 0);
			ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) probe;
			if (state.shulkerInventory$getActiveLayerCount() == 0) {
				return true;
			}
			Vector3fc r = ((LayerRenderStateAccessor) state.shulkerInventory$getLayers()[0])
					.shulkerInventory$getItemTransform().rotation();
			return Math.abs(r.x()) < 0.5f && Math.abs(r.y()) < 0.5f && Math.abs(r.z()) < 0.5f;
		});
	}

	// Whether the content is drawn by a special block-entity renderer (skull, conduit, copper golem statue, shulker,
	// bed, decorated pot...). Such a block uses its own per-context display transform, which in third person is a "held
	// in the hand" pose that reads wrong nested in the box; like a flat sprite, it is instead rendered with its GUI
	// (inventory) display so it looks the same - upright, not held - in every context. Cached per Item.
	public static boolean isSpecial(ItemStack content, @Nullable Level level) {
		return SPECIAL_BY_ITEM.computeIfAbsent(content.getItem(), item -> {
			ItemStackRenderState probe = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver()
					.updateForTopItem(probe, content, ItemDisplayContext.GUI, level, null, 0);
			ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) probe;
			ItemStackRenderState.LayerRenderState[] layers = state.shulkerInventory$getLayers();
			int count = state.shulkerInventory$getActiveLayerCount();
			for (int i = 0; i < count; i++) {
				if (((LayerRenderStateAccessor) layers[i]).shulkerInventory$getSpecialRenderer() != null) {
					return true;
				}
			}
			return false;
		});
	}

	// The box's GUI display reference {rotX, rotY, rotZ (degrees), uniform scale}, probed deterministically from the box
	// item's OWN model (vanilla or whatever a resource pack defines for THIS shulker colour), cached per Item. This is
	// the reference the held-orientation delta turns content FROM, and the size the per-context shrink factor divides by.
	// Probed on a fresh, marker-less stack of the same item so the content-layer mixin (which keys on the animation
	// marker) does nothing during the probe - no re-entrancy, no nesting. RAM-only memoisation of a deterministic model
	// property; nothing is written to disk.
	public static float[] boxGuiRef(ItemStack boxStack, @Nullable Level level) {
		return BOX_GUI_REF.computeIfAbsent(boxStack.getItem(), item -> {
			ItemStackRenderState probe = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver()
					.updateForTopItem(probe, new ItemStack(item), ItemDisplayContext.GUI, level, null, 0);
			ItemStackRenderState.LayerRenderState[] layers =
					((ItemStackRenderStateAccessor) probe).shulkerInventory$getLayers();
			ItemTransform it = ((LayerRenderStateAccessor) layers[0]).shulkerInventory$getItemTransform();
			Vector3fc r = it.rotation();
			return new float[] {r.x(), r.y(), r.z(), it.scale().x()};
		});
	}
}
