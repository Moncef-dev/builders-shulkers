package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import io.github.moncefdev.shulkerinventory.client.PocketBuildContentLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Carries a per-layer "this is Pocket-Build content" flag and publishes it to the render side channel for the duration
// of the layer's submit, so the shulker openness/dissolve mixins can keep a content shulker static while the outer box
// animates. Deterministic and independent of draw order or shulker colour - unlike the old "first shulker drawn wins"
// scheme, which broke when the box and a same-colour nested shulker batched together and the render reordered them.
// The flag is reset on clear() because render states are pooled and reused across frames.
@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class LayerRenderStateContentMixin implements PocketBuildContentLayer {
	@Unique
	private boolean shulkerInventory$pocketBuildContent;

	@Unique
	private boolean shulkerInventory$pocketBuildFlatGuiLit;

	// Scratches for the normal-matrix snapshots below. Written by the snapshot injections and consumed by
	// restoreNormals within one applyTransform call (the injections always pair up inside that single leaf
	// method), on the render thread only, like every layer submit - shared statics are safe under exactly those
	// two invariants.
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$normalSnapshot = new org.joml.Matrix3f();
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$localDirection = new org.joml.Matrix3f();
	@Unique
	private static final org.joml.Vector3f shulkerInventory$columnScratch = new org.joml.Vector3f();

	@Override
	public void shulkerInventory$setPocketBuildContent(boolean value) {
		this.shulkerInventory$pocketBuildContent = value;
	}

	@Override
	public boolean shulkerInventory$isPocketBuildContent() {
		return this.shulkerInventory$pocketBuildContent;
	}

	@Override
	public void shulkerInventory$setPocketBuildFlatGuiLit(boolean value) {
		this.shulkerInventory$pocketBuildFlatGuiLit = value;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void shulkerInventory$resetContentFlag(CallbackInfo ci) {
		this.shulkerInventory$pocketBuildContent = false;
		this.shulkerInventory$pocketBuildFlatGuiLit = false;
	}

	// Keep a content layer's lighting normals identical to the same item rendered standalone: vanilla's mulPose
	// folds the localTransform (our in-box shrink included) into the pose's NORMAL matrix, which makes quads
	// shade as if unlit on the NeoForge render path. Snapshot the normal matrix before the fold, restore it after
	// composed with only the localTransform's DIRECTION part. Positions keep the full chain; only the lighting
	// normals are steered. TECHNICAL.md section 8 (in-box lighting).
	@Inject(method = "applyTransform",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;mulPose(Lorg/joml/Matrix4fc;)V"))
	private void shulkerInventory$snapshotNormals(PoseStack.Pose pose, CallbackInfo ci) {
		if (this.shulkerInventory$pocketBuildContent) {
			shulkerInventory$normalSnapshot.set(pose.normal());
		}
	}

	// The DIRECTION part (rotation and mirror; scale and translation stripped) of this layer's localTransform,
	// its columns normalized. A special model's localTransform carries its baked transformation - the shield and
	// banner entity models are built Y-down and MIRRORED there - and standalone rendering folds that mirror into
	// the lighting normals, so the restore keeps it. Identity for plain blocks and sprites.
	// TECHNICAL.md section 8 (in-box lighting).
	@Unique
	private org.joml.Matrix3f shulkerInventory$localDirectionPart() {
		org.joml.Matrix4f local = ((LayerRenderStateAccessor) (Object) this).shulkerInventory$getLocalTransform();
		shulkerInventory$localDirection.set(local);
		for (int c = 0; c < 3; c++) {
			shulkerInventory$localDirection.getColumn(c, shulkerInventory$columnScratch);
			float len = shulkerInventory$columnScratch.length();
			if (len > 1.0e-6f) {
				shulkerInventory$localDirection.setColumn(c, shulkerInventory$columnScratch.div(len));
			}
		}
		return shulkerInventory$localDirection;
	}

	@Inject(method = "applyTransform", at = @At("TAIL"))
	private void shulkerInventory$restoreNormals(PoseStack.Pose pose, CallbackInfo ci) {
		// Content that REPORTS flat lighting (true 2D sprites and flat-lit 3D geometry alike: tools, saplings,
		// the shield, banners, calibrated_sculk_sensor, decorated_pot) renders standalone under the ITEMS_FLAT
		// rig; the slot lights the whole composite once with the box's ITEMS_3D rig, leaving that content dimmer.
		// Rotate its normals by the exact ITEMS_FLAT -> ITEMS_3D rig correction (derived at runtime from
		// vanilla's own rig values, see GuiLightRigs), which maps the whole geometry's flat-lit shading onto the
		// 3D rig.
		if (this.shulkerInventory$pocketBuildFlatGuiLit) {
			pose.normal().set(io.github.moncefdev.shulkerinventory.client.GuiLightRigs.flatTo3d())
					.mul(shulkerInventory$normalSnapshot)
					.mul(shulkerInventory$localDirectionPart());
		} else if (this.shulkerInventory$pocketBuildContent) {
			pose.normal().set(shulkerInventory$normalSnapshot).mul(shulkerInventory$localDirectionPart());
		}
	}

	@Inject(method = "submit", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$markContent(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		// Skip drawing Pocket-Build content while the lid fully covers it (closed position AND opaque): otherwise the
		// content keeps rendering through the close drain and pokes out of a visually-closed box. Reset the side-channel
		// flag first since the RETURN unmark does not fire on a cancelled call.
		if (this.shulkerInventory$pocketBuildContent && ClientShulkerSession.contentCoveredByClosedLid()) {
			ClientShulkerSession.setRenderingContentLayer(false);
			ci.cancel();
			return;
		}
		ClientShulkerSession.setRenderingContentLayer(this.shulkerInventory$pocketBuildContent);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void shulkerInventory$unmarkContent(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		ClientShulkerSession.setRenderingContentLayer(false);
	}
}
