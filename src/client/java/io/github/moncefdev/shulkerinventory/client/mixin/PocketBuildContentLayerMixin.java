package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
import io.github.moncefdev.shulkerinventory.client.PocketBuildContentKind;
import io.github.moncefdev.shulkerinventory.client.PocketBuildContentLayer;
import io.github.moncefdev.shulkerinventory.client.PocketBuildContentRender;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Composes the Pocket-Build selected content into the shulker's own item render state, the vanilla bundle technique:
// the content's layers are appended to the SAME render state (shared depth = the content reads as sitting INSIDE the
// box), then oriented, shrunk and centred by the passes below. Content is classified by HOW it renders, never by item
// type (see PocketBuildContentRender and PocketBuildContentKind); flat-reporting content is additionally marked for
// the GUI lighting correction applied in LayerRenderStateContentMixin. Gated to the local player's live Pocket-Build
// shulker. The full composition rationale lives in TECHNICAL.md section 8.
@Mixin(ItemModelResolver.class)
public abstract class PocketBuildContentLayerMixin {
	// Separate-model shulker renderers for NESTED content shulkers, cached per colour sprite, so a content
	// shulker's lid pose is not coalesced with the box's shared model (see separateNestedShulkerModel).
	private static final Map<SpriteId, ShulkerBoxSpecialRenderer> CONTENT_SHULKER_RENDERERS = new ConcurrentHashMap<>();
	// Flat sprites (tools, the shield...) read wider than a block at the same scale, so they are shrunk a bit more to
	// stay inside the box (e.g. the sword and trident otherwise just clip the edges). A 3D block keeps the larger 0.6.
	private static final float FLAT_CONTENT_SCALE = 0.5f;
	// Third person holds the box smaller/further, so the flat sprite is shrunk a touch more there to stay tidy.
	private static final float FLAT_CONTENT_SCALE_THIRD_PERSON = 0.4f;
	// First person sits the flat sprite a little low in the box, so it is nudged up (box-local units, ~box height 0.4).
	private static final float FLAT_FIRST_PERSON_LIFT = 0.1f;
	private static final float BLOCK_CONTENT_SCALE = 0.6f;
	// The content's local space spans [0,1] per block unit and fills the box at scale 1, so the box centre is 0.5.
	private static final float BOX_CENTER = 0.5f;


