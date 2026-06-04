package io.github.moncefdev.shulkerinventory.client.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Reads the sprite getter baked into a ShulkerBoxRenderer, so a fresh ShulkerBoxRenderer (with its OWN model) can be
// built with the same sprites for a Pocket-Build content shulker (see ShulkerBoxSpecialRendererAccessor).
@Mixin(ShulkerBoxRenderer.class)
public interface ShulkerBoxRendererAccessor {
	@Accessor("sprites")
	SpriteGetter shulkerInventory$getSprites();
}
