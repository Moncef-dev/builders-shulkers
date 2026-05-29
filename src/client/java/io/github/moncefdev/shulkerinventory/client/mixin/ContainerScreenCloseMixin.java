package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// When the shulker screen closes, start the CLOSING phase of its lid animation.
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerScreenCloseMixin {
	@Inject(method = "removed", at = @At("HEAD"))
	private void shulkerInventory$startCloseAnimation(CallbackInfo ci) {
		if (!(((Object) this) instanceof ShulkerBoxScreen)) return;
		Long animationId = ClientShulkerSession.getIdForScreen((Screen) (Object) this);
		if (animationId == null) return;
		ClientShulkerSession.startClosing(animationId);
	}
}
