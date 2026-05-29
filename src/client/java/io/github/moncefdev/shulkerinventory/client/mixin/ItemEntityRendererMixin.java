package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.AnimationIdHolder;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// For dropped shulker item entities: captures the stack's animation id into the render state, then publishes it
// around the submit call so the openness mixin animates the world-rendered lid too.
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",
			at = @At("RETURN"))
	private void shulkerInventory$captureAnimationId(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
		Long id = ClientShulkerSession.getAnimationIdForStack(entity.getItem());
		((AnimationIdHolder) state).shulkerInventory$setAnimationId(id != null ? id : 0L);
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("HEAD"))
	private void shulkerInventory$pushTls(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		long id = ((AnimationIdHolder) state).shulkerInventory$getAnimationId();
		if (id != 0L && ClientShulkerSession.isAnimating(id)) {
			ClientShulkerSession.setCurrentItemEntityAnimationId(id);
		}
	}

	@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
			at = @At("RETURN"))
	private void shulkerInventory$popTls(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		ClientShulkerSession.setCurrentItemEntityAnimationId(0L);
	}
}
