package io.github.moncefdev.shulkerinventory.client.mixin;

// Animates the lid of the shulker the local player holds in FIRST person. The GUI, dropped entities, and the
// held item on OTHER entities (third person) are instrumented by their own mixins; first person was the one path
// left without a side channel, so the openness mixin used to fall back to "the local player's held animation"
// for any shulker with no channel set, which wrongly animated other players' held shulkers too. Publishing the id
// around the first-person item draw lets that fallback be removed: each shulker is now animated only by its own
// marker.
//
// 26.3 split ItemInHandRenderer into FirstPersonHandsAndItems (state extraction) and FirstPersonHandsAndItemsRenderer
// (submission), and dropped renderItem: the first-person item is now submitted inside submitArmWithItem, off the
// extracted ItemStackRenderState. That method is the 26.3 window, and the holder is by definition the local player.
//? if >=26.3 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class FirstPersonHeldItemMixin {
	@Inject(method = "submitArmWithItem", at = @At("HEAD"))
	private void shulkerInventory$markFirstPerson(PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState handState, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		Long id = ClientShulkerSession.resolveHeldAnimationId(stack, Minecraft.getInstance().player);
		if (id != null && ClientShulkerSession.isAnimating(id)) {
			ClientShulkerSession.setCurrentItemEntityAnimationId(id);
		}
	}

	@Inject(method = "submitArmWithItem", at = @At("RETURN"))
	private void shulkerInventory$unmarkFirstPerson(PlayerRenderState playerState,
			FirstPersonHandsAndItemsRenderState handState, float partialTick, float pitch, InteractionHand hand,
			float swingProgress, ItemStack stack, float equipProgress, PoseStack pose, SubmitNodeCollector collector,
			int light, CallbackInfo ci) {
		ClientShulkerSession.setCurrentItemEntityAnimationId(0L);
	}
}
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
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
*///?}
