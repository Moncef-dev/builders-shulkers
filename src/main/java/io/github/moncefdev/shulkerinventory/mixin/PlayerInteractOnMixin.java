package io.github.moncefdev.shulkerinventory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.moncefdev.shulkerinventory.PocketBuildServerState;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import net.minecraft.core.NonNullList;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

// Content-swap on the ENTITY path, the counterpart of ServerPlayerGameModeMixin for blocks. Player.interactOn is
// common code, run both on the server (authoritative, from the interact packet) and on the client for prediction; in
// single-player the two share this static state. While in Pocket-Build mode we briefly put the selected content in
// the hand, run the WHOLE vanilla entity interaction on it (e.g. placing the content into an item frame, with its
// sound, its survival-only consume), then restore the shulker and remove from its CONTAINER exactly what vanilla used.
//
// The "hand is a shulker" guard makes this idempotent if the content is already swapped in (the client interact wrap
// swaps first, then this runs on the content and no-ops), so the two never double-swap.
@Mixin(Player.class)
public abstract class PlayerInteractOnMixin {
	@WrapMethod(method = "interactOn")
	private InteractionResult shulkerInventory$useContentOnEntity(Entity entity, InteractionHand hand, Vec3 vec,
			Operation<InteractionResult> original) {
		Player self = (Player) (Object) this;
		if (hand != InteractionHand.MAIN_HAND || !PocketBuildServerState.isActive(self.getUUID())) {
			return original.call(entity, hand, vec);
		}
		ItemStack shulker = self.getMainHandItem();
		if (shulker.isEmpty() || !shulker.typeHolder().is(ItemTags.SHULKER_BOXES)) {
			return original.call(entity, hand, vec);
		}
		NonNullList<ItemStack> items = ShulkerContents.read(shulker);
		// Nothing selected (empty shulker -> slot -1): swap an EMPTY hand so the vanilla interaction runs with no item
		// (e.g. just rotating a framed item), never falling through to dropping the shulker into the frame.
		int slot = PocketBuildServerState.selectedSlot(self.getUUID());
		ItemStack content = slot >= 0 ? items.get(slot) : ItemStack.EMPTY;
		int handSlot = self.getInventory().getSelectedSlot();
		ItemStack held = content.copy();
		self.getInventory().setItem(handSlot, held);
		try {
			return original.call(entity, hand, vec);
		} finally {
			self.getInventory().setItem(handSlot, shulker);
			int consumed = content.getCount() - held.getCount();
			if (consumed > 0) {
				ItemStack remaining = content.copy();
				remaining.shrink(consumed);
				items.set(slot, remaining);
				ShulkerContents.write(shulker, items);
			}
		}
	}
}
