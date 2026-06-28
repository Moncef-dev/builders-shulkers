package io.github.moncefdev.shulkerinventory.client.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.resources.model.MaterialSet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Reads the material set baked into a ShulkerBoxRenderer, so a fresh ShulkerBoxRenderer (with its OWN model) can be
// built with the same sprites for a Pocket-Build content shulker (see ShulkerBoxSpecialRendererAccessor). 1.21.11 stores
// a MaterialSet in the "materials" field (26.x renamed it to a SpriteGetter in "sprites").
@Mixin(ShulkerBoxRenderer.class)
public interface ShulkerBoxRendererAccessor {
	@Accessor("materials")
	MaterialSet shulkerInventory$getSprites();
}