	@Inject(method = "appendItemLayers", at = @At("TAIL"))
	private void shulkerInventory$appendPocketBuildContent(ItemStackRenderState output, ItemStack item,
			ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		// Held contexts resolve through the bridge (owner-gated), so the box's lid, content and badge follow the
		// click; every other context reads the plain marker (see resolveHeldAnimationId for why).
		Long id = (displayContext.firstPerson() || shulkerInventory$isThirdPersonHand(displayContext))
				? ClientShulkerSession.resolveHeldAnimationId(item, owner)
				: ClientShulkerSession.getAnimationIdForStack(item);
		if (id == null) {
			return;
		}
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
		// Give an ANIMATED box its OWN model instance (per id) so two same-colour shulkers held at once do not share one
		// lid ModelPart - the bug where a second held shulker of the same colour freezes this box's lid closed at draw
		// while the dissolve (its threshold captured at submit) keeps playing. Done on the box's own layers (those
		// already present, before any content is appended). Covers the inventory open and Pocket-Build alike; a static
		// box (no live animation) keeps the shared vanilla model. See shulkerInventory$separateAnimatedBoxModel.
		if (ClientShulkerSession.isAnimating(id)) {
			ItemStackRenderState.LayerRenderState[] boxLayers = state.shulkerInventory$getLayers();
			int boxLayerCount = state.shulkerInventory$getActiveLayerCount();
			for (int i = 0; i < boxLayerCount; i++) {
				shulkerInventory$separateAnimatedBoxModel(boxLayers[i], id);
			}
		}
		// Client setting: render the in-box content only when "show items" is on (the box still renders and animates).
		if (!ClientConfig.get().inBoxContent) {
			return;
		}
		ItemStack content = ClientShulkerSession.getPocketBuildContent(id);
		if (content.isEmpty()) {
			return;
		}
		PocketBuildContentKind kind = shulkerInventory$classify(content, level);
		// All content is appended with the GUI (inventory) display: flat sprites and 3D blocks alike. The block branch
		// then re-orients held/dropped content per layer (its own vanilla context display for plain baked models, the
		// box-following delta for special-renderer models - see applyContextOrientation). Flat content drops its
		// transform entirely.
		ItemDisplayContext contentContext = ItemDisplayContext.GUI;
		int before = state.shulkerInventory$getActiveLayerCount();
		((ItemModelResolver) (Object) this).appendItemLayers(output, content, contentContext, level, owner, seed);
		int after = state.shulkerInventory$getActiveLayerCount();
		ItemStackRenderState.LayerRenderState[] layers = state.shulkerInventory$getLayers();
		// Tag the appended layers as Pocket-Build content, so the shulker lid mixins keep a nested content shulker static
		// (closed, no dissolve) while the outer box animates - whatever the draw order or the shulker colour. Content
		// whose model REPORTS flat lighting is additionally marked for the GUI rig correction, keyed on the lighting
		// report - NOT on the composition path - so both the true sprites (FLAT kind) and the flat-lit 3D geometry
		// (shield, banners, calibrated_sculk_sensor, decorated_pot; BLOCK kinds) get the same exact correction
		// (see LayerRenderStateContentMixin).
		boolean flatGuiLit = displayContext == ItemDisplayContext.GUI && PocketBuildContentRender.isFlat(content, level);
		for (int i = before; i < after; i++) {
			((PocketBuildContentLayer) (Object) layers[i]).shulkerInventory$setPocketBuildContent(true);
			((PocketBuildContentLayer) (Object) layers[i]).shulkerInventory$setPocketBuildFlatGuiLit(flatGuiLit);
			shulkerInventory$separateNestedShulkerModel(layers[i]);
		}

		if (kind.isFlat()) {
			shulkerInventory$applyFlatContent(layers, before, after, displayContext);
		} else {
			shulkerInventory$applyBlockContent(layers, before, after, displayContext, item, content, level, kind);
		}
	}

	// Classify by render signals, splitting the two "reports FLAT lighting but is really 3D" cases on the NATIVE
	// GUI rotation: face-on special blocks (decorated_pot) go BLOCK_OVERSIZED (3D path + block iso substituted,
	// their inventory iso normally comes from the oversized PIP this composition bypasses); natively iso-rotated
	// flat-reporting models (banners, a tilted shield, front-gui_light blocks) go BLOCK as-is. Everything non-flat
	// is BLOCK. TECHNICAL.md section 8 (classification).
	private static PocketBuildContentKind shulkerInventory$classify(ItemStack content, Level level) {
		boolean flatReport = PocketBuildContentRender.isFlat(content, level);
		if (!flatReport) {
			return PocketBuildContentKind.BLOCK;
		}
		boolean faceOn = PocketBuildContentRender.isFaceOnGui(content, level);
		if (!faceOn) {
			return PocketBuildContentKind.BLOCK;
		}
		if (PocketBuildContentRender.isSpecial(content, level) && content.getItem() instanceof BlockItem) {
			return PocketBuildContentKind.BLOCK_OVERSIZED;
		}
		return PocketBuildContentKind.FLAT;
	}

	// Whether a relative rotation maps the coordinate axes onto (signed) coordinate axes, i.e. belongs to the cube's
	// rotation group within tolerance. Checking two basis vectors suffices (the third follows by orthogonality). The
	// 0.99 tolerance passes exact 90-degree families and rejects every aesthetic display angle in vanilla (whose
	// smallest off-axis component, 25 degrees on the bed, projects far below it).
	private static boolean shulkerInventory$isBoxAligned(Quaternionf rel) {
		Vector3f v = rel.transform(new Vector3f(1.0f, 0.0f, 0.0f));
		if (Math.max(Math.abs(v.x), Math.max(Math.abs(v.y), Math.abs(v.z))) < 0.99f) {
			return false;
		}
		rel.transform(v.set(0.0f, 1.0f, 0.0f));
		return Math.max(Math.abs(v.x), Math.max(Math.abs(v.y), Math.abs(v.z))) >= 0.99f;
	}

