package io.github.moncefdev.shulkerinventory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import io.github.moncefdev.shulkerinventory.client.DissolveMask;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Dissolves the lid of the live Pocket-Build shulker as it opens, so it stops covering the content composed inside the
// box. A true translucent fade cannot be ordered correctly (content is in the item pass, the lid in the model-translucent
// pass, composited in a context-dependent order we do not control). Instead the lid uses the vanilla dissolve render type
// (the EnderDragon death effect): a CUTOUT that discards fragments where the tint alpha falls below a per-texel mask
// threshold. Cutout writes depth and never blends, so it is order-INDEPENDENT: the opaque content shows through the
// discarded texels in every context, and nothing behind (water, clouds) is masked through the holes. The tint alpha is
// (1 - openness), so the lid dissolves smoothly to nothing. The base is always submitted opaque, separately. setupAnim
// has already run, so both parts are positioned for the current openness. Untouched for any other shulker (placed blocks,
// non-Pocket-Build animations), which take the original single submit.
@Mixin(ShulkerBoxRenderer.class)
public abstract class ShulkerBoxLidFadeMixin {
	// Above this openness (fully open) the lid is fully dissolved anyway, so we skip submitting it.
	private static final float LID_HIDE_OPENNESS = 0.999f;

	@WrapOperation(
			method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIFLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/sprite/SpriteId;I)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;IIILnet/minecraft/client/resources/model/sprite/SpriteId;Lnet/minecraft/client/resources/model/sprite/SpriteGetter;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"))
	private void shulkerInventory$dissolveLid(SubmitNodeCollector collector, Model<?> model, Object state,
			PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor, SpriteId sprite, SpriteGetter sprites,
			int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay, Operation<Void> original) {
		if (!ClientShulkerSession.isPocketBuildLidRendering()) {
			original.call(collector, model, state, poseStack, lightCoords, overlayCoords, tintedColor, sprite, sprites,
					outlineColor, crumblingOverlay);
			return;
		}
		ModelPart root = model.root();
		ModelPart base = root.getChild("base");
		ModelPart lid = root.getChild("lid");
		float openness = state instanceof Float progress ? progress : 0.0f;
		TextureAtlasSprite atlasSprite = sprites.get(sprite);
		// Base: always opaque cutout, original tint.
		collector.submitModelPart(base, poseStack, sprite.renderType(RenderTypes::entityCutout), lightCoords,
				overlayCoords, atlasSprite, false, false, tintedColor, crumblingOverlay, outlineColor);
		// Lid: dissolve (order-independent cutout), threshold driven by tint alpha = 1 - openness, until fully gone.
		if (openness < LID_HIDE_OPENNESS) {
			RenderType dissolve = sprite.renderType(
					texture -> RenderTypes.entityCutoutDissolve(texture, DissolveMask.identifier()));
			collector.submitModelPart(lid, poseStack, dissolve, lightCoords, overlayCoords, atlasSprite, false, false,
					ARGB.white(1.0f - openness), crumblingOverlay, outlineColor);
		}
	}
}
