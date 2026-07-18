package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

// Client-side state for shulker lid animations. Allocates a unique id per open request, tracks each animation
// through OPENING -> OPENED -> CLOSING, and exposes the "currently rendering" id to the render mixins via the
// thread-confined fields below (a side channel, because the vanilla item render path is stateless).
public final class ClientShulkerSession {
	private ClientShulkerSession() {}

	public enum AnimationStatus { OPENING, OPENED, CLOSING }

	public static final class AnimationState {
		float progress;
		float progressOld;
		AnimationStatus status = AnimationStatus.OPENING;
		// The Pocket-Build selected content to draw inside this box, or EMPTY for a normal (inventory) animation. Held
		// on the animation (not on PocketBuildMode) so it survives the whole close: the mode goes inactive the instant
		// you leave, but the closing lid still plays, and the content must stay visible until that animation finishes.
		ItemStack pocketBuildContent = ItemStack.EMPTY;
		// Whether this animation belongs to a Pocket-Build box (vs a normal inventory open). Drives the lid dissolve,
		// which must happen for the whole Pocket-Build open even when the selected slot is empty (no content to draw):
		// gating the dissolve on the content would leave the lid opaque on an empty selection.
		boolean pocketBuild = false;
		// A model instance unique to THIS animation (lazily created on first render), so the animated box's lid is not
		// mutualised with another same-colour shulker rendered in the same frame. The shulker model is shared per colour
		// and its lid position is resolved at DRAW, so the last setupAnim of the frame wins: a second held shulker of the
		// same colour (even static, openness 0) would otherwise reset this box's lid to closed at draw. Lives on the
		// animation, so it is freed with it when it drains - no separate cleanup.
		ShulkerBoxSpecialRenderer separatedBoxRenderer = null;
		// A LOCAL open (the local player's own, via beginHeldOpening), as opposed to a remote one mirrored from a
		// broadcast. Only local opens get the deferred-progress treatment below; remote viewers keep advancing on tick so
		// a box that opens off-screen is already open when looked at.
		boolean local = false;
		// Whether the render path has followed this animation at least once (the openness override marks it). A local open
		// holds its OPENING progress until this flips true, so it starts lifting from where the render first picks it up
		// rather than from wherever it silently ticked to during the open round-trip (the server writes the animation_id
		// marker onto the held stack and syncs it back only a round-trip later; until then the hand has no marker to
		// resolve, so it cannot follow the animation - and the lid used to jump from 0 to ~0.1 the instant it could).
		boolean rendered = false;
	}

	public record AnimationMarker(long animationId) {}

	private static final Map<Long, AnimationState> animations = new HashMap<>();
	// The animation currently broadcast for each holder entity (REMOTE animations only). A holder has at most one held
	// shulker, hence at most one live animation: when a new open arrives for a holder, its previous animation is drained
	// here. Without this, a holder repeatedly opening then moving the shulker out of hand (whose close is held-gated and
	// may never reach this viewer) would leak one orphaned AnimationState - and its per-id box model - per cycle, an
	// abuse vector. Cleared on disconnect with the rest.
	private static final Map<Integer, Long> holderToAnim = new HashMap<>();
	private static final WeakHashMap<Screen, Long> screenIdMap = new WeakHashMap<>();
	private static Long pendingIdForScreen = null;
	// A pending open waits at most this many ticks for a shulker screen to claim it; if none does (e.g. a
	// re-click the server turned into a close, or a rejected open), tick() drops the orphan so a later real
	// shulker screen cannot inherit a stale animation.
	private static int pendingIdTtl = 0;
	private static final int PENDING_OPEN_GRACE_TICKS = 10;
	// Side channel: the render mixins set these to the animation id they are about to draw, so the shulker
	// openness mixin (which only receives a float arg) can resolve the matching progress. Render-thread only.
	private static long currentAtlasRenderingId = 0L;
	private static long currentItemEntityAnimationId = 0L;

	public static void setCurrentItemEntityAnimationId(long id) {
		currentItemEntityAnimationId = id;
	}

	public static long getCurrentItemEntityAnimationId() {
		return currentItemEntityAnimationId;
	}