	// A held context: the box is posed in hand (first or third person). The orientation-follow and the height-centring
	// both key off this identical predicate, kept in one place.
	private static boolean shulkerInventory$isHeldContext(ItemDisplayContext displayContext) {
		return displayContext.firstPerson()
				|| displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
	}

	private static boolean shulkerInventory$isThirdPersonHand(ItemDisplayContext displayContext) {
		return displayContext == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| displayContext == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
	}

	// FLAT content: drop the content's own display transform (a flat GUI transform has no rotation) and centre +
	// scale it onto the box's REAL geometry centre, read from the box's own layers with their full transforms -
	// the box is drawn in entity-model native space, so its visual centre is NOT (0.5,0.5,0.5) and content placed
	// there drifts onto the hand. Held-only tweaks: third person shrinks a bit more, first person lifts the sprite
	// a touch. TECHNICAL.md section 8 (the flat path).
	private static void shulkerInventory$applyFlatContent(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, ItemDisplayContext displayContext) {
		float flatScale = shulkerInventory$isThirdPersonHand(displayContext)
				? FLAT_CONTENT_SCALE_THIRD_PERSON : FLAT_CONTENT_SCALE;
		float liftY = displayContext.firstPerson() ? FLAT_FIRST_PERSON_LIFT : 0.0f;
		float[] box = shulkerInventory$finalBbox(layers, 0, before, displayContext.leftHand());
		float[] cont = shulkerInventory$bbox(layers, before, after);
		Matrix4f localTransform;
		if (box != null && cont != null) {
			float boxCx = (box[0] + box[3]) * 0.5f, boxCy = (box[1] + box[4]) * 0.5f, boxCz = (box[2] + box[5]) * 0.5f;
			float contCx = (cont[0] + cont[3]) * 0.5f, contCy = (cont[1] + cont[4]) * 0.5f, contCz = (cont[2] + cont[5]) * 0.5f;
			float boxSize = Math.max(box[3] - box[0], Math.max(box[4] - box[1], box[5] - box[2]));
			float contSize = Math.max(cont[3] - cont[0], Math.max(cont[4] - cont[1], cont[5] - cont[2]));
			float scale = contSize > 1.0e-4f ? flatScale * boxSize / contSize : flatScale;
			// +0.5 compensates ItemTransform.apply: even NO_TRANSFORM (which we assign to the content below) ends with
			// translate(-0.5,-0.5,-0.5), so the content's final transform is translate(-0.5) x localTransform. We want
			// the content centre to land on the box centre boxC, hence localTransform must map it to boxC + 0.5.
			// NB: LayerRenderStateContentMixin re-folds the localTransform's DIRECTION part into the lighting
			// normals; this one is translate + uniform scale only, so its direction part is identity.
			localTransform = new Matrix4f()
					.translate(boxCx + BOX_CENTER, boxCy + BOX_CENTER + liftY, boxCz + BOX_CENTER)
					.scale(scale)
					.translate(-contCx, -contCy, -contCz);
		} else {
			localTransform = new Matrix4f()
					.translate(BOX_CENTER, BOX_CENTER + liftY, BOX_CENTER)
					.scale(flatScale)
					.translate(-BOX_CENTER, -BOX_CENTER, -BOX_CENTER);
		}
		for (int i = before; i < after; i++) {
			layers[i].setItemTransform(ItemTransform.NO_TRANSFORM);
			layers[i].setLocalTransform(localTransform);
		}
	}

