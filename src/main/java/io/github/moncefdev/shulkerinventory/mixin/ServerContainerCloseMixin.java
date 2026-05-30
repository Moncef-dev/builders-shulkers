package io.github.moncefdev.shulkerinventory.mixin;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Stale-close guard: during a shulker swap we close one menu and immediately open another; a late close for the
// previous menu could otherwise close the freshly opened one. Scoped to OUR menu so we never suppress close packets
// for other mods' containers. (The creative cursor is reconciled at session close in InventoryShulkerBoxMenu, by
// dropping the server-side cursor shadow there, so there is no close-time cursor clearing in this mixin anymore.)
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerContainerCloseMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$onContainerClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
		// Drop a mismatched close while one of our shulker menus is the open container.
		if (player.containerMenu instanceof InventoryShulkerBoxMenu
				&& packet.getContainerId() != player.containerMenu.containerId) {
			ci.cancel();
		}
	}
}
