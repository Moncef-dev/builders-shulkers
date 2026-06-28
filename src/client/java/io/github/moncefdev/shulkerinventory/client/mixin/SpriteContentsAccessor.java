package io.github.moncefdev.shulkerinventory.client.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Read-only access to a sprite's source image (resource-pack-applied, pre-stitch) so the dissolve clone can copy the
// shulker atlas region per colour. Never mutates the sprite. See DissolveTexture.
@Mixin(SpriteContents.class)
public interface SpriteContentsAccessor {
	@Accessor("originalImage")
	NativeImage shulkerInventory$originalImage();
}
