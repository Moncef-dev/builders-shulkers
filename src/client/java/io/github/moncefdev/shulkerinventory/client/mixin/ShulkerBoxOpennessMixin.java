package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// Overrides the shulker lid openness (0..1) with our interpolated animation progress when the stack being rendered is
// the local player's live Pocket-Build box; otherwise returns the original vanilla value so other shulkers (placed
// blocks, or a shulker drawn INSIDE the box as content) render unchanged.
@Mixin(ShulkerBoxSpecialRenderer.class)
public abstract class ShulkerBoxOpennessMixin {
	// 1.21.11 ShulkerBoxRenderer.submit is the 9-arg form (carries the box orientation Direction and a Material); the
	// openness float sits at index 5 (after the Direction). 26.x dropped the Direction and used a SpriteId, with the
	// openness at index 4.
	@ModifyArg(method = "submit",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/blockentity/ShulkerBoxRenderer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IILnet/minecraft/core/Direction;FLnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;Lnet/minecraft/client/resources/model/Material;I)V"),
			index = 5)
	private float shulkerInventory$overrideOpenness(float original) {
		long id = ClientShulkerSession.getCurrentAtlasRenderingId();
		if (id == 0L) {
			id = ClientShulkerSession.getCurrentItemEntityAnimationId();
		}
		boolean content = ClientShulkerSession.isRenderingContentLayer();
		// Animate the lid for the OUTER box only; a content shulker (content-layer flag) keeps the vanilla value. This is
		// safe even for a same-colour nested shulker because the content uses a SEPARATE model instance (swapped on in
		// PocketBuildContentLayerMixin), so its closed lid no longer drags the box's shared model to closed.
		if (id == 0L || content) {
			return original;
		}
		// The render is now following this animation: release a deferred local open so it starts lifting from here
		// (its current progress) instead of from wherever it would have ticked to during the open round-trip.
		ClientShulkerSession.markRendered(id);
		Minecraft mc = Minecraft.getInstance();
		float progress = ClientShulkerSession.getInterpolatedProgress(id,
				mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		// Publish the raw progress (drives the DISSOLVE fade) and the lifecycle status (drives the instant DISAPPEAR) for
		// the lid-fade mixin. Both are independent of the lid POSITION returned below, so the effect plays its own
		// transition - decoupled from the lift animation - and the lid can keep dissolving while it no longer lifts.
		ClientShulkerSession.setCurrentDissolveProgress(progress);
		ClientShulkerSession.AnimationStatus status = ClientShulkerSession.getStatus(id);
		ClientShulkerSession.setCurrentRenderStatus(status);
		// Lid POSITION: lift with the progress when this animation type is enabled; otherwise freeze at the configured
		// resting lid state (Open = 1, Closed = 0). Pocket-Build vs inventory open is told apart by the pocketBuild flag.
		ClientConfig config = ClientConfig.get();
		boolean pocketBuild = ClientShulkerSession.isPocketBuild(id);
		boolean animationOn = pocketBuild ? config.pocketBuildAnimation : config.inventoryAnimation;
		float position;
		if (animationOn) {
			position = progress;
		} else if (status == ClientShulkerSession.AnimationStatus.CLOSING) {
			// Lift animation off: the lid does not move with the progress. Snap the POSITION shut at the FIRST frame of a
			// close instead of lingering at the resting state for the whole drain. Only the position is forced here; the
			// dissolve/disappear effect is left to play its own transition (published above) on top of the closed lid.
			position = 0.0f;
		} else {
			boolean lidOpen = pocketBuild ? config.pocketBuildLidOpenWhenAnimationOff : config.inventoryLidOpenWhenAnimationOff;
			position = lidOpen ? 1.0f : 0.0f;
		}
		// Publish the final lid position so the content layer can hide the in-box content once the lid is closed AND
		// opaque (see ClientShulkerSession.contentCoveredByClosedLid), instead of letting it poke out for the whole drain.
		ClientShulkerSession.setCurrentLidOpenness(position);
		return position;
	}
}
