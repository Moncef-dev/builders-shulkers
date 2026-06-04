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

	@Inject(method = "submit", at = @At("HEAD"))
	private void shulkerInventory$markContent(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		ClientShulkerSession.setRenderingContentLayer(this.shulkerInventory$pocketBuildContent);
	}

	@Inject(method = "submit", at = @At("RETURN"))
	private void shulkerInventory$unmarkContent(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
			int overlayCoords, int outlineColor, CallbackInfo ci) {
		ClientShulkerSession.setRenderingContentLayer(false);
	}
}
