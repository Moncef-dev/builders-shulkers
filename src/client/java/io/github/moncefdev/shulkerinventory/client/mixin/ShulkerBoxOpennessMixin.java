package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Overrides the shulker lid openness (0..1) with our interpolated animation progress when the stack being
// rendered is one of ours; otherwise returns the original vanilla value so other shulkers render unchanged.
@Mixin(ShulkerBoxSpecialRenderer.class)
public abstract class ShulkerBoxOpennessMixin {
	@ModifyArg(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/blockentity/ShulkerBoxRenderer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIFLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/sprite/SpriteId;I)V"),
			index = 4)
	private float shulkerInventory$overrideOpenness(float original) {
		Minecraft mc = Minecraft.getInstance();
		long atlasId = ClientShulkerSession.getCurrentAtlasRenderingId();
		if (atlasId != 0L) {
			return ClientShulkerSession.getInterpolatedProgress(atlasId,
					mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		long itemEntityId = ClientShulkerSession.getCurrentItemEntityAnimationId();
		if (itemEntityId != 0L) {
			return ClientShulkerSession.getInterpolatedProgress(itemEntityId,
					mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		return original;
	}
}
