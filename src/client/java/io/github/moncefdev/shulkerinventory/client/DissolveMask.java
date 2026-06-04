package io.github.moncefdev.shulkerinventory.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

// Builds the lid dissolve mask at runtime instead of shipping a stored PNG. The dissolve shader (vanilla entity.fsh,
// "#ifdef DISSOLVE") reads ONLY the mask's ALPHA at the lid's texCoord0 and discards a fragment where
// (1 - openness) < maskAlpha. So an even dissolve needs nothing more than alpha uniformly distributed over [0,1]; the
// RGB the old 512x512 RGBA PNG carried (and three quarters of its bytes) were dead weight, and the exact noise pattern
// never mattered, only its statistical uniformity. We fill alpha from a per-texel integer hash - deterministic, no
// stored asset, no allocation of a Random - and register it once as a DynamicTexture under a runtime id. DynamicTexture
// is not a ReloadableTexture, so a resource reload (F3+T) leaves these pixels in place rather than trying to reload them
// from a (non-existent) file. Built lazily on the render thread, the first time a lid actually dissolves.
public final class DissolveMask {
	private DissolveMask() {}

	// Matches the old mask's resolution so the effective texel density over the lid's atlas sub-rectangle (and thus the
	// grain of the dissolve) is unchanged: this keeps the procedural mask a like-for-like swap for the stored one.
	private static final int SIZE = 512;
	private static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "dissolve_mask_runtime");
	private static boolean built = false;

	// The registered mask texture id, building and registering it on first use. Render-thread only (it touches the GL
	// texture manager); the lid-fade mixin that calls this already runs on the render thread.
	public static Identifier identifier() {
		if (!built) {
			NativeImage image = new NativeImage(SIZE, SIZE, false);
			for (int y = 0; y < SIZE; y++) {
				for (int x = 0; x < SIZE; x++) {
					// ABGR packing: alpha in the high byte, RGB left white (unread by the shader).
					image.setPixelABGR(x, y, (hashAlpha(x, y) << 24) | 0x00FFFFFF);
				}
			}
			Minecraft.getInstance().getTextureManager()
					.register(ID, new DynamicTexture(() -> "builders-shulkers dissolve mask", image));
			built = true;
		}
		return ID;
	}

	// A uniform 8-bit alpha per texel, from an integer bit-mix of (x, y). Neighbouring texels come out uncorrelated
	// (white noise) and the whole field is reproducible with no stored data and no per-frame randomness.
	private static int hashAlpha(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return h & 0xFF;
	}
}
