package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Brackets the item shulker's special-model submit with a render-thread flag that says "this is our live Pocket-Build
// shulker". The actual lid dissolve lives on ShulkerBoxRenderer (shared with world block entities); this flag is how
// that mixin tells our held/GUI shulker apart from a placed shulker block, which must never dissolve. The animation is
// identified through the render side channel (the same one the openness override reads), and tagged as Pocket-Build
// when the mode opens it, so the lid dissolves for the whole open even on an empty selection (no content to draw).
@Mixin(ShulkerBoxSpecialRenderer.class)
public abstract class ShulkerBoxLidFadeGateMixin {
	@Inject(method = "submit", at = @At("HEAD"))
	private void shulkerInventory$markPocketBuild(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, boolean hasFoil, int outlineColor, CallbackInfo ci) {
		long id = ClientShulkerSession.currentRenderingId();
		boolean content = ClientShulkerSession.isRenderingContentLayer();
		// Suppress only the DISSOLVE for a content shulker (keep its lid opaque, so it never collapses to base-only). Its
		// lid OPENNESS is left to animate with the box: for a same-colour nested shulker the lid position is resolved at
		// draw from the shared special-renderer instance, so forcing the content closed (openness 0) would drag the box's
		// own lid to 0 too. The dissolve, by contrast, is decided per-submit (pbLid), so it can be suppressed safely.
		ClientShulkerSession.setPocketBuildLidRendering(id != 0L && ClientShulkerSession.isPocketBuild(id) && !content);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void shulkerInventory$clearPocketBuild(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, boolean hasFoil, int outlineColor, CallbackInfo ci) {
		ClientShulkerSession.setPocketBuildLidRendering(false);
	}
}