	// Render-thread flag: true only while the live Pocket-Build item shulker's special model is being submitted. The
	// lid-fade mixin sits on ShulkerBoxRenderer, which also renders world block entities, so it must act ONLY when this
	// flag is set (i.e. the item special-render path for our Pocket-Build shulker), never on a placed shulker block.
	private static boolean pocketBuildLidRendering = false;

	public static void setPocketBuildLidRendering(boolean value) {
		pocketBuildLidRendering = value;
	}

	public static boolean isPocketBuildLidRendering() {
		return pocketBuildLidRendering;
	}

	// Render-thread: the live animation's RAW progress (the dissolve fade amount), set by the openness override just
	// before the shulker submit and read by the lid-fade mixin. Kept separate from the openness ARG passed to the
	// renderer (which carries the lid POSITION, frozen at the resting lid state when that animation type's animation is
	// off), so the lid can keep dissolving while it no longer lifts ("dissolve, no animation").
	private static float currentDissolveProgress = 0.0f;

	public static void setCurrentDissolveProgress(float progress) {
		currentDissolveProgress = progress;
	}

	public static float currentDissolveProgress() {
		return currentDissolveProgress;
	}

	// Render-thread: the live animation's lifecycle status, published by the openness override alongside the raw progress
	// and read by the lid-fade mixin. The DISAPPEAR effect keys on THIS (binary: gone while opening/open, present while
	// closing), not on the progress, so it stays instant in both directions and decoupled from the lift animation.
	private static AnimationStatus currentRenderStatus = null;

	public static void setCurrentRenderStatus(AnimationStatus status) {
		currentRenderStatus = status;
	}

	public static AnimationStatus currentRenderStatus() {
		return currentRenderStatus;
	}

	// Render-thread: the lid POSITION the openness override last returned for the live box (0 = closed, 1 = open). Read at
	// the content layer's submit to decide whether the in-box content is covered. Below this the lid counts as closed.
	private static final float LID_CLOSED_OPENNESS = 1.0e-3f;
	// Render-thread: at/below this dissolve progress the DISSOLVE lid is treated as fully re-materialised (opaque).
	private static final float LID_OPAQUE_DISSOLVE = 1.0e-3f;
	private static float currentLidOpenness = 0.0f;

	public static void setCurrentLidOpenness(float openness) {
		currentLidOpenness = openness;
	}

	// Whether the Pocket-Build content should be skipped this frame because the lid fully covers it: the lid is at the
	// closed POSITION (openness 0) AND completely OPAQUE (no dissolve/disappear revealing the interior). Without this the
	// content keeps rendering through the whole close drain and pokes out of a visually-closed box. The box layer's
	// openness override runs before the content layers (appended after the box), so the fields read here are fresh.
	public static boolean contentCoveredByClosedLid() {
		if (currentLidOpenness > LID_CLOSED_OPENNESS) {
			return false;
		}
		return switch (ClientConfig.get().lidEffect) {
			case NONE -> true;
			case DISSOLVE -> currentDissolveProgress <= LID_OPAQUE_DISSOLVE;
			case DISAPPEAR -> currentRenderStatus == AnimationStatus.CLOSING;
		};
	}

	// A random non-zero long, so ids are globally unique across clients. A per-client sequential counter would
	// restart at the same values on every client, so in multiplayer a shulker carrying client A's id could collide
	// with an unrelated animation client B is tracking under the same id, making B render A's shulker as open. Zero
	// is reserved as the "no id" sentinel for the render side channel.
	public static long allocateId() {
		long id;
		do {
			id = ThreadLocalRandom.current().nextLong();
		} while (id == 0L);
		return id;
	}

