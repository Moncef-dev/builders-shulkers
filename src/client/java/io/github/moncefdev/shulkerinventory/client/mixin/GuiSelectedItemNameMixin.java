package io.github.moncefdev.shulkerinventory.client.mixin;

import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.client.PocketBuildMode;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// While in Pocket-Build mode, the hotbar item-name popup should name the SELECTED CONTENT of the held shulker, not the
// shulker itself. Hud.tick() reads the held stack via Inventory.getSelectedItem() to drive that popup (it pops and
// fades whenever that stack changes). We redirect just that one read to the selected content, so the whole vanilla
// behaviour emerges on the content: it pops when you scroll to a different item, fades like vanilla, uses the rarity
// colour, and shows the name only. Outside the mode (or for a non-shulker) the real held stack is returned unchanged.
@Mixin(Hud.class)
public abstract class GuiSelectedItemNameMixin {
	@Redirect(method = "tick()V", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;getSelectedItem()Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack shulkerInventory$nameSelectedContent(Inventory inventory) {
		ItemStack held = inventory.getSelectedItem();
		if (PocketBuildMode.isActive() && ShulkerContents.isShulker(held)) {
			return PocketBuildMode.selectedStack(held);
		}
		return held;
	}
}
