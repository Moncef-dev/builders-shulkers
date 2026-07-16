package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;
import io.github.moncefdev.shulkerinventory.network.PocketBuildSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// While Pocket-Build mode is active, the mouse wheel cycles the selected shulker content slot instead of moving the
// hotbar selection. We cancel the vanilla scroll entirely so the hotbar never moves. Guarded on no open screen so a
// stray active flag during the one-tick window before the mode exits on a menu open never breaks a screen's scroll.
@Mixin(MouseHandler.class)
public abstract class MouseHandlerScrollMixin {
	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$rerouteScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		if (!PocketBuildMode.isActive()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null) {
			return;
		}
		double amount = yOffset != 0.0 ? yOffset : xOffset;
		if (amount != 0.0) {
			// Wheel down (negative) advances to the next slot, wheel up to the previous one.
			PocketBuildMode.cycle(amount > 0.0 ? -1 : 1, mc.player.getMainHandItem());
			// Sync the new selection to the server so a placement (a normal vanilla use packet) swaps in the right
			// content.
			if (ClientPlatform.network().canSend(PocketBuildSelectPayload.TYPE)) {
				ClientPlatform.network().send(new PocketBuildSelectPayload(PocketBuildMode.selectedContentSlot()));
			}
		}
		ci.cancel();
	}
}
