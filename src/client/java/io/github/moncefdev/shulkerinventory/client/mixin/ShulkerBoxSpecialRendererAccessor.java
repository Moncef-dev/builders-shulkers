package io.github.moncefdev.shulkerinventory.client.mixin;

import net.minecraft.client.renderer.blockentity.ShulkerBoxRenderer;
import net.minecraft.client.renderer.special.ShulkerBoxSpecialRenderer;
import net.minecraft.client.resources.model.sprite.SpriteId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Reads a shulker special renderer's colour sprite and its underlying block-entity renderer, so a SEPARATE-model
// renderer instance can be built for a shulker drawn as Pocket-Build content. A content shulker that shares the box's
// model (same colour) would otherwise share its single lid pose: vanilla resolves the lid at draw from that one shared
// model, so the box and the nested shulker cannot show different openness. A distinct model decouples them.
@Mixin(ShulkerBoxSpecialRenderer.class)
public interface ShulkerBoxSpecialRendererAccessor {
	@Accessor("sprite")
	SpriteId shulkerInventory$getSprite();

	@Accessor("shulkerBoxRenderer")
	ShulkerBoxRenderer shulkerInventory$getShulkerBoxRenderer();
}
