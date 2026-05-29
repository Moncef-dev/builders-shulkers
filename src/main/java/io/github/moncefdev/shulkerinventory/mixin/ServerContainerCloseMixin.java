package io.github.moncefdev.shulkerinventory.mixin;

import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerContainerCloseMixin {
	@Shadow
	public ServerPlayer player;

	// During a shulker swap we close one menu and immediately open another. A close packet for the previous
	// menu can arrive late and would otherwise close the freshly opened one, so drop any close whose id does
	// not match the currently open menu.
	@Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$ignoreStaleClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
		if (packet.getContainerId() != player.containerMenu.containerId) {
			ci.cancel();
		}
	}
}