	// Begin the lid OPENING animation, with a freshly-allocated id, for a shulker the local player just opened (the
	// inventory GUI, or Pocket-Build), resuming from the stack's CURRENT animation openness so a quick reopen
	// continues from where the closing lid is instead of snapping shut to 0 first. A stack that is not mid-animation
	// resumes from 0 (opens from closed). armScreen also arms the pending-screen cleanup used by the GUI flow;
	// Pocket-Build has no screen and passes false. Returns the new id to send to the server. Shared by both local
	// open modes, so the resume behaviour can never diverge between them again.
	public static long beginHeldOpening(ItemStack stack, boolean armScreen) {
		Long currentId = getAnimationIdForStack(stack);
		float resume = currentId != null ? currentProgress(currentId) : 0f;
		long newId = allocateId();
		AnimationState anim = animations.computeIfAbsent(newId, k -> new AnimationState());
		anim.status = AnimationStatus.OPENING;
		anim.progress = resume;
		anim.progressOld = resume;
		// Local open: for the SCREEN mode, hold the OPENING progress until the render first follows it (see
		// AnimationState.rendered), so the lid starts lifting from 0 instead of bumping to wherever it ticked
		// during the menu round-trip. The Pocket-Build mode needs no hold: its immediate-association bridge (see
		// getAnimationIdForStack) links the render on the very next frame, and holding here cost the open exactly
		// one tick per cycle that the close never lost - rapid open/close clicking visibly ratcheted the lid
		// toward closed. So it starts already-rendered and advances from the click's own tick, symmetric with the
		// close.
		anim.local = true;
		anim.rendered = !armScreen;
		if (armScreen) {
			pendingIdForScreen = newId;
			pendingIdTtl = PENDING_OPEN_GRACE_TICKS;
		}
		return newId;
	}

	// Starts an animation that mirrors another player's shulker (driven by a server broadcast), with NO pending
	// screen: this client has no shulker screen for it, so it must not arm the orphan-cleanup that startOpening
	// uses to drop a local open that no screen claimed.
	public static void startOpeningRemote(long animationId, int holderEntityId) {
		// One held shulker per holder means one live animation: drain this holder's previous one if any. A leftover is an
		// orphan (e.g. an earlier open whose held-gated close never reached this viewer), so replacing it here bounds the
		// per-holder state to one and removes the open/move-out-of-hand spam as a memory-leak vector.
		// Resume from the previous animation's openness BEFORE dropping it, so a quick re-open continues from where the
		// closing lid is - matching what the opener sees (beginHeldOpening) - instead of snapping back to 0 for viewers.
		Long previous = holderToAnim.put(holderEntityId, animationId);
		float resume = 0f;
		if (previous != null && previous != animationId) {
			resume = currentProgress(previous);
			animations.remove(previous);
		}
		AnimationState anim = animations.computeIfAbsent(animationId, k -> new AnimationState());
		anim.status = AnimationStatus.OPENING;
		anim.progress = resume;
		anim.progressOld = resume;
	}

	public static void startClosing(long animationId) {
		AnimationState anim = animations.get(animationId);
		if (anim != null && anim.status != AnimationStatus.CLOSING) {
			anim.status = AnimationStatus.CLOSING;
		}
	}