	// 3D BLOCK content: a small block shrunk 0.6 about the box centre, turned and contained WITH the box. The steps run
	// in order, each measuring the result of the earlier ones: iso substitution (oversized only), box-following
	// orientation, box size factor, the 0.6 shrink, then centring.
	private static void shulkerInventory$applyBlockContent(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, ItemDisplayContext displayContext, ItemStack item, ItemStack content, Level level,
			PocketBuildContentKind kind) {
		// Box GUI display reference (rotation + scale), probed deterministically from the box's own model, cached.
		float[] boxRef = PocketBuildContentRender.boxGuiRef(item, level);
		// The DROPPED item entity (GROUND) shows the box in a posed, non-GUI display just like the hand does, so the
		// content must follow the box and be contained in it there too.
		boolean dropped = displayContext == ItemDisplayContext.GROUND;
		boolean held = shulkerInventory$isHeldContext(displayContext);
		if (kind.needsBlockIso() && before > 0) {
			shulkerInventory$applyBlockIso(layers, before, after, boxRef);
		}
		if (before > 0 && (held || dropped)) {
			shulkerInventory$applyContextOrientation(layers, before, after, boxRef, displayContext, content, level);
		}
		if (before > 0 && boxRef[3] > 1.0e-4f) {
			shulkerInventory$applyBoxSizeFactor(layers, before, after, boxRef);
		}
		shulkerInventory$applyBlockShrink(layers, before, after);
		if (held || dropped || displayContext == ItemDisplayContext.GUI) {
			shulkerInventory$applyBlockCentring(layers, before, after, displayContext, held, dropped, content, level);
		}
	}

	// An oversized special block (decorated_pot) reports a STRAIGHT GUI rotation - its inventory iso normally comes from
	// the oversized PIP, which our in-box composition bypasses - so substitute the standard block iso onto each layer.
	// That gives it the 3D inventory look in the slot, and becomes the base the held delta then turns.
	private static void shulkerInventory$applyBlockIso(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, float[] boxRef) {
		for (int i = before; i < after; i++) {
			ItemTransform it = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getItemTransform();
			layers[i].setItemTransform(new ItemTransform(new Vector3f(boxRef[0], boxRef[1], boxRef[2]), it.translation(), it.scale()));
		}
	}

	// Orient held/dropped 3D content, two vanilla-derived branches: a standard block pose takes the content's OWN
	// display rotation for this context (vanilla-exact per family; a shared delta cannot match cubes AND stairs at
	// once); everything else - special renderers and aesthetic hand poses - follows the box via
	// delta = boxRot(context) * boxRot(gui)^-1. The gate is geometric (isBoxAligned), never an item list, and an
	// unknown family degrades to the safe box-following branch. Runs before the size + centring passes so they
	// measure the final orientation. Full rationale: TECHNICAL.md section 8 (orientation).
	private static void shulkerInventory$applyContextOrientation(ItemStackRenderState.LayerRenderState[] layers,
			int before, int after, float[] boxRef, ItemDisplayContext displayContext, ItemStack content, Level level) {
		float[][] own = PocketBuildContentRender.contextRotations(content, level, displayContext);
		Vector3fc boxRotCtx = ((LayerRenderStateAccessor) layers[0]).shulkerInventory$getItemTransform().rotation();
		Quaternionf qBoxCtx = new Quaternionf().rotationXYZ(
				(float) Math.toRadians(boxRotCtx.x()),
				(float) Math.toRadians(boxRotCtx.y()),
				(float) Math.toRadians(boxRotCtx.z()));
		Vector3f boxGui = new Vector3f(boxRef[0], boxRef[1], boxRef[2]);
		Quaternionf qBoxGuiInv = new Quaternionf().rotationXYZ(
				(float) Math.toRadians(boxGui.x()),
				(float) Math.toRadians(boxGui.y()),
				(float) Math.toRadians(boxGui.z())).conjugate();
		Quaternionf delta = new Quaternionf(qBoxCtx).mul(qBoxGuiInv);
		for (int i = before; i < after; i++) {
			ItemTransform it = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getItemTransform();
			boolean special = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getSpecialRenderer() != null;
			// The layer count guard covers models that select different submodels per display context, where the
			// probed layers would not line up with ours; such content falls back to the box-following branch.
			if (!special && own.length == after - before) {
				float[] r = own[i - before];
				Quaternionf qOwn = new Quaternionf().rotationXYZ(
						(float) Math.toRadians(r[0]),
						(float) Math.toRadians(r[1]),
						(float) Math.toRadians(r[2]));
				if (shulkerInventory$isBoxAligned(new Quaternionf(qBoxCtx).conjugate().mul(qOwn))) {
					layers[i].setItemTransform(new ItemTransform(new Vector3f(r[0], r[1], r[2]),
							it.translation(), it.scale()));
					continue;
				}
			}
			Vector3fc r = it.rotation();
			Quaternionf qNew = new Quaternionf(delta).mul(new Quaternionf().rotationXYZ(
					(float) Math.toRadians(r.x()),
					(float) Math.toRadians(r.y()),
					(float) Math.toRadians(r.z())));
			Vector3f euler = qNew.getEulerAnglesXYZ(new Vector3f());
			layers[i].setItemTransform(new ItemTransform(
					new Vector3f((float) Math.toDegrees(euler.x),
							(float) Math.toDegrees(euler.y),
							(float) Math.toDegrees(euler.z)),
					it.translation(), it.scale()));
		}
	}

