package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Overrides the shulker lid openness (0..1) with our interpolated animation progress when the stack being
// rendered is one of ours; otherwise returns the original vanilla value so other shulkers render unchanged.
@Mixin(ShulkerBoxSpecialRenderer.class)
public abstract class ShulkerBoxOpennessMixin {
	@ModifyArg(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/blockentity/ShulkerBoxRenderer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IIFLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/sprite/SpriteId;I)V"),
			index = 4)
	private float shulkerInventory$overrideOpenness(float original) {
		Minecraft mc = Minecraft.getInstance();
		long atlasId = ClientShulkerSession.getCurrentAtlasRenderingId();
		if (atlasId != 0L) {
			return ClientShulkerSession.getInterpolatedProgress(atlasId,
					mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		long itemEntityId = ClientShulkerSession.getCurrentItemEntityAnimationId();
		if (itemEntityId != 0L) {
			return ClientShulkerSession.getInterpolatedProgress(itemEntityId,
					mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		}
		if (mc.player != null) {
			Long heldId = findHeldShulkerAnimationId(mc.player);
			if (heldId != null) {
				return ClientShulkerSession.getInterpolatedProgress(heldId,
						mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
			}
		}
		return original;
	}

	private static Long findHeldShulkerAnimationId(Player player) {
		Long mainId = idIfAnimating(player.getMainHandItem());
		if (mainId != null) return mainId;
		return idIfAnimating(player.getOffhandItem());
	}

	private static Long idIfAnimating(ItemStack stack) {
		Long id = ClientShulkerSession.getAnimationIdForStack(stack);
		if (id != null && ClientShulkerSession.isAnimating(id)) return id;
		return null;
	}
}
