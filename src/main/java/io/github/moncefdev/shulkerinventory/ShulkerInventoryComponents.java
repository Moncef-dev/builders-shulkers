package io.github.moncefdev.shulkerinventory;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public final class ShulkerInventoryComponents {
	// Transient marker placed on a shulker stack while its lid animation plays. The client reads it
	// to decide which stack to animate, and it is removed once the close animation reports finished.
	// ignoreSwapAnimation() keeps the held-item swap animation from firing when only this value changes.
	public static final DataComponentType<Long> ANIMATION_ID =
			DataComponentType.<Long>builder()
					.persistent(Codec.LONG)
					.networkSynchronized(ByteBufCodecs.VAR_LONG.cast())
					.ignoreSwapAnimation()
					.build();

	private ShulkerInventoryComponents() {}

	public static void register() {
		Registry.register(
				BuiltInRegistries.DATA_COMPONENT_TYPE,
				Identifier.fromNamespaceAndPath("shulker-inventory", "animation_id"),
				ANIMATION_ID);
	}
}