	// Make the content track the box's size across views. The content is appended GUI-scaled (for orientation), so it
	// keeps the GUI display scale in every context; the box, in its own per-context display, shrinks in hand. Multiply
	// the content's scale by the box's OWN shrink factor (boxScale-now / boxScale-in-GUI) - a single scalar, so each item
	// keeps its own GUI proportions (no per-item skew). The size-relative pass then measures a content and box that
	// shrink together, so its result is the same in the slot and in hand (a conduit stays small in both). No-op in GUI
	// (factor == 1).
	private static void shulkerInventory$applyBoxSizeFactor(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, float[] boxRef) {
		float boxNow = ((LayerRenderStateAccessor) layers[0]).shulkerInventory$getItemTransform().scale().x();
		float factor = boxNow / boxRef[3];
		if (Math.abs(factor - 1.0f) > 1.0e-4f) {
			for (int i = before; i < after; i++) {
				ItemTransform it = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getItemTransform();
				Vector3fc s = it.scale();
				layers[i].setItemTransform(new ItemTransform(it.rotation(), it.translation(),
						new Vector3f(s.x() * factor, s.y() * factor, s.z() * factor)));
			}
		}
	}

	// Shrink content to a CONSTANT 0.6x of its OWN vanilla GUI size, like the vanilla slot's single fixed block
	// scale: same-body blocks stay same-height and a naturally small model stays small (never a box-relative cap,
	// which over-shrank thin-axis overhangs like the calibrated sensor's horn). Composed with - never substituted
	// for - each layer's own localTransform, so a special renderer's model fit is kept. TECHNICAL.md section 8.
	private static void shulkerInventory$applyBlockShrink(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after) {
		// NB: the composed localTransform's DIRECTION part is the fit's own (our factor is translate + uniform
		// scale, direction-neutral). A special model's fit can carry a MIRROR (the shield and banner models are
		// built Y-down, flipped by their item JSON transformation); LayerRenderStateContentMixin re-folds exactly
		// that direction part into the lighting normals, matching what standalone rendering does.
		Matrix4f localTransform = new Matrix4f()
				.translate(BOX_CENTER, BOX_CENTER, BOX_CENTER)
				.scale(BLOCK_CONTENT_SCALE)
				.translate(-BOX_CENTER, -BOX_CENTER, -BOX_CENTER);
		for (int i = before; i < after; i++) {
			Matrix4f fit = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getLocalTransform();
			layers[i].setLocalTransform(new Matrix4f(localTransform).mul(fit));
		}
	}