	// Steps every active animation once per client tick: OPENING fills up, CLOSING drains and, when it reaches
	// zero, drops the animation so it stops being rendered.
	public static void tick() {
		// Drop an orphaned pending open: if no shulker screen claimed it within the grace window, it was a
		// re-click the server turned into a close (or a rejected open). Remove its dangling state so nothing
		// leaks and a later screen cannot inherit it.
		if (pendingIdForScreen != null && --pendingIdTtl <= 0) {
			animations.remove(pendingIdForScreen);
			pendingIdForScreen = null;
		}
		Iterator<Map.Entry<Long, AnimationState>> it = animations.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<Long, AnimationState> entry = it.next();
			AnimationState anim = entry.getValue();
			anim.progressOld = anim.progress;
			switch (anim.status) {
				case OPENING -> {
					// A local open does not advance until the render has followed it once, so it starts lifting from 0
					// the moment it becomes renderable (marker synced / screen associated) rather than mid-animation.
					if (anim.local && !anim.rendered) {
						break;
					}
					anim.progress += 0.1f;
					if (anim.progress >= 1f) {
						anim.progress = 1f;
						anim.status = AnimationStatus.OPENED;
					}
				}
				case OPENED -> {}
				case CLOSING -> {
					anim.progress -= 0.1f;
					if (anim.progress <= 0f) {
						it.remove();
					}
				}
			}
		}
	}

	// Plays the shulker open/close sound at an entity's position, with vanilla's volume and slight pitch jitter.
	// Shared by the opener's own sound and the broadcast sound played for OTHER players' held shulkers, so the two
	// can never drift apart.
	public static void playShulkerSound(Level level, Entity at, boolean opening) {
		// Client setting: mute the open/close sounds (own and other players') when disabled.
		if (!ClientConfig.get().openCloseSounds) {
			return;
		}
		level.playLocalSound(at,
				opening ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE,
				SoundSource.BLOCKS, 0.5f, level.getRandom().nextFloat() * 0.1f + 0.9f);
	}

	// Plays the opener's OWN shulker open/close sound, locally at their own position. The opener always hears both
	// sounds, whether the shulker is held or buried in the inventory; this is deliberately independent of the
	// held-gated broadcast that carries the sound to OTHER players (who only hear it for a visibly held shulker).
	public static void playOwnSound(boolean opening) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		playShulkerSound(mc.level, mc.player, opening);
	}

	public static boolean isAnimating(long animationId) {
		return animations.containsKey(animationId);
	}

	// Marks that the render path has followed this animation, releasing a deferred local open so it can start advancing
	// from where the render first picked it up (see AnimationState.rendered). Called by the openness override each time it
	// resolves the box. No-op once already set, or if the animation has ended.
	public static void markRendered(long animationId) {
		AnimationState anim = animations.get(animationId);
		if (anim != null) {
			anim.rendered = true;
		}
	}

	// The animation's current lifecycle status (OPENING / OPENED / CLOSING), or null if there is no live animation for
	// this id. Lets the openness override snap the lid shut at the START of a close when the lift animation is off,
	// instead of lingering at the resting state while the close drains.
	public static AnimationStatus getStatus(long animationId) {
		AnimationState anim = animations.get(animationId);
		return anim == null ? null : anim.status;
	}

	// The animation's current logical openness (0..1), or 0 if there is no live animation for this id. Used to
	// resume a reopen from wherever the closing lid currently is.
	public static float currentProgress(long animationId) {
		AnimationState anim = animations.get(animationId);
		return anim == null ? 0f : anim.progress;
	}

	public static float getInterpolatedProgress(long animationId, float partialTick) {
		AnimationState anim = animations.get(animationId);
		if (anim == null) return 0f;
		return Mth.lerp(partialTick, anim.progressOld, anim.progress);
	}

	public static Long getAnimationIdForStack(ItemStack stack) {
		return ShulkerAnimationMarker.get(stack);
	}

	// Resolver for HELD-item render paths only (first person, and the local player's own content append in a hand
	// context): the marker, or the Pocket-Build immediate-association bridge. The client creates the open
	// animation at the click, but the marker that links a stack to it is written by the SERVER and only lands
	// after the payload round-trip - so the held lid sat still for that window, and rapid open/close cycling
	// ratcheted the lid toward closed (a stale marker from the previous session even kept the render on the OLD
	// closing animation). Until the identity guard confirms the mode's marker landed, a stack that matches the
	// mode's box (full equality against a marker-stripped snapshot) held by the LOCAL PLAYER resolves to the
	// mode's own animation id, overriding a stale marker. The holder gate is what makes the equality match safe:
	// every OTHER render context (GUI slots, item entities, other players' hands) uses the plain marker path
	// above, so equal-content boxes - every empty shulker of a colour is bit-identical - never twitch in the
	// hotbar or on other players. Strictly read-only (no stack is ever mutated mid-session - the 1.0.7 ghost
	// lesson); a mismatch degrades to the old sub-tick latency, never to a wrong association.
	public static Long resolveHeldAnimationId(ItemStack stack, Object holder) {
		if (PocketBuildMode.isActive() && !PocketBuildClient.isHeldMarkerConfirmed()
				&& holder == net.minecraft.client.Minecraft.getInstance().player
				&& PocketBuildMode.matchesModeBox(stack)) {
			return PocketBuildMode.animationId();
		}
		return ShulkerAnimationMarker.get(stack);
	}

	public static void associateScreenWithPendingId(Screen screen) {
		if (pendingIdForScreen != null) {
			screenIdMap.put(screen, pendingIdForScreen);
			pendingIdForScreen = null;
		}
	}

	public static Long getIdForScreen(Screen screen) {
		return screenIdMap.get(screen);
	}

	public static void setCurrentAtlasRenderingId(long id) {
		currentAtlasRenderingId = id;
	}

	public static long getCurrentAtlasRenderingId() {
		return currentAtlasRenderingId;
	}

	// True only while a layer that is Pocket-Build CONTENT (not the box itself) is being submitted - set by the layer
	// submit mixin from a per-layer flag. The animated lid openness and the lid dissolve belong to the OUTER box only; a
	// shulker drawn INSIDE it as content must stay closed/static. Keying on this per-layer flag is deterministic, unlike
	// the old "first shulker drawn wins" approach, which broke when the box and a same-colour nested shulker batched
	// together and the render reordered them. Render-thread only.
	private static boolean renderingContentLayer = false;

	public static void setRenderingContentLayer(boolean value) {
		renderingContentLayer = value;
	}

	public static boolean isRenderingContentLayer() {
		return renderingContentLayer;
	}

	// The animation id currently being rendered, resolved from the render side channel (the GUI atlas draw, or a
	// held/dropped item draw), or 0 if none. Lets a render mixin know WHICH shulker animation is on screen right now
	// (e.g. to draw the Pocket-Build selected content only inside the local player's own animated shulker).
	public static long currentRenderingId() {
		return currentAtlasRenderingId != 0L ? currentAtlasRenderingId : currentItemEntityAnimationId;
	}

	// Sets the Pocket-Build content drawn inside the given animation's box (a copy; EMPTY clears it). No-op if the
	// animation no longer exists. Refreshed while the mode is active so it tracks the scroll selection, then left
	// frozen on the animation through the close.
	public static void setPocketBuildContent(long animationId, ItemStack content) {
		AnimationState anim = animations.get(animationId);
		if (anim != null) {
			anim.pocketBuildContent = (content == null || content.isEmpty()) ? ItemStack.EMPTY : content.copy();
		}
	}

	// The Pocket-Build content to draw inside the given animation's box, or EMPTY (no content, or not a Pocket-Build
	// animation, or the animation has ended).
	public static ItemStack getPocketBuildContent(long animationId) {
		AnimationState anim = animations.get(animationId);
		return anim == null ? ItemStack.EMPTY : anim.pocketBuildContent;
	}

	// Tags the animation as a Pocket-Build box (set once when the mode opens it). Stays set through the close.
	public static void markPocketBuild(long animationId) {
		AnimationState anim = animations.get(animationId);
		if (anim != null) {
			anim.pocketBuild = true;
		}
	}

	// Whether the given animation is a Pocket-Build box. Drives the lid dissolve, which must run for the whole
	// Pocket-Build open regardless of whether the selected slot has content.
	public static boolean isPocketBuild(long animationId) {
		AnimationState anim = animations.get(animationId);
		return anim != null && anim.pocketBuild;
	}

	// The model instance unique to this animation, lazily created via the factory on first render and cached on the
	// AnimationState. Returns null if the animation no longer exists (then the layer keeps the shared vanilla model).
	// See AnimationState.separatedBoxRenderer for why the per-id model is needed.
	public static ShulkerBoxSpecialRenderer separatedBoxRenderer(long animationId, Supplier<ShulkerBoxSpecialRenderer> factory) {
		AnimationState anim = animations.get(animationId);
		if (anim == null) {
			return null;
		}
		if (anim.separatedBoxRenderer == null) {
			anim.separatedBoxRenderer = factory.get();
		}
		return anim.separatedBoxRenderer;
	}

	// Drop all client animation state. Called on disconnect / world change so orphaned animations (a holder whose
	// held-gated close never arrived, then left view) and their per-id box models never accumulate across sessions.
	public static void clearAll() {
		animations.clear();
		holderToAnim.clear();
	}
}
