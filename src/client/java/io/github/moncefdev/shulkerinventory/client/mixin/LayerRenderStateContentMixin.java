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

	// Scratch for the normal-matrix snapshot below. Render-thread only, like every layer submit.
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$normalSnapshot = new org.joml.Matrix3f();

	@Override
	public void shulkerInventory$setPocketBuildContent(boolean value) {
		this.shulkerInventory$pocketBuildContent = value;
	}

	@Override
	public boolean shulkerInventory$isPocketBuildContent() {
		return this.shulkerInventory$pocketBuildContent;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void shulkerInventory$resetContentFlag(CallbackInfo ci) {
		this.shulkerInventory$pocketBuildContent = false;
	}

	// Keep a content layer's lighting normals identical to the same item rendered standalone. Our content layers
	// carry the in-box shrink in their localTransform; vanilla's Pose.mulPose folds that matrix into the pose's
	// NORMAL matrix too (a full inverse-transpose recompute), and on the NeoForge render path the quads then shade
	// as if unlit (dark, no per-face contrast) - bisected in game: with the localTransform gone the lighting is
	// exactly the standalone slot look, with it the content goes dark. Our localTransform is translate+uniform
	// scale only (never a rotation), so the normal DIRECTIONS gained nothing from it: snapshot the normal matrix
	// right before vanilla folds the localTransform in, and restore it right after. Positions keep the full chain
	// (geometry unchanged, everywhere); only the lighting normals see the vanilla-managed transform, on both
	// loaders and in every context. If a content localTransform ever gains a rotation, this must rotate the
	// snapshot accordingly.
	@Inject(method = "applyTransform",
			at = @At(value = "INVOKE",
					target = "Lcom/mojang/blaze3d/vertex/PoseStack$Pose;mulPose(Lorg/joml/Matrix4fc;)V"))
	private void shulkerInventory$snapshotNormals(PoseStack.Pose pose, CallbackInfo ci) {
		if (this.shulkerInventory$pocketBuildContent) {
			shulkerInventory$normalSnapshot.set(pose.normal());
		}
	}

	@Inject(method = "applyTransform", at = @At("TAIL"))
	private void shulkerInventory$restoreNormals(PoseStack.Pose pose, CallbackInfo ci) {
		if (this.shulkerInventory$pocketBuildContent) {
			pose.normal().set(shulkerInventory$normalSnapshot);
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
