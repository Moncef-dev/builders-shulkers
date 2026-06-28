package io.github.moncefdev.shulkerinventory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Dissolves the lid of the live Pocket-Build shulker as it opens, so it stops covering the content composed inside the
// box. A true translucent fade cannot be ordered correctly (content is in the item pass, the lid in the model-translucent
// pass, composited in a context-dependent order we do not control). Instead the lid uses a vanilla alpha-dissolve render
// type that discards fragments below an alpha threshold: it writes depth and never blends, so it is order-INDEPENDENT
// (the opaque content shows through the discarded texels in every context, and nothing behind is masked through the
// holes). On 1.21.11 that render type is dragonExplosionAlpha (the EnderDragon death effect), driven by the tint alpha
// exactly like vanilla drives it (ARGB.white(deathTime/200)); 26.x instead has entityCutoutDissolve with a separate mask
// texture. The tint alpha here is (1 - progress), so the lid dissolves smoothly to nothing. The base is always submitted
// opaque, separately. setupAnim has already run, so both parts are positioned for the current openness. Untouched for any
// other shulker (placed blocks, non-Pocket-Build animations), which take the original single submit.
//
// 1.21.11 deltas vs 26.x: ShulkerBoxRenderer.submit carries the box orientation (Direction) and a Material (26.x dropped
// the Direction and uses a SpriteId); submitModel/submitModelPart take a RenderType + TextureAtlasSprite (the wrapped
// submitModel already hands us the resolved sprite, so no SpriteGetter lookup) and submitModelPart takes two extra
// booleans after the sprite.
@Mixin(ShulkerBoxRenderer.class)
public abstract class ShulkerBoxLidFadeMixin {
	// Above this openness (fully open) the lid is fully dissolved anyway, so we skip submitting it.
	private static final float LID_HIDE_OPENNESS = 0.999f;

	@WrapOperation(
			method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/core/Direction;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/Material;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void shulkerInventory$dissolveLid(SubmitNodeCollector collector, Model<?> model, Object state,
			PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords, int tintedColor,
			TextureAtlasSprite atlasSprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
			Operation<Void> original) {
		// Draw the lid normally (opaque, lifts or stays at its resting state) when it is not our Pocket-Build lid, or when
		// the lid effect is NONE. DISSOLVE and DISAPPEAR both split base + lid below; neither ever uses a translucent pass.
		ClientConfig.LidEffect effect = ClientConfig.get().lidEffect;
		if (!ClientShulkerSession.isPocketBuildLidRendering() || effect == ClientConfig.LidEffect.NONE) {
			original.call(collector, model, state, poseStack, renderType, lightCoords, overlayCoords, tintedColor,
					atlasSprite, outlineColor, crumblingOverlay);
			return;
		}
		ModelPart root = model.root();
		ModelPart base = root.getChild("base");
		ModelPart lid = root.getChild("lid");
		// The effect tracks the animation's RAW progress (published by the openness override), not the openness arg, which
		// may be frozen at the resting lid state when the animation is off - so the lid clears without moving.
		float progress = ClientShulkerSession.currentDissolveProgress();
		RenderType cutout = RenderTypes.entityCutout(atlasSprite.atlasLocation());
		// Base: always opaque cutout, original tint.
		collector.submitModelPart(base, poseStack, cutout, lightCoords, overlayCoords, atlasSprite, false, false,
				tintedColor, crumblingOverlay, outlineColor);
		if (effect == ClientConfig.LidEffect.DISAPPEAR) {
			// Lid present (opaque, original tint - identical to a vanilla closed lid) only while CLOSING; while opening or
			// open it is simply not submitted. Keyed on the lifecycle STATUS, not the progress, so it vanishes the instant
			// opening starts AND reappears the instant closing starts (instant both ways, decoupled from the lift). A
			// draw-call on/off, never a fade, so it cannot mask anything behind it.
			if (ClientShulkerSession.currentRenderStatus() == ClientShulkerSession.AnimationStatus.CLOSING) {
				collector.submitModelPart(lid, poseStack, renderType, lightCoords, overlayCoords, atlasSprite, false, false,
						tintedColor, crumblingOverlay, outlineColor);
			}
		} else {
			// DISSOLVE: order-independent alpha-dissolve, threshold driven by tint alpha = 1 - progress, until fully gone.
			if (progress < LID_HIDE_OPENNESS) {
				RenderType dissolve = RenderTypes.dragonExplosionAlpha(atlasSprite.atlasLocation());
				collector.submitModelPart(lid, poseStack, dissolve, lightCoords, overlayCoords, atlasSprite, false, false,
						ARGB.white(1.0f - progress), crumblingOverlay, outlineColor);
			}
		}
	}
}
