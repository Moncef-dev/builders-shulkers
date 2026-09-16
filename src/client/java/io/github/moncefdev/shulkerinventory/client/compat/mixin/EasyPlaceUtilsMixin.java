package io.github.moncefdev.shulkerinventory.client.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import io.github.moncefdev.shulkerinventory.client.compat.LitematicaEasyPlaceCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Litematica easy place interop for the REWRITE implementation (the one its easyPlacePostRewrite setting turns on).
// WorldUtilsEasyPlaceMixin covers the historic implementation with the same pair of hooks; both are needed because
// either can be the active one, and they share their decisions through LitematicaEasyPlaceCompat.
//
// Easy place resolves the item its schematic needs, then hands off to MultiPlayerGameMode.useItemOn with a hit vector
// encoding the block orientation - and useItemOn is the method the Pocket-Build content swap already wraps. So the
// placement itself needs nothing: the two hooks only fix the ITEM RESOLUTION, which knows how to search the inventory
// but not the container component of the box in hand.
//
// Both hooks sit on internal call sites of a private method (there is no event for this path, unlike the schematic
// pick block), so they are declared require = 0: a Litematica refactor then turns the interop off instead of
// crashing the game. Nothing is remapped (Minecraft ships deobfuscated and Litematica is a third-party jar).
@Mixin(value = EasyPlaceUtils.class, remap = false)
public abstract class EasyPlaceUtilsMixin {

	// Keep Litematica's inventory pick away from the locked Pocket-Build hotbar slot, and select the required item
	// inside the box instead.
	@WrapOperation(method = "handleEasyPlace", require = 0, at = @At(value = "INVOKE",
			target = "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"))
	private static void shulkerInventory$pickFromHeldBox(ItemStack required, BlockPos pos, Level schematicWorld,
			Minecraft mc, Operation<Void> original) {
		if (!LitematicaEasyPlaceCompat.handlesPick(required)) {
			original.call(required, pos, schematicWorld, mc);
		}
	}

	// The check that used to end pocket-building: easy place compares the required item with what is actually in hand,
	// sees a shulker box, gets no hand back and gives up.
	@WrapOperation(method = "handleEasyPlace", require = 0, at = @At(value = "INVOKE",
			target = "Lfi/dy/masa/litematica/util/EntityUtils;getUsedHandForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionHand;"))
	private static InteractionHand shulkerInventory$handFromHeldBox(Player player, ItemStack required,
			Operation<InteractionHand> original) {
		return LitematicaEasyPlaceCompat.handFor(required, original.call(player, required));
	}
}
