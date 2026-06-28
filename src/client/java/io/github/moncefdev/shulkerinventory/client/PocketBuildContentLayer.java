package io.github.moncefdev.shulkerinventory.client;

// Duck interface implemented on ItemStackRenderState.LayerRenderState (via LayerRenderStateContentMixin): marks a layer
// as Pocket-Build CONTENT (a block or shulker drawn INSIDE the box) as opposed to the box's own layers. Lets the
// shulker lid openness/dissolve mixins keep a content shulker static while the outer box animates. Lives outside the
// mixin package because classes in the mixin package cannot be referenced directly.
public interface PocketBuildContentLayer {
	void shulkerInventory$setPocketBuildContent(boolean value);

	boolean shulkerInventory$isPocketBuildContent();

	// A per-layer local transform (an arbitrary 4x4 matrix), applied to the pose AFTER the layer's own ItemTransform when
	// the layer is submitted. 1.21.11's LayerRenderState has no such field (it carries only an ItemTransform = TRS); 26.x
	// added one, and the in-box content composition (PocketBuildContentLayerMixin) needs an arbitrary matrix for the
	// shrink / centre / box-fit math. So we recreate it here as a mixin-held field. null means "no local transform" -
	// the layer then behaves exactly like vanilla.
	void shulkerInventory$setLocalTransform(org.joml.Matrix4f matrix);

	// The layer's local transform, or null if none was set (treated as identity). Null is the vanilla default, so reading
	// it on a box's own (untouched) layer returns null - callers must treat that as identity.
	org.joml.Matrix4f shulkerInventory$getLocalTransform();
}
