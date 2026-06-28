package io.github.moncefdev.shulkerinventory.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.moncefdev.shulkerinventory.client.mixin.SpriteContentsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

// Builds, at runtime, a dissolve-capable copy of the shulker atlas. On 1.21.11 vanilla offers no separate dissolve-mask
// sampler (the entity.fsh "#ifdef DISSOLVE" block that 26.x uses does not exist). The only vanilla render type that
// dissolves, dragonExplosionAlpha, runs the entity_alpha shader: it discards a fragment where the SAMPLED TEXTURE's alpha
// is below the tint alpha. Shulker textures are fully opaque (binary alpha), so sampling them yields no gradient and no
// fade (the lid would stay solid then pop). The dragon dissolves because it samples dragon_exploding.png, whose alpha IS
// a gradient. We do the same: clone the shulker atlas but replace the alpha of every opaque texel with uniform white
// noise, keeping the RGB. dragonExplosionAlpha then sweeps the tint alpha (1 - openness) across that noise and the lid
// dissolves evenly, on a vanilla render type, with no custom shader (so it stays compatible with shader packs).
//
// One clone covers ALL colours (and modded / HD shulker textures): we copy the already-STITCHED atlas, so vanilla has
// already placed every colour at its own position and size. Each box keeps its own sprite (its own UV rect) and samples
// its own region of the clone, with the right RGB. The clone has the SAME dimensions as the source atlas, so the existing
// sprite UVs map unchanged. Regions are filled lazily, per sprite first dissolved, from the live (resource-pack-applied)
// sprite image.
//
// Resource-pack compatibility without a reload listener: the blitted set is keyed by SpriteContents IDENTITY through a
// WeakHashMap. A resource reload re-stitches the atlas and creates fresh SpriteContents, so a changed colour is no longer
// in the set and gets re-blitted (fresh pixels) the next time it dissolves; the stale entries are weakly held and GC'd.
// A pack that also changes the atlas dimensions is caught by the size check in forSprite, which reallocates the clone.
// Render-thread only (it touches the GL texture manager and the live atlas image), which is where the lid-fade mixin
// calls it.
public final class DissolveTexture {
	private DissolveTexture() {}

	private static final Identifier ID = Identifier.fromNamespaceAndPath("builders-shulkers", "shulker_dissolve_runtime");
	private static NativeImage image = null;
	private static DynamicTexture texture = null;
	// Sprite contents whose region is already copied into the clone, so we blit + re-upload only once per colour. Keyed by
	// identity and weakly held so a resource reload's fresh SpriteContents trigger a re-blit and the old ones are GC'd.
	private static final Set<SpriteContents> blitted = Collections.newSetFromMap(new WeakHashMap<>());

	// The dissolve clone's texture id, ensuring the given sprite's region carries the shulker RGB + noise alpha.
	public static Identifier forSprite(TextureAtlasSprite sprite) {
		// Derive the atlas dimensions from the sprite's UV rect (u0 = x / atlasWidth), so the clone matches the atlas and
		// the existing sprite UVs sample the same texels. TextureAtlas exposes no size getter here.
		int atlasWidth = Math.round(sprite.contents().width() / (sprite.getU1() - sprite.getU0()));
		int atlasHeight = Math.round(sprite.contents().height() / (sprite.getV1() - sprite.getV0()));
		if (image == null || image.getWidth() != atlasWidth || image.getHeight() != atlasHeight) {
			allocate(atlasWidth, atlasHeight);
		}
		if (blitted.add(sprite.contents())) {
			blit(sprite);
			texture.upload();
		}
		return ID;
	}

	private static void allocate(int width, int height) {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
		}
		blitted.clear();
		// Zero-filled: atlas regions we never blit stay transparent rather than holding uninitialised garbage.
		image = new NativeImage(width, height, true);
		texture = new DynamicTexture(() -> "builders-shulkers shulker dissolve", image);
		Minecraft.getInstance().getTextureManager().register(ID, texture);
	}

	// Copies one sprite's region into the clone: RGB verbatim, alpha replaced by uniform noise where the source was
	// opaque (transparent texels stay transparent, so empty atlas areas are never given noise).
	private static void blit(TextureAtlasSprite sprite) {
		NativeImage src = ((SpriteContentsAccessor) (Object) sprite.contents()).shulkerInventory$originalImage();
		int x0 = sprite.getX();
		int y0 = sprite.getY();
		int w = sprite.contents().width();
		int h = sprite.contents().height();
		for (int sy = 0; sy < h; sy++) {
			for (int sx = 0; sx < w; sx++) {
				int argb = src.getPixel(sx, sy);
				int alpha = (argb >>> 24) & 0xFF;
				int newAlpha = alpha == 0 ? 0 : hashAlpha(x0 + sx, y0 + sy);
				image.setPixel(x0 + sx, y0 + sy, (argb & 0x00FFFFFF) | (newAlpha << 24));
			}
		}
	}

	// Uniform 8-bit alpha per texel, from an integer bit-mix of (x, y): white noise, no stored asset, reproducible.
	private static int hashAlpha(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		h ^= h >>> 16;
		return h & 0xFF;
	}
}
