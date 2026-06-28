package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Around each animated GUI item draw, reads the animation marker off the render state and publishes its id so the
// shulker openness mixin can resolve the matching progress, then clears it afterwards. On 1.21.11 the atlas draw that
// runs the item's special model is GuiRenderer.renderItemToAtlas(TrackingItemStackRenderState, ...) (26.x had a
// separate GuiItemAtlas.drawToSlot taking an ItemStackRenderState); here the render state IS the tracking one already.
@Mixin(GuiRenderer.class)
public abstract class GuiItemAtlasMixin {
	@Inject(method = "renderItemToAtlas", at = @At("HEAD"))
	private void shulkerInventory$markIfOpen(TrackingItemStackRenderState tracking, PoseStack poseStack, int i, int j,
			int k, CallbackInfo ci) {
		Object identity = tracking.getModelIdentity();
		if (identity instanceof List<?> list) {
			for (Object element : list) {
				if (element instanceof ClientShulkerSession.AnimationMarker marker) {
					ClientShulkerSession.setCurrentAtlasRenderingId(marker.animationId());
					return;
				}
			}
		}
	}

	@Inject(method = "renderItemToAtlas", at = @At("RETURN"))
	private void shulkerInventory$unmark(TrackingItemStackRenderState tracking, PoseStack poseStack, int i, int j,
			int k, CallbackInfo ci) {
		ClientShulkerSession.setCurrentAtlasRenderingId(0L);
	}
}
