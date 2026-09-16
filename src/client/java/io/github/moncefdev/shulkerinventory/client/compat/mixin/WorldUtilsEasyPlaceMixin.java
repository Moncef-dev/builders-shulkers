package io.github.moncefdev.shulkerinventory.client.compat.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.litematica.util.WorldUtils;
import io.github.moncefdev.shulkerinventory.client.compat.LitematicaEasyPlaceCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// Litematica easy place interop for the HISTORIC implementation (Litematica ships two and picks one at runtime from
// its easyPlacePostRewrite setting, off by default; EasyPlaceUtilsMixin covers the rewrite). Applied ONLY when
// Litematica is installed (LitematicaMixinPlugin gates the config).
//
// Easy place resolves the item its schematic needs from the player INVENTORY, then checks the stack in hand; a
// shulker box never matches, so it gives up and cancels the click. These two hooks answer for the held box instead:
// the required item becomes the Pocket-Build selection, and the hand check reports MAIN_HAND. Everything downstream
// is untouched, easy place going on to call useItemOn, the method the content swap already wraps.
//
// Injection point form: owner and name, WITHOUT the descriptor. 1.21.x is obfuscated, so a descriptor spelling out
// Minecraft types would have to be remapped to intermediary for the shipped jar while staying named in the dev run;
// matching on owner and name alone is mapping-independent, and each of these names is unique in its class. Both hooks
// are require = 0, so a Litematica refactor turns the interop off instead of crashing the game.
@Mixin(value = WorldUtils.class, remap = false)
public abstract class WorldUtilsEasyPlaceMixin {

	@WrapOperation(method = "doEasyPlaceAction", require = 0, at = @At(value = "INVOKE",
			target = "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock"))
	private static void shulkerInventory$pickFromHeldBox(ItemStack required, BlockPos pos, Level schematicWorld,
			Minecraft mc, Operation<Void> original) {
		if (!LitematicaEasyPlaceCompat.handlesPick(required)) {
			original.call(required, pos, schematicWorld, mc);
		}
	}

	@WrapOperation(method = "doEasyPlaceAction", require = 0, at = @At(value = "INVOKE",
			target = "Lfi/dy/masa/litematica/util/EntityUtils;getUsedHandForItem"))
	private static InteractionHand shulkerInventory$handFromHeldBox(Player player, ItemStack required,
			Operation<InteractionHand> original) {
		return LitematicaEasyPlaceCompat.handFor(required, original.call(player, required));
	}
}
