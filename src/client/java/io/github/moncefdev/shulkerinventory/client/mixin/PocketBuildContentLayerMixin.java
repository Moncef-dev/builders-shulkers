package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Composes the Pocket-Build selected content INTO the shulker's own item render state, the vanilla bundle technique: the
// content's layers are appended to the SAME render state, resolved for the SAME display context, then shrunk to sit in
// the box. One render state means the content gets its native per-context display transform once (correct 3D orientation
// in slot, first person and third person) and is occluded by the box geometry through the depth test. BLOCK content only:
// a block reads correctly as a small 3D block in every context. Non-block (2D) items are NOT composed here: their
// per-context transforms overflow the box and their GUI transform cannot be oriented or lit correctly from within a
// world render, so drawing them like their inventory slot is deferred (see TECHNICAL.md). Gated to the local player's
// live Pocket-Build shulker.
@Mixin(ItemModelResolver.class)
public abstract class PocketBuildContentLayerMixin {
	private static final float CONTENT_SCALE = 0.6f;
	// The content's local space spans [0,1] per block unit and fills the box at scale 1, so the box centre is 0.5.
	private static final float BOX_CENTER = 0.5f;

	@Inject(method = "appendItemLayers", at = @At("TAIL"))
	private void shulkerInventory$appendPocketBuildContent(ItemStackRenderState output, ItemStack item,
			ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		Long id = ShulkerAnimationMarker.get(item);
		if (id == null) {
			return;
		}
		ItemStack content = ClientShulkerSession.getPocketBuildContent(id);
		// Blocks only here; non-block (2D) items are drawn by PocketBuildItemRenderer.
		if (content.isEmpty() || !(content.getItem() instanceof BlockItem)) {
			return;
		}
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
		int before = state.shulkerInventory$getActiveLayerCount();
		// Append the content's own layers, resolved for the same display context, into this render state (like the
		// bundle's selected item). Recurses into appendItemLayers, but the content is not a Pocket-Build shulker so it
		// no-ops there.
		((ItemModelResolver) (Object) this).appendItemLayers(output, content, displayContext, level, owner, seed);
		int after = state.shulkerInventory$getActiveLayerCount();
		// Scale 0.6 about the FIXED box centre (0.5): a block's model space IS the box's [0,1], so this preserves its
		// natural place (a full block centred, a bottom slab in the lower half, ...).
		Matrix4f localTransform = new Matrix4f()
				.translate(BOX_CENTER, BOX_CENTER, BOX_CENTER)
				.scale(CONTENT_SCALE)
				.translate(-BOX_CENTER, -BOX_CENTER, -BOX_CENTER);
		ItemStackRenderState.LayerRenderState[] layers = state.shulkerInventory$getLayers();
		for (int i = before; i < after; i++) {
			layers[i].setLocalTransform(localTransform);
		}
	}
}
