package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Overrides the shulker lid openness (0..1) with our interpolated animation progress when the stack being rendered is
// the local player's live Pocket-Build box; otherwise returns the original vanilla value so other shulkers (placed
// blocks, or a shulker drawn INSIDE the box as content) render unchanged.
@Mixin(ShulkerBoxSpecialRenderer.class)
public abstract class ShulkerBoxOpennessMixin {
	@ModifyArg(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/blockentity/ShulkerBoxRenderer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIFLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/sprite/SpriteId;I)V"),
			index = 4)
	private float shulkerInventory$overrideOpenness(float original) {
		long id = ClientShulkerSession.getCurrentAtlasRenderingId();
		if (id == 0L) {
			id = ClientShulkerSession.getCurrentItemEntityAnimationId();
		}
		boolean content = ClientShulkerSession.isRenderingContentLayer();
		float ret = original;
		// Animate the lid for the OUTER box only; a content shulker (content-layer flag) stays closed (openness 0). This
		// is safe even for a same-colour nested shulker because the content uses a SEPARATE model instance (swapped on in
		// PocketBuildContentLayerMixin), so its closed lid no longer drags the box's shared model to closed.
		if (id != 0L && !content) {
			Minecraft mc = Minecraft.getInstance();
			ret = ClientShulkerSession.getInterpolatedProgress(id,
					mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		return ret;
	}
}
