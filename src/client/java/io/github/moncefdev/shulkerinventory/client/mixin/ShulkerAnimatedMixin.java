package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

// Marks an animating shulker's GUI render state as animated and tags it with the animation id, so the atlas
// draw can publish that id for the openness mixin to consume.
@Mixin(GuiGraphicsExtractor.class)
public abstract class ShulkerAnimatedMixin {
	@Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V",
					shift = At.Shift.AFTER),
			locals = LocalCapture.CAPTURE_FAILSOFT)
	private void shulkerInventory$markAnimated(LivingEntity entity, Level level, ItemStack stack, int x, int y, int z,
			CallbackInfo ci, TrackingItemStackRenderState state) {
		Long animationId = ClientShulkerSession.getAnimationIdForStack(stack);
		if (animationId == null) return;
		if (!ClientShulkerSession.isAnimating(animationId)) return;
		state.setAnimated();
		state.appendModelIdentityElement(new ClientShulkerSession.AnimationMarker(animationId));
	}
}
