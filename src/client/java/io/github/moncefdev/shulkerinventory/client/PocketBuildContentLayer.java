package io.github.moncefdev.shulkerinventory.client;

// Duck interface implemented on ItemStackRenderState.LayerRenderState (via LayerRenderStateContentMixin): marks a layer
// as Pocket-Build CONTENT (a block or shulker drawn INSIDE the box) as opposed to the box's own layers. Lets the
// shulker lid openness/dissolve mixins keep a content shulker static while the outer box animates. Lives outside the
// mixin package because classes in the mixin package cannot be referenced directly.
public interface PocketBuildContentLayer {
	void shulkerInventory$setPocketBuildContent(boolean value);

	boolean shulkerInventory$isPocketBuildContent();
}
