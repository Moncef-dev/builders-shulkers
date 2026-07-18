package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Animates the lid of the shulker the local player holds in FIRST person. The GUI, dropped entities, and the
// held item on OTHER entities (third person) are instrumented by their own mixins; first person was the one path
// left without a side channel, so the openness mixin used to fall back to "the local player's held animation"
// for any shulker with no channel set, which wrongly animated other players' held shulkers too. Publishing the id
// around the first-person item draw lets that fallback be removed: each shulker is now animated only by its own
// marker.
@Mixin(ItemInHandRenderer.class)
public abstract class FirstPersonHeldItemMixin {
	@Inject(method = "renderItem", at = @At("HEAD"))
	private void shulkerInventory$markFirstPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		Long id = ClientShulkerSession.resolveHeldAnimationId(stack, entity);
		if (id != null && ClientShulkerSession.isAnimating(id)) {
			ClientShulkerSession.setCurrentItemEntityAnimationId(id);
		}
	}

	@Inject(method = "renderItem", at = @At("RETURN"))
	private void shulkerInventory$unmarkFirstPerson(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
			PoseStack pose, SubmitNodeCollector collector, int light, CallbackInfo ci) {
		ClientShulkerSession.setCurrentItemEntityAnimationId(0L);
	}
}
