package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.AnimationIdHolder;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Captures a held stack's animation id into its render state as the state is built (for any living entity holding the
// item), so a held shulker animates its lid in third person - the holder's own third-person view and other players'
// views. submitArmWithItem reads it back from the render state. This is the resolution point that exists identically on
// every version (1.21.11 ADDED the raw ItemStack parameter to submitArmWithItem; 1.21.10 has no such parameter), so
// going through the render state keeps the feature at parity with one uniform hook. Mirrors the dropped-item-entity
// path (ItemEntityRendererMixin).
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@Inject(method = "updateForLiving", at = @At("RETURN"))
	private void shulkerInventory$captureHeldAnimationId(ItemStackRenderState state, ItemStack stack,
			ItemDisplayContext context, LivingEntity entity, CallbackInfo ci) {
		Long id = ClientShulkerSession.getAnimationIdForStack(stack);
		((AnimationIdHolder) state).shulkerInventory$setAnimationId(id != null ? id : 0L);
	}
}
