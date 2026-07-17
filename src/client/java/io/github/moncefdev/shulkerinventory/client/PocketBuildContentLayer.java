package io.github.moncefdev.shulkerinventory.client;

// Duck interface implemented on ItemStackRenderState.LayerRenderState (via LayerRenderStateContentMixin): marks a layer
// as Pocket-Build CONTENT (a block or shulker drawn INSIDE the box) as opposed to the box's own layers. Lets the
// shulker lid openness/dissolve mixins keep a content shulker static while the outer box animates. Lives outside the
// mixin package because classes in the mixin package cannot be referenced directly.
public interface PocketBuildContentLayer {
	void shulkerInventory$setPocketBuildContent(boolean value);

	boolean shulkerInventory$isPocketBuildContent();

	// GUI lighting treatment for a content layer whose model reports FLAT lighting (applied by
	// LayerRenderStateContentMixin). Set at composition time, GUI context only.
	enum FlatGuiLighting {
		// Not flat-reporting content (or not the GUI context): plain pre-localTransform normals.
		NONE,
		// A true 2D sprite (sword, tool, sapling): block-top brightness treatment.
		SPRITE,
		// Flat-lit 3D geometry (shield, banners, calibrated_sculk_sensor, decorated_pot): rig-to-rig correction.
		FLAT_LIT_3D
	}

	void shulkerInventory$setPocketBuildFlatGuiLighting(FlatGuiLighting mode);
}
