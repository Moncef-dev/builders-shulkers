package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// Around each animated GUI item draw, reads the animation marker off the render state and publishes its id so
// the shulker openness mixin can resolve the matching progress, then clears it afterwards.
@Mixin(GuiItemAtlas.class)
public abstract class GuiItemAtlasMixin {
	@Inject(method = "drawToSlot", at = @At("HEAD"))
	private void shulkerInventory$markIfOpen(int x, int y, boolean animated, ItemStackRenderState state, CallbackInfo ci) {
		if (state instanceof TrackingItemStackRenderState tracking) {
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
	}

	@Inject(method = "drawToSlot", at = @At("RETURN"))
	private void shulkerInventory$unmark(int x, int y, boolean animated, ItemStackRenderState state, CallbackInfo ci) {
		ClientShulkerSession.setCurrentAtlasRenderingId(0L);
	}
}
