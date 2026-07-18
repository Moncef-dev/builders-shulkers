package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.platform.Lighting;
import io.github.moncefdev.shulkerinventory.client.GuiLightRigs;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Captures the final transformed light directions vanilla computes for the ITEMS_FLAT and ITEMS_3D GUI rigs, at
// the single point every rig update funnels through (the Lighting constructor transforms each rig's base lights
// and hands them to updateBuffer). GuiLightRigs derives the flat -> 3D rig correction from the captured pairs,
// so the in-box content lighting follows vanilla's actual rig values instead of a hardcoded copy of them.
@Mixin(Lighting.class)
public abstract class LightingRigCaptureMixin {
	// updateBuffer takes concrete Vector3f parameters on the whole 1.21.x line (26.x moved them to the Vector3fc
	// interface), so the handler matches them directly.
	@Inject(method = "updateBuffer", at = @At("HEAD"))
	private void shulkerInventory$captureRigLights(Lighting.Entry entry, Vector3f light0,
			Vector3f light1, CallbackInfo ci) {
		if (entry == Lighting.Entry.ITEMS_FLAT) {
			GuiLightRigs.captureFlatRig(light0, light1);
		} else if (entry == Lighting.Entry.ITEMS_3D) {
			GuiLightRigs.capture3dRig(light0, light1);
		}
	}
}
