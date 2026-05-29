package io.github.moncefdev.shulkerinventory.mixin;

import io.github.moncefdev.shulkerinventory.menu.InventoryShulkerBoxMenu;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Two close-time concerns, handled at the head of handleContainerClose:
// 1. Stale-close guard: during a shulker swap we close one menu and immediately open another; a late close for the
//    previous menu could otherwise close the freshly opened one. Scoped to OUR menu so we never suppress close
//    packets for other mods' containers.
// 2. Creative cursor reconciliation: prevents a creative-only duplication (see below).
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerContainerCloseMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
	private void shulkerInventory$onContainerClose(ServerboundContainerClosePacket packet, CallbackInfo ci) {
		// Creative duplication fix: in creative the cursor is client-authoritative (the client commits slot changes
		// via SetCreativeModeSlot), so the server's inventoryMenu.carried can become a stale phantom that creative
		// cursor operations (throw, place) never clear. When the player closes their own inventory, vanilla re-gives
		// that carried into the inventory, duplicating an item the client already disposed of. Clear it so vanilla
		// re-gives nothing; the creative client owns and commits the real cursor item itself. Survival is unaffected
		// (there the cursor is server-authoritative and is always reconciled before close).
		if (player.isCreative() && player.containerMenu == player.inventoryMenu
				&& !player.containerMenu.getCarried().isEmpty()) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}

		// Stale-close guard: drop a mismatched close while one of our shulker menus is the open container.
		if (player.containerMenu instanceof InventoryShulkerBoxMenu
				&& packet.getContainerId() != player.containerMenu.containerId) {
			ci.cancel();
		}
	}
}
