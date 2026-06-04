package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
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
	}

	public record AnimationMarker(long animationId) {}

	private static final Map<Long, AnimationState> animations = new HashMap<>();
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
		if (armScreen) {
			pendingIdForScreen = newId;
			pendingIdTtl = PENDING_OPEN_GRACE_TICKS;
		}
		return newId;
	}

	// Starts an animation that mirrors another player's shulker (driven by a server broadcast), with NO pending
	// screen: this client has no shulker screen for it, so it must not arm the orphan-cleanup that startOpening
	// uses to drop a local open that no screen claimed.
	public static void startOpeningRemote(long animationId) {
		AnimationState anim = animations.computeIfAbsent(animationId, k -> new AnimationState());
		anim.status = AnimationStatus.OPENING;
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
}
