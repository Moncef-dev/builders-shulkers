package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Binds a shulker screen, built right after our open click, to the pending animation id, so the close
// animation can later be matched back to this exact screen.
@Mixin(ShulkerBoxScreen.class)
public abstract class ShulkerBoxScreenAssociateMixin {
	@Inject(method = "<init>", at = @At("RETURN"))
	private void shulkerInventory$associateWithPendingId(ShulkerBoxMenu menu, Inventory inventory, Component title, CallbackInfo ci) {
		Screen screen = (Screen) (Object) this;
		ClientShulkerSession.associateScreenWithPendingId(screen);
		// A non-null id means this screen claimed our pending open, i.e. it is one of OUR shulker sessions (not a
		// vanilla block shulker screen). The opener always hears their own open sound.
		if (ClientShulkerSession.getIdForScreen(screen) != null) {
			ClientShulkerSession.playOwnSound(true);
		}
	}
}
