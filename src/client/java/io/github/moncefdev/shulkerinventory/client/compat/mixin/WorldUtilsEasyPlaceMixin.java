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

// Same interop as EasyPlaceUtilsMixin, for the HISTORIC easy place implementation. Litematica ships both and picks
// one at runtime from its easyPlacePostRewrite setting (off by default, so this is the one most players run); the two
// have the same item-resolution call sites, so both carry the same pair of hooks and share their decisions through
// LitematicaEasyPlaceCompat. This one runs from the input/tick layer, before the vanilla use, so the hand it inspects
// is always the box itself.
@Mixin(value = WorldUtils.class, remap = false)
public abstract class WorldUtilsEasyPlaceMixin {

	@WrapOperation(method = "doEasyPlaceAction", require = 0, at = @At(value = "INVOKE",
			target = "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"))
	private static void shulkerInventory$pickFromHeldBox(ItemStack required, BlockPos pos, Level schematicWorld,
			Minecraft mc, Operation<Void> original) {
		if (!LitematicaEasyPlaceCompat.handlesPick(required)) {
			original.call(required, pos, schematicWorld, mc);
		}
	}

	@WrapOperation(method = "doEasyPlaceAction", require = 0, at = @At(value = "INVOKE",
			target = "Lfi/dy/masa/litematica/util/EntityUtils;getUsedHandForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionHand;"))
	private static InteractionHand shulkerInventory$handFromHeldBox(Player player, ItemStack required,
			Operation<InteractionHand> original) {
		return LitematicaEasyPlaceCompat.handFor(required, original.call(player, required));
	}
}
