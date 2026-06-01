package io.github.moncefdev.shulkerinventory.client;

import io.github.moncefdev.shulkerinventory.ShulkerAnimationMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

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

	public static void startOpening(long animationId) {
		pendingIdForScreen = animationId;
		pendingIdTtl = PENDING_OPEN_GRACE_TICKS;
		AnimationState anim = animations.computeIfAbsent(animationId, k -> new AnimationState());
		anim.status = AnimationStatus.OPENING;
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

	// Plays the opener's OWN shulker open/close sound, locally at their own position. The opener always hears both
	// sounds, whether the shulker is held or buried in the inventory; this is deliberately independent of the
	// held-gated broadcast that carries the sound to OTHER players (who only hear it for a visibly held shulker).
	public static void playOwnSound(boolean opening) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) return;
		mc.level.playLocalSound(mc.player,
				opening ? SoundEvents.SHULKER_BOX_OPEN : SoundEvents.SHULKER_BOX_CLOSE,
				SoundSource.BLOCKS, 0.5f, mc.level.getRandom().nextFloat() * 0.1f + 0.9f);
	}

	public static boolean isAnimating(long animationId) {
		return animations.containsKey(animationId);
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
}
