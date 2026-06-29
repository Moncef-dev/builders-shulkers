package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.AnimationIdHolder;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Animates the lid of a shulker held by an entity as seen in third person (notably another player's hand). The GUI,
// first-person hand, and dropped-entity paths are instrumented elsewhere; this covers the held item on other entities.
// Around the held-item submit, it publishes the animation id to the openness side channel when this client is tracking
// that animation (driven locally for the holder, or by the server broadcast for other viewers).
//
// The id is read from the render state (stashed there by ItemModelResolverMixin when the held item's render state is
// built), not from the ItemStack: 1.21.11 passes submitArmWithItem the held ItemStack but 1.21.10 does not, while the
// render state is present on both, so reading from it keeps the feature at parity. The handler still mirrors the target
// signature (Mixin requires the full argument list), and the unused-on-1.21.10 ItemStack parameter is dropped by a
// Stonecutter replacement for that version. The itemStack argument itself is intentionally unused.
@Mixin(ItemInHandLayer.class)
public abstract class HeldItemAnimationMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void shulkerInventory$markHeld(ArmedEntityRenderState state,
			ItemStackRenderState itemRenderState, ItemStack itemStack, HumanoidArm arm,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		long id = ((AnimationIdHolder) itemRenderState).shulkerInventory$getAnimationId();
		if (id != 0L && ClientShulkerSession.isAnimating(id)) {
			ClientShulkerSession.setCurrentItemEntityAnimationId(id);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void shulkerInventory$unmarkHeld(ArmedEntityRenderState state,
			ItemStackRenderState itemRenderState, ItemStack itemStack, HumanoidArm arm,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ClientShulkerSession.setCurrentItemEntityAnimationId(0L);
	}
}
