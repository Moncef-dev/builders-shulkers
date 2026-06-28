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

	@Override
	public void shulkerInventory$setPocketBuildContent(boolean value) {
		this.shulkerInventory$pocketBuildContent = value;
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
		this.shulkerInventory$localTransform = null;
	}

	// Apply the recreated localTransform right AFTER vanilla applies this layer's ItemTransform, matching 26.x order
	// (final = itemTransform then localTransform). mulPose updates both the pose and the normal matrix. No-op for any
	// untouched layer (null), so the box's own layers are unaffected.
	@Inject(method = "submit", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/renderer/block/model/ItemTransform;apply(ZLcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
			shift = At.Shift.AFTER))
	private void shulkerInventory$applyLocalTransform(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		if (this.shulkerInventory$localTransform != null) {
			poseStack.mulPose(this.shulkerInventory$localTransform);
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
