package io.github.moncefdev.shulkerinventory.client;

// How the selected Pocket-Build content renders, which picks its in-box composition path. Classified by how it
// DRAWS, not by item type (a sapling is a BlockItem yet flat). See PocketBuildContentLayerMixin (the classifier
// and consumer) and PocketBuildContentRender (the cached probes it reads). Lives outside the mixin package: the
// merged mixin code references it at runtime, and classes inside a registered mixin package must never be
// classloaded directly (the NeoForge loader enforces this strictly and crashes on it; fabric happens to tolerate
// it, so the stricter rule is the portable one).
public enum PocketBuildContentKind {
	// A flat sprite (tools, the shield, flat blocks like saplings): drawn as a layer, its own transform dropped and
	// re-centred + scaled on the box's real geometry.
	FLAT,
	// Any 3D block (a plain block, a custom-held block, a special block-entity renderer, or a natively iso-rotated
	// banner / shield / calibrated_sculk_sensor): the unified 3D path (GUI display + box-following delta + 0.6 shrink).
	BLOCK,
	// An oversized special block whose inventory iso normally comes from the oversized PIP our composition bypasses
	// (decorated_pot): the 3D path AND the standard block iso substituted onto each layer.
	BLOCK_OVERSIZED;

	public boolean isFlat() {
		return this == FLAT;
	}

	public boolean needsBlockIso() {
		return this == BLOCK_OVERSIZED;
	}
}