	// Centre the block on the box: X and Z in every view; the HEIGHT in held and dropped views (the posed box needs
	// the full 3-axis centre) and, in the GUI slot, only for the groups whose "natural height" is not their slot
	// look (see the loop below) - a slab keeps resting on the box floor. The offset is added to the display
	// translation, in the box's shared pre-outer-pose space. TECHNICAL.md section 8 (centre offset).
	private static void shulkerInventory$applyBlockCentring(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, ItemDisplayContext displayContext, boolean held, boolean dropped, ItemStack content,
			Level level) {
		float[] boxF = shulkerInventory$finalBbox(layers, 0, before, displayContext.leftHand());
		float[] conF = shulkerInventory$finalBbox(layers, before, after, displayContext.leftHand());
		if (boxF != null && conF != null) {
			float offX = (boxF[0] + boxF[3] - conF[0] - conF[3]) * 0.5f;
			float offZ = (boxF[2] + boxF[5] - conF[2] - conF[5]) * 0.5f;
			// Three groups are Y-centred in GUI too, "natural height" not being their slot look: special-renderer
			// content (entity-model space), geometry horizontally off the shrink pivot (the bed - a merely
			// overhanging but on-pivot model like the calibrated sensor keeps natural height), and content whose
			// own gui display lifts it (heavy_core). TECHNICAL.md section 8 (centre offset).
			boolean offBlockSpace = PocketBuildContentRender.offPivotHorizontally(content, level);
			for (int i = before; !offBlockSpace && i < after; i++) {
				LayerRenderStateAccessor layer = (LayerRenderStateAccessor) layers[i];
				// A vertical lift in the model's own gui display is vanilla's signal that the geometry's natural
				// height is NOT the intended slot look (heavy_core raises its floor-sitting 8px cube to the tile
				// centre): such content is centred in the box exactly like the vanilla slot centres it. Zero for
				// every standard block display, whose natural height stands.
				if (layer.shulkerInventory$getSpecialRenderer() != null
						|| Math.abs(layer.shulkerInventory$getItemTransform().translation().y()) > 1.0e-4f) {
					offBlockSpace = true;
				}
			}
			float offY = (held || dropped || offBlockSpace) ? (boxF[1] + boxF[4] - conF[1] - conF[4]) * 0.5f : 0f;
			for (int i = before; i < after; i++) {
				ItemTransform it = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getItemTransform();
				Vector3fc t = it.translation();
				layers[i].setItemTransform(new ItemTransform(it.rotation(),
						new Vector3f(t.x() + offX, t.y() + offY, t.z() + offZ), it.scale()));
			}
		}
	}

	// Give a nested CONTENT shulker its OWN model instance, so its (closed) lid pose is not coalesced with the box's
	// shared model at draw. Same-colour shulkers share one ShulkerBoxModel per colour and vanilla resolves the lid from
	// that single model, so a content shulker forced closed would otherwise drag the box's lid closed too. The separate
	// renderer keeps the same colour sprite, is cached per sprite, and only the renderer field is swapped (transforms are
	// left intact). No-op for non-shulker content.
	private static void shulkerInventory$separateNestedShulkerModel(ItemStackRenderState.LayerRenderState layer) {
		SpecialModelRenderer<?> renderer = ((LayerRenderStateAccessor) layer).shulkerInventory$getSpecialRenderer();
		if (!(renderer instanceof ShulkerBoxSpecialRenderer shulker)) {
			return;
		}
		ShulkerBoxSpecialRendererAccessor acc = (ShulkerBoxSpecialRendererAccessor) shulker;
		ShulkerBoxSpecialRenderer separate = CONTENT_SHULKER_RENDERERS.computeIfAbsent(acc.shulkerInventory$getSprite(), s -> {
			SpriteGetter sprites = ((ShulkerBoxRendererAccessor) acc.shulkerInventory$getShulkerBoxRenderer())
					.shulkerInventory$getSprites();
			ShulkerBoxRenderer freshRenderer = new ShulkerBoxRenderer(Minecraft.getInstance().getEntityModels(), sprites);
			return new ShulkerBoxSpecialRenderer(freshRenderer, 0.0f, s);
		});
		((LayerRenderStateAccessor) layer).shulkerInventory$setSpecialRenderer(separate);
	}

