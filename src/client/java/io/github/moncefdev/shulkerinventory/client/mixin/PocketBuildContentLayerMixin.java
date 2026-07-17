package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import io.github.moncefdev.shulkerinventory.client.ClientShulkerSession;
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

// Composes the Pocket-Build selected content into the shulker's own item render state, the vanilla bundle technique: the
// content's layers are appended to the SAME render state, then shrunk 0.6 about a centre. One render state means the
// content is occluded by the box through the depth test. The content is classified by how it renders, not by item type
// (see PocketBuildContentRender): a 3D block vs a flat sprite (a sapling is a BlockItem yet flat).
//
// 3D BLOCK content (SETTLED, do not touch): rendered with the shulker's own per-context display transform, so it reads
// as a small 3D block, perfectly placed in slot, first person and third person.
//
// FLAT content (tools, the shield, flat blocks like saplings): a layer in every context, with its own display transform
// dropped and re-centred on the box's real geometry centre, scaled to 0.6, orientation kept. In held/world its lighting
// is correct; in the GUI slot the composite is lit with the box's 3D-item lighting, under which a flat sprite would
// sit below its flat-lit standalone brightness - corrected by the flat-brightness normal treatment in
// LayerRenderStateContentMixin (a separate flat-lit draw was never an option: it would have its own lighting but no
// shared depth, so it could not be HALF inside the box, in front of one wall and behind
// another - the dim-but-correctly-occluded layer is the accepted trade-off for now). Gated to the local player's live
// Pocket-Build shulker.
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

	// How the selected content renders, which picks its in-box composition path. Classified by how it DRAWS, not by item
	// type (a sapling is a BlockItem yet flat). See shulkerInventory$classify and PocketBuildContentRender.
	private enum ContentKind {
		// A flat sprite (tools, the shield, flat blocks like saplings): drawn as a layer, its own transform dropped and
		// re-centred + scaled on the box's real geometry.
		FLAT,
		// Any 3D block (a plain block, a custom-held block, a special block-entity renderer, or a natively iso-rotated
		// banner / shield / calibrated_sculk_sensor): the unified 3D path (GUI display + box-following delta + 0.6 shrink).
		BLOCK,
		// An oversized special block whose inventory iso normally comes from the oversized PIP our composition bypasses
		// (decorated_pot): the 3D path AND the standard block iso substituted onto each layer.
		BLOCK_OVERSIZED;

		boolean isFlat() {
			return this == FLAT;
		}

		boolean needsBlockIso() {
			return this == BLOCK_OVERSIZED;
		}
	}

	@Inject(method = "appendItemLayers", at = @At("TAIL"))
	private void shulkerInventory$appendPocketBuildContent(ItemStackRenderState output, ItemStack item,
			ItemDisplayContext displayContext, Level level, ItemOwner owner, int seed, CallbackInfo ci) {
		Long id = ShulkerAnimationMarker.get(item);
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
		ContentKind kind = shulkerInventory$classify(content, level);
		// All content is appended with the GUI (inventory) display: flat sprites and 3D blocks alike. A 3D block's own
		// per-context display can be a held-in-hand pose that reads wrong nested in the box; the GUI display is the same
		// upright look the slot shows, and the block branch then turns it WITH the box via the delta. Flat content drops
		// its transform entirely.
		ItemDisplayContext contentContext = ItemDisplayContext.GUI;
		int before = state.shulkerInventory$getActiveLayerCount();
		((ItemModelResolver) (Object) this).appendItemLayers(output, content, contentContext, level, owner, seed);
		int after = state.shulkerInventory$getActiveLayerCount();
		ItemStackRenderState.LayerRenderState[] layers = state.shulkerInventory$getLayers();
		// Tag the appended layers as Pocket-Build content, so the shulker lid mixins keep a nested content shulker static
		// (closed, no dissolve) while the outer box animates - whatever the draw order or the shulker colour.
		for (int i = before; i < after; i++) {
			((PocketBuildContentLayer) (Object) layers[i]).shulkerInventory$setPocketBuildContent(true);
			shulkerInventory$separateNestedShulkerModel(layers[i]);
		}

		if (kind.isFlat()) {
			shulkerInventory$applyFlatContent(layers, before, after, displayContext);
		} else {
			shulkerInventory$applyBlockContent(layers, before, after, displayContext, item, level, kind);
		}
	}

	// Classify the content by how it renders (see PocketBuildContentRender), telling apart two "reports FLAT lighting but
	// is really 3D" cases by the NATIVE GUI rotation:
	//  - decorated_pot: reports flat AND is face-on (rotation ~0). Its inventory iso normally comes from the oversized
	//    PIP, which our in-box composition bypasses, so the flat path would draw it as a flat icon. Route it to the 3D
	//    path AND substitute the standard block iso (BLOCK_OVERSIZED).
	//  - calibrated_sculk_sensor / banners / a tilted shield: report flat but are ALREADY iso-rotated natively (a 3D
	//    block with front gui_light, a banner, a shield's display). They take the unified 3D path AS-IS so they follow
	//    the box instead of being drawn flat (BLOCK). This also pulls shield + banners onto the 3D path.
	// Every non-flat item is a 3D block and takes the unified block path (BLOCK).
	private static ContentKind shulkerInventory$classify(ItemStack content, Level level) {
		boolean flatReport = PocketBuildContentRender.isFlat(content, level);
		if (!flatReport) {
			return ContentKind.BLOCK;
		}
		boolean faceOn = PocketBuildContentRender.isFaceOnGui(content, level);
		if (!faceOn) {
			return ContentKind.BLOCK;
		}
		if (PocketBuildContentRender.isSpecial(content, level) && content.getItem() instanceof BlockItem) {
			return ContentKind.BLOCK_OVERSIZED;
		}
		return ContentKind.FLAT;
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

	// FLAT content: drop the content's own display transform (a flat GUI transform has no rotation, so the current
	// horizontal orientation is preserved) and apply only a centre + scale onto the box's ACTUAL geometry centre, read
	// from the box's OWN layers, not the nominal block centre (0.5,0.5,0.5). As an item the shulker box is rendered in
	// its entity model's native space (ShulkerBoxRenderer skips the block-entity re-centring transform for items), so its
	// visual centre is not (0.5,0.5,0.5); a held flat item placed there drifts onto the hand. Matching the box's real
	// extents centre works in every context (same space) and scales the content to 0.6 of the box.
	// Box: its TRUE centre in render space = full per-layer transform (itemTransform AND localTransform) applied; the box
	// layer carries a non-identity localTransform, so itemTransform alone misplaces the centre. Content: raw model
	// extents, since its own transform is dropped to NO_TRANSFORM here and re-derived.
	// Per-context flat tweaks (held only; the GUI slot and any other context keep the defaults, untouched):
	//  - third person shrinks a bit more (0.4),
	//  - first person lifts the sprite up a touch in the box.
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
			// In the GUI slot, mark the flat layer for the flat-brightness lighting treatment (the slot lights the
			// whole composite with the box's 3D item lighting, which leaves a flat sprite dimmer than its own
			// flat-lit standalone render; see LayerRenderStateContentMixin).
			((PocketBuildContentLayer) (Object) layers[i])
					.shulkerInventory$setPocketBuildFlatGuiContent(displayContext == ItemDisplayContext.GUI);
		}
	}

	// 3D BLOCK content: a small block shrunk 0.6 about the box centre, turned and contained WITH the box. The steps run
	// in order, each measuring the result of the earlier ones: iso substitution (oversized only), box-following
	// orientation, box size factor, the 0.6 shrink, then centring.
	private static void shulkerInventory$applyBlockContent(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, ItemDisplayContext displayContext, ItemStack item, Level level, ContentKind kind) {
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
			shulkerInventory$applyBoxFollowingOrientation(layers, before, after, boxRef);
		}
		if (before > 0 && boxRef[3] > 1.0e-4f) {
			shulkerInventory$applyBoxSizeFactor(layers, before, after, boxRef);
		}
		shulkerInventory$applyBlockShrink(layers, before, after);
		if (held || dropped || displayContext == ItemDisplayContext.GUI) {
			shulkerInventory$applyBlockCentring(layers, before, after, displayContext, held, dropped);
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

	// Make a 3D block follow the box's orientation EXACTLY like a plain block (deepslate) does, WITHOUT discarding its own
	// intrinsic orientation. The content is appended with the GUI display, correct in the slot for every block (statue
	// upright, bed laid flat, head facing out); a held/dropped pose then rotates the box, and because the content's GUI
	// rotation is fixed it no longer matches, multiplying into a 3-axis skew. Fix: apply to the content the SAME rotation
	// the box undergoes from its GUI display to this context (delta = boxRot(context) * boxRot(gui)^-1), pre-multiplied
	// onto each layer's own GUI rotation. So the content keeps its correct GUI orientation and merely turns with the box.
	// Simply copying the box's rotation instead would drop the content's intrinsic part and, combined with an entity
	// model's own fit flip, turn a copper golem statue upside down or yaw a bed's two halves wrong. In GUI the delta is
	// identity, so the slot is untouched. Done before the size + centring so both measure the final orientation. The GUI
	// reference (boxRef) is probed deterministically from the box's own item model (see boxGuiRef).
	private static void shulkerInventory$applyBoxFollowingOrientation(ItemStackRenderState.LayerRenderState[] layers,
			int before, int after, float[] boxRef) {
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

	// Shrink content to a CONSTANT 0.6x of its OWN vanilla GUI size, exactly like a plain block (deepslate). Uniform, so
	// two blocks with the same body (sculk_sensor vs calibrated_sculk_sensor, whose extra amethyst horn only widens its
	// bbox on one axis) render at the SAME height - as they do in the vanilla slot, where every block uses the same fixed
	// GUI scale. A naturally small model (conduit, ~0.6 of a full block) keeps its small proportion (0.6x of its small
	// size), NOT inflated to fill the box. An earlier version scaled to 0.6 OF THE BOX (0.6 * box/content), which inflated
	// small conduits; a min(1, box/content) cap was then added to undo that, but it ALSO over-shrank any block whose bbox
	// merely exceeds the box on one thin axis (calibrated's horn) - breaking the same-height match. The constant 0.6x
	// needs no cap: even the largest content (spore_blossom, ~1.25 box) still sits well inside the box at 0.6x. Composed
	// with (not substituted for) each layer's own localTransform, so a special renderer's model-fitting transform is kept.
	private static void shulkerInventory$applyBlockShrink(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after) {
		Matrix4f localTransform = new Matrix4f()
				.translate(BOX_CENTER, BOX_CENTER, BOX_CENTER)
				.scale(BLOCK_CONTENT_SCALE)
				.translate(-BOX_CENTER, -BOX_CENTER, -BOX_CENTER);
		for (int i = before; i < after; i++) {
			Matrix4f fit = ((LayerRenderStateAccessor) layers[i]).shulkerInventory$getLocalTransform();
			layers[i].setLocalTransform(new Matrix4f(localTransform).mul(fit));
		}
	}

	// Centre the block on the box. X and Z (horizontal) are centred in every view, so the block stays inside the box and
	// does not drift toward the hand. The HEIGHT (Y) is centred in held AND dropped views: there the box is posed
	// (tilted), so the full 3-axis centre is what keeps the block contained - dropping Y there also skews the apparent
	// X/Z under the pose rotation. In the upright GUI slot the height is NOT centred, so the block keeps its natural
	// height (a slab rests on the box floor instead of being lifted to the vertical centre). The offset is added to the
	// block's display transform translation, shifting the content in the box's shared (pre-outer-pose) space so it lands
	// centred whatever the pose is. Orientation kept.
	private static void shulkerInventory$applyBlockCentring(ItemStackRenderState.LayerRenderState[] layers, int before,
			int after, ItemDisplayContext displayContext, boolean held, boolean dropped) {
		float[] boxF = shulkerInventory$finalBbox(layers, 0, before, displayContext.leftHand());
		float[] conF = shulkerInventory$finalBbox(layers, before, after, displayContext.leftHand());
		if (boxF != null && conF != null) {
			float offX = (boxF[0] + boxF[3] - conF[0] - conF[3]) * 0.5f;
			float offZ = (boxF[2] + boxF[5] - conF[2] - conF[5]) * 0.5f;
			// Height centred in held and dropped views; in the upright GUI slot the block keeps its natural height.
			float offY = (held || dropped) ? (boxF[1] + boxF[4] - conF[1] - conF[4]) * 0.5f : 0f;
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
