package io.github.moncefdev.shulkerinventory.client;

// Duck interface implemented on ItemStackRenderState.LayerRenderState (via LayerRenderStateContentMixin): marks a layer
// as Pocket-Build CONTENT (a block or shulker drawn INSIDE the box) as opposed to the box's own layers. Lets the
// shulker lid openness/dissolve mixins keep a content shulker static while the outer box animates. Lives outside the
// mixin package because classes in the mixin package cannot be referenced directly.
public interface PocketBuildContentLayer {
	void shulkerInventory$setPocketBuildContent(boolean value);

	boolean shulkerInventory$isPocketBuildContent();

	// Marks a content layer whose model reports FLAT lighting, composed into the GUI slot: it renders standalone
	// under the ITEMS_FLAT light rig, but the slot lights the whole composite once with the box's ITEMS_3D rig,
	// so LayerRenderStateContentMixin applies the exact FLAT -> 3D rig correction to its lighting normals. Set at
	// composition time, GUI context only.
	void shulkerInventory$setPocketBuildFlatGuiLit(boolean value);
}
