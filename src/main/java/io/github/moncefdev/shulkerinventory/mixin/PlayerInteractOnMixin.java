package io.github.moncefdev.shulkerinventory.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.moncefdev.shulkerinventory.PocketBuildContentSwap;
import io.github.moncefdev.shulkerinventory.PocketBuildServerState;
import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import io.github.moncefdev.shulkerinventory.ShulkerContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;

// Content-swap on the ENTITY path, the counterpart of ServerPlayerGameModeMixin for blocks. Player.interactOn is
// common code, run both on the server (authoritative, from the interact packet) and on the client for prediction; in
// single-player the two share this static state. While in Pocket-Build mode PocketBuildContentSwap briefly puts the
// selected content in the hand, runs the WHOLE vanilla entity interaction on it (e.g. placing the content into an
// item frame, with its sound, its survival-only consume), then restores the shulker and removes from its CONTAINER
// exactly what vanilla used.
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
		if (!ShulkerContents.isShulker(shulker)) {
			return original.call(entity, hand, vec);
		}
		// Fail-closed on held-box identity (see ServerPlayerGameModeMixin): an in-place item swap replaces the held
		// shulker without moving the selected slot, so only interact with the content if the held box's marker still
		// matches the one the mode entered with; otherwise run plain vanilla and clear the now-stale state.
		if (!PocketBuildServerState.matchesModeBox(self.getUUID(), ShulkerAnimationMarker.get(shulker))) {
			PocketBuildServerState.exit(self.getUUID());
			return original.call(entity, hand, vec);
		}
		// interactOn reads the hand item itself, so the swapped-in content drives the interaction; held is unused here.
		return PocketBuildContentSwap.runOnServer(self, shulker, held -> original.call(entity, hand, vec));
	}
}
