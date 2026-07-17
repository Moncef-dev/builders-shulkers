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
	private PocketBuildContentLayer.FlatGuiLighting shulkerInventory$pocketBuildFlatGuiLighting =
			PocketBuildContentLayer.FlatGuiLighting.NONE;

	// Scratch for the normal-matrix snapshot below. Written by snapshotNormals and consumed by restoreNormals
	// within one applyTransform call (the two injections always pair up inside that single leaf method), on the
	// render thread only, like every layer submit - a shared static is safe under exactly those two invariants.
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$normalSnapshot = new org.joml.Matrix3f();

	// The standard block GUI display rotation (degrees), and the tip taking a sprite's front normal (+Z) up to
	// where a block's TOP face normal (+Y) sits. RE-VERIFY the iso at each update against
	// assets/minecraft/models/block/block.json ("gui" display rotation).
	@Unique
	private static final float shulkerInventory$BLOCK_GUI_ISO_X = 30.0f;
	@Unique
	private static final float shulkerInventory$BLOCK_GUI_ISO_Y = 225.0f;
	@Unique
	private static final float shulkerInventory$FRONT_TO_TOP_TIP_X = -90.0f;

	// Rotates a normal so its diffuse response under the ITEMS_3D light rig equals its response under the
	// ITEMS_FLAT rig: both rigs are orthogonal transforms of the same two-light base (built in the Lighting
	// constructor), so CORR = M_3D * M_FLAT^-1 maps one response onto the other exactly, for any geometry.
	// RE-VERIFY the two rig transforms at each update against the Lighting constructor (values below are the
	// 26.x ones: FLAT = rotationY(-0.3926991).rotateX(2.3561945); 3D = scaling(-1,1,1)
	// .rotateYXZ(1.0821041, 3.2375858, 0).rotateYXZ(-0.3926991, 2.3561945, 0)).
	@Unique
	private static final org.joml.Matrix3f shulkerInventory$FLAT_TO_3D_LIGHT = new org.joml.Matrix3f(
			new org.joml.Matrix4f().scaling(-1.0f, 1.0f, 1.0f)
					.rotateYXZ(1.0821041f, 3.2375858f, 0.0f)
					.rotateYXZ(-0.3926991f, 2.3561945f, 0.0f))
			.mul(new org.joml.Matrix3f(new org.joml.Matrix4f().rotationY(-0.3926991f).rotateX(2.3561945f)).transpose());

	@Override
	public void shulkerInventory$setPocketBuildContent(boolean value) {
		this.shulkerInventory$pocketBuildContent = value;
	}

	@Override
	public boolean shulkerInventory$isPocketBuildContent() {
		return this.shulkerInventory$pocketBuildContent;
	}

	@Override
	public void shulkerInventory$setPocketBuildFlatGuiLighting(PocketBuildContentLayer.FlatGuiLighting mode) {
		this.shulkerInventory$pocketBuildFlatGuiLighting = mode;
	}

	@Inject(method = "clear", at = @At("HEAD"))
	private void shulkerInventory$resetContentFlag(CallbackInfo ci) {
		this.shulkerInventory$pocketBuildContent = false;
		this.shulkerInventory$pocketBuildFlatGuiLighting = PocketBuildContentLayer.FlatGuiLighting.NONE;
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
		// Content that REPORTS flat lighting renders standalone under the ITEMS_FLAT rig; the slot lights the whole
		// composite once with the box's ITEMS_3D rig, leaving that content dimmer. Two empirically-validated
		// corrections, each on its family (a single exact rig-to-rig correction SHOULD cover both, but in-game the
		// sprite one only matches its standalone look with the block-top form - the standalone flat bake does not
		// share the composite's base pose, so "same response, our base" is not "same pixels"):
		// - a true 2D sprite: point its front normal (+Z) where a standard block's TOP face lands (base pose, block
		//   GUI iso 30/225, +Z tipped up to +Y); its diffuse clamps to full brightness = the standalone sprite look.
		// - flat-lit 3D geometry (shield, banners, calibrated_sculk_sensor, decorated_pot): rotate the normals by
		//   the ITEMS_FLAT -> ITEMS_3D rig correction, which maps the whole geometry's flat-lit shading onto the 3D
		//   rig exactly.
		if (this.shulkerInventory$pocketBuildFlatGuiLighting == PocketBuildContentLayer.FlatGuiLighting.SPRITE) {
			pose.normal().set(shulkerInventory$normalSnapshot)
					.rotateXYZ((float) Math.toRadians(shulkerInventory$BLOCK_GUI_ISO_X),
							(float) Math.toRadians(shulkerInventory$BLOCK_GUI_ISO_Y), 0.0f)
					.rotateX((float) Math.toRadians(shulkerInventory$FRONT_TO_TOP_TIP_X));
		} else if (this.shulkerInventory$pocketBuildFlatGuiLighting == PocketBuildContentLayer.FlatGuiLighting.FLAT_LIT_3D) {
			pose.normal().set(shulkerInventory$FLAT_TO_3D_LIGHT).mul(shulkerInventory$normalSnapshot);
		} else if (this.shulkerInventory$pocketBuildContent) {
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
