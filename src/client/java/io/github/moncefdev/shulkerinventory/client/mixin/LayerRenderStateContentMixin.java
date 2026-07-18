package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import io.github.moncefdev.shulkerinventory.client.PocketBuildContentLayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.joml.Matrix4f;
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

	// Recreates 26.x's per-layer localTransform (absent in 1.21.11, whose LayerRenderState carries only an ItemTransform).
	// null = none (vanilla behaviour); applied to the pose after the layer's ItemTransform in submit (below).
	@Unique
	private Matrix4f shulkerInventory$localTransform;

	@Unique
	private boolean shulkerInventory$pocketBuildFlatGuiLit;

	@Override
	public void shulkerInventory$setPocketBuildContent(boolean value) {
		this.shulkerInventory$pocketBuildContent = value;
	}

	@Override
	public void shulkerInventory$setPocketBuildFlatGuiLit(boolean value) {
		this.shulkerInventory$pocketBuildFlatGuiLit = value;
	}

	@Override
	public boolean shulkerInventory$isPocketBuildContent() {
		return this.shulkerInventory$pocketBuildContent;
	}

	@Override
	public void shulkerInventory$setLocalTransform(Matrix4f matrix) {
		this.shulkerInventory$localTransform = matrix;
	}

	@Override
	public Matrix4f shulkerInventory$getLocalTransform() {
		return this.shulkerInventory$localTransform;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void shulkerInventory$resetContentFlag(CallbackInfo ci) {
		// Render states are pooled and reused across frames, so clear both side fields back to their vanilla default.
		this.shulkerInventory$pocketBuildContent = false;
		this.shulkerInventory$pocketBuildFlatGuiLit = false;
		this.shulkerInventory$localTransform = null;
	}

	// Scratches for the normal-matrix handling below: written and consumed within one applyLocalTransform call, on
	// the render thread only, like every layer submit.
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$normalSnapshot = new org.joml.Matrix3f();
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$localDirection = new org.joml.Matrix3f();
	@Unique
	private static final org.joml.Vector3f shulkerInventory$columnScratch = new org.joml.Vector3f();

	// Apply the recreated localTransform right AFTER vanilla applies this layer's ItemTransform, matching 26.x order
	// (final = itemTransform then localTransform). mulPose updates both the pose and the normal matrix. No-op for any
	// untouched layer (null), so the box's own layers are unaffected.
	//
	// For CONTENT layers, keep the lighting normals identical to the same item rendered standalone (the 26.x
	// lighting fix, ported to this branch's own call site): mulPose folds the localTransform's scale into the
	// pose's NORMAL matrix (a full inverse-transpose recompute), and on the NeoForge render path the quads then
	// shade as if unlit (dark, no per-face contrast; fabric is spared by Indigo redrawing item quads through its
	// own pipeline). So the normal matrix is snapshotted before the fold and restored after, composed with only
	// the localTransform's DIRECTION part (columns normalized: rotation and mirror kept, scale stripped -
	// identity for our translate + uniform shrink matrices). Positions keep the full chain; only the lighting
	// normals see the vanilla-managed transform.
	@Inject(method = "submit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/model/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
			shift = At.Shift.AFTER))
	private void shulkerInventory$applyLocalTransform(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		if (this.shulkerInventory$localTransform == null) {
			return;
		}
		boolean content = this.shulkerInventory$pocketBuildContent;
		if (content) {
			shulkerInventory$normalSnapshot.set(poseStack.last().normal());
		}
		poseStack.mulPose(this.shulkerInventory$localTransform);
		if (content) {
			shulkerInventory$localDirection.set(this.shulkerInventory$localTransform);
			for (int c = 0; c < 3; c++) {
				shulkerInventory$localDirection.getColumn(c, shulkerInventory$columnScratch);
				float len = shulkerInventory$columnScratch.length();
				if (len > 1.0e-6f) {
					shulkerInventory$localDirection.setColumn(c, shulkerInventory$columnScratch.div(len));
				}
			}
			if (this.shulkerInventory$pocketBuildFlatGuiLit) {
				// Content that REPORTS flat lighting (true 2D sprites and flat-lit 3D geometry alike: tools,
				// saplings, the shield, banners, calibrated_sculk_sensor, decorated_pot) renders standalone under
				// the ITEMS_FLAT rig; the slot lights the whole composite once with the box's ITEMS_3D rig,
				// leaving that content dimmer. Rotate its normals by the exact ITEMS_FLAT -> ITEMS_3D rig
				// correction (derived at runtime from vanilla's own rig values, see GuiLightRigs), which maps the
				// whole geometry's flat-lit shading onto the 3D rig.
				poseStack.last().normal()
						.set(io.github.moncefdev.shulkerinventory.client.GuiLightRigs.flatTo3d())
						.mul(shulkerInventory$normalSnapshot)
						.mul(shulkerInventory$localDirection);
			} else {
				poseStack.last().normal().set(shulkerInventory$normalSnapshot).mul(shulkerInventory$localDirection);
			}
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
