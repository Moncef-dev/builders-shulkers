package io.github.moncefdev.shulkerinventory.client.mixin;

import java.util.function.Supplier;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Reaches a layer's local-space extents (the geometry's bounding-box corner points), so the work-in-progress non-block
// (2D) Pocket-Build content can pivot its shrink about the item's own centre. See PocketBuildContentLayerMixin.
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("extents")
	Supplier<Vector3fc[]> shulkerInventory$getExtents();

	// The layer's baked display transform. Needed to express the box's geometry in the same (post-transform) space as
	// the content, so the content can be centred on the box's real visual centre.
	@Accessor("itemTransform")
	ItemTransform shulkerInventory$getItemTransform();

	@Accessor("localTransform")
	org.joml.Matrix4f shulkerInventory$getLocalTransform();
}
