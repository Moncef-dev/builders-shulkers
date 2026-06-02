package io.github.moncefdev.shulkerinventory.client.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;

// Content-swap (client prediction): while in Pocket-Build mode, a PLAIN right-click on a block runs the vanilla
// use-on flow on the SELECTED CONTENT (briefly held), so the client predicts the placement and plays the place
// sound itself, and sends the normal vanilla use-on packet. The server mirrors the swap and stays authoritative.
// Ctrl + right-click is the mode toggle (handled by the use-event callback), so it is left untouched here.
// @WrapMethod wraps the whole call in try/finally, so the held shulker is always restored.
@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
	@WrapMethod(method = "useItemOn")
	private InteractionResult shulkerInventory$useContent(LocalPlayer player, InteractionHand hand, BlockHitResult hit,
			Operation<InteractionResult> original) {
		if (hand != InteractionHand.MAIN_HAND || !PocketBuildMode.isActive()
				|| InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
			return original.call(player, hand, hit);
		}
		ItemStack shulker = player.getMainHandItem();
		if (shulker.isEmpty() || !shulker.typeHolder().is(ItemTags.SHULKER_BOXES)) {
			return original.call(player, hand, hit);
		}
		// An empty selection (e.g. an empty shulker) swaps an EMPTY hand: the vanilla flow then places nothing, instead
		// of falling through to placing the shulker itself.
		ItemStack content = PocketBuildMode.selectedStack(shulker);
		int handSlot = player.getInventory().getSelectedSlot();
		ItemStack held = content.copy();
		player.getInventory().setItem(handSlot, held);
		try {
			return original.call(player, hand, hit);
		} finally {
			player.getInventory().setItem(handSlot, shulker);
		}
	}
}