	// Swap an ANIMATED box layer's shulker renderer for one backed by a model instance unique to this animation id, so
	// its lid is not mutualised with another same-colour shulker rendered in the same frame (the shared model's last
	// setupAnim of the frame wins at draw, freezing this box's lid). The instance is cached on the AnimationState (keyed
	// by id, not by sprite as the content cache is, so two animated same-colour boxes still get distinct models), and
	// freed when the animation drains. No-op for a non-shulker layer, or once the animation is gone (renderer null).
	private static void shulkerInventory$separateAnimatedBoxModel(ItemStackRenderState.LayerRenderState layer, long id) {
		SpecialModelRenderer<?> renderer = ((LayerRenderStateAccessor) layer).shulkerInventory$getSpecialRenderer();
		if (!(renderer instanceof ShulkerBoxSpecialRenderer shulker)) {
			return;
		}
		ShulkerBoxSpecialRenderer separate = ClientShulkerSession.separatedBoxRenderer(id, () -> {
			ShulkerBoxSpecialRendererAccessor acc = (ShulkerBoxSpecialRendererAccessor) shulker;
			SpriteGetter sprites = ((ShulkerBoxRendererAccessor) acc.shulkerInventory$getShulkerBoxRenderer())
					.shulkerInventory$getSprites();
			ShulkerBoxRenderer freshRenderer = new ShulkerBoxRenderer(Minecraft.getInstance().getEntityModels(), sprites);
			return new ShulkerBoxSpecialRenderer(freshRenderer, 0.0f, acc.shulkerInventory$getSprite());
		});
		if (separate != null) {
			((LayerRenderStateAccessor) layer).shulkerInventory$setSpecialRenderer(separate);
		}
	}

	// Axis-aligned bounding box {minX,minY,minZ,maxX,maxY,maxZ} over the RAW (untransformed) extents of layers
	// [from, to), or null if those layers have no extents. Used for the content, whose own transform is dropped to
	// NO_TRANSFORM, so its geometry centre is read directly from the model.
	private static float[] shulkerInventory$bbox(ItemStackRenderState.LayerRenderState[] layers, int from, int to) {
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
		boolean any = false;
		for (int i = from; i < to; i++) {
			for (Vector3fc extent : ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getExtents().get()) {
				any = true;
				minX = Math.min(minX, extent.x());
				minY = Math.min(minY, extent.y());
				minZ = Math.min(minZ, extent.z());
				maxX = Math.max(maxX, extent.x());
				maxY = Math.max(maxY, extent.y());
				maxZ = Math.max(maxZ, extent.z());
			}
		}
		return any ? new float[] {minX, minY, minZ, maxX, maxY, maxZ} : null;
	}

	// Bbox of layers [from,to) with the FULL per-layer transform applied (itemTransform then localTransform), giving each
	// layer's geometry in the shared pre-outer-pose space. Used to read the box's true centre, since the box layer
	// carries a non-identity localTransform (and an entity-model-native itemTransform) that its raw extents do not show.
	private static float[] shulkerInventory$finalBbox(ItemStackRenderState.LayerRenderState[] layers, int from, int to,
			boolean leftHand) {
		float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
		boolean any = false;
		Vector3f scratch = new Vector3f();
		for (int i = from; i < to; i++) {
			LayerRenderStateAccessor layer = (LayerRenderStateAccessor) layers[i];
			PoseStack.Pose pose = new PoseStack.Pose();
			layer.shulkerInventory$getItemTransform().apply(leftHand, pose);
			Matrix4f m = new Matrix4f(pose.pose()).mul(layer.shulkerInventory$getLocalTransform());
			for (Vector3fc extent : layer.shulkerInventory$getExtents().get()) {
				any = true;
				scratch.set(extent).mulPosition(m);
				minX = Math.min(minX, scratch.x());
				minY = Math.min(minY, scratch.y());
				minZ = Math.min(minZ, scratch.z());
				maxX = Math.max(maxX, scratch.x());
				maxY = Math.max(maxY, scratch.y());
				maxZ = Math.max(maxZ, scratch.z());
			}
		}
		return any ? new float[] {minX, minY, minZ, maxX, maxY, maxZ} : null;
	}
}
