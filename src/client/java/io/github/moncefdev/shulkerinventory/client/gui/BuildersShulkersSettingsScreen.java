package io.github.moncefdev.shulkerinventory.client.gui;

import com.mojang.serialization.Codec;
import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

// The mod's client settings screen, built on the vanilla OptionsSubScreen so it looks and behaves exactly like the
// vanilla Video/Accessibility screens (scrollable OptionsList + a Done button). Opened by the settings keybind. Each
// option carries a descriptive tooltip, and the values drive the matching ClientConfig fields (harvested on close).
// Cross-version notes: the toggles use createBoolean's 2-/3-arg overloads; the Open/Closed and the lid-effect (enum
// cycle) options use the multi-arg constructor with a no-op listener that target-types to either 26.1.2's Consumer or
// 26.2's ValueUpdateListener; the count-size control is a plain AbstractSliderButton (its OptionInstance equivalent's
// callback differs between versions).
public class BuildersShulkersSettingsScreen extends OptionsSubScreen {
	private final OptionInstance<Boolean> inventoryAnimation;
	private final OptionInstance<Boolean> pocketBuildAnimation;
	private final OptionInstance<ClientConfig.LidEffect> lidEffect;
	private final OptionInstance<Boolean> inBoxContent;
	private final OptionInstance<Boolean> openCloseSounds;
	private final OptionInstance<Boolean> otherPlayerAnimations;
	private final OptionInstance<Boolean> inventoryLidOpen;
	private final OptionInstance<Boolean> pocketBuildLidOpen;
	private final OptionInstance<Boolean> showItemCount;
	private final ItemCountSizeSlider itemCountSize;

	public BuildersShulkersSettingsScreen(Screen lastScreen) {
		super(lastScreen, Minecraft.getInstance().options, Component.translatable("screen.builders-shulkers.settings.title"));
		ClientConfig c = ClientConfig.get();
		this.inventoryAnimation = toggle("screen.builders-shulkers.settings.inventory_animation", c.inventoryAnimation);
		this.pocketBuildAnimation = toggle("screen.builders-shulkers.settings.pocket_build_animation", c.pocketBuildAnimation);
		this.lidEffect = lidEffectOption("screen.builders-shulkers.settings.lid_effect", c.lidEffect);
		this.inBoxContent = toggle("screen.builders-shulkers.settings.in_box_content", c.inBoxContent);
		this.openCloseSounds = toggle("screen.builders-shulkers.settings.open_close_sounds", c.openCloseSounds);
		this.otherPlayerAnimations = toggle("screen.builders-shulkers.settings.other_player_animations", c.otherPlayerAnimations);
		this.inventoryLidOpen = openClosedOption("screen.builders-shulkers.settings.inventory_lid_open", c.inventoryLidOpenWhenAnimationOff);
		this.pocketBuildLidOpen = openClosedOption("screen.builders-shulkers.settings.pocket_build_lid_open", c.pocketBuildLidOpenWhenAnimationOff);
		this.showItemCount = toggle("screen.builders-shulkers.settings.show_item_count", c.showItemCount);
		this.itemCountSize = new ItemCountSizeSlider(c.itemCountSize);
	}

	@Override
	protected void addOptions() {
		this.list.addSmall(
				inventoryAnimation, pocketBuildAnimation,
				inventoryLidOpen, pocketBuildLidOpen,
				lidEffect, inBoxContent,
				openCloseSounds, otherPlayerAnimations);
		// "Show item count" and its size slider share the last row (5 rows of 2).
		this.list.addSmall(showItemCount.createButton(this.minecraft.options), itemCountSize);
	}

	@Override
	public void onClose() {
		// Harvest the values into the config and persist (the controls carry no live callback here).
		ClientConfig c = ClientConfig.get();
		c.inventoryAnimation = inventoryAnimation.get();
		c.pocketBuildAnimation = pocketBuildAnimation.get();
		c.lidEffect = lidEffect.get();
		c.inBoxContent = inBoxContent.get();
		c.openCloseSounds = openCloseSounds.get();
		c.otherPlayerAnimations = otherPlayerAnimations.get();
		c.inventoryLidOpenWhenAnimationOff = inventoryLidOpen.get();
		c.pocketBuildLidOpenWhenAnimationOff = pocketBuildLidOpen.get();
		c.showItemCount = showItemCount.get();
		c.itemCountSize = itemCountSize.countSize();
		ClientConfig.save();
		super.onClose();
	}

	// An ON/OFF toggle with a descriptive tooltip (key + ".tooltip"). The 3-arg createBoolean is identical on 26.1.2/26.2.
	private static OptionInstance<Boolean> toggle(String key, boolean initial) {
		return OptionInstance.createBoolean(key,
				OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				initial);
	}

	// The Pocket-Build lid effect, a 3-value cycle (None / Dissolve / Disappear). The Enum ValueSet needs a Codec, built
	// trivially from the enum name. The constructor's last parameter (the change callback) differs between 26.1.2
	// (Consumer) and 26.2 (ValueUpdateListener), but the no-op lambda target-types to either, like the boolean options;
	// the value is harvested on close. The CaptionBasedToString returns only the value label (the framework prepends the
	// caption), keyed as "<key>.none" / ".dissolve" / ".disappear".
	private static final Codec<ClientConfig.LidEffect> LID_EFFECT_CODEC =
			Codec.STRING.xmap(ClientConfig.LidEffect::valueOf, ClientConfig.LidEffect::name);
	private static final OptionInstance.Enum<ClientConfig.LidEffect> LID_EFFECT_VALUES =
			new OptionInstance.Enum<>(List.of(ClientConfig.LidEffect.values()), LID_EFFECT_CODEC);

	private static OptionInstance<ClientConfig.LidEffect> lidEffectOption(String key, ClientConfig.LidEffect initial) {
		return new OptionInstance<>(key,
				OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				(caption, value) -> Component.translatable(key + "." + value.name().toLowerCase(Locale.ROOT)),
				LID_EFFECT_VALUES,
				initial,
				newValue -> {});
	}

	// A boolean option whose value reads "Open"/"Closed" instead of "ON"/"OFF", with a tooltip. The CaptionBasedToString
	// returns only the value (the framework prepends the caption). The 5-arg createBoolean's callback parameter differs
	// between 26.1.2 (Consumer) and 26.2 (ValueUpdateListener), but the no-op lambda target-types to either, so the same
	// source compiles on both; the value is harvested on close, so no live callback is needed.
	private static OptionInstance<Boolean> openClosedOption(String key, boolean initial) {
		return OptionInstance.createBoolean(key,
				OptionInstance.cachedConstantTooltip(Component.translatable(key + ".tooltip")),
				(caption, value) -> Component.translatable(
						value ? "screen.builders-shulkers.settings.lid_open" : "screen.builders-shulkers.settings.lid_closed"),
				initial,
				newValue -> {});
	}

	// Plain vanilla slider for the selected-content count scale, 50%..100% (the OptionInstance slider constructor's
	// callback parameter differs between 26.1.2 and 26.2, so a direct AbstractSliderButton is used). The OptionsList
	// arranges and resizes it; the normalized 0..1 value maps onto the 0.5..1.0 scale.
	private static final class ItemCountSizeSlider extends AbstractSliderButton {
		private static final double MIN = 0.5;
		private static final double MAX = 1.0;

		ItemCountSizeSlider(double countSize) {
			super(0, 0, 150, 20, Component.empty(), (countSize - MIN) / (MAX - MIN));
			updateMessage();
			setTooltip(Tooltip.create(Component.translatable("screen.builders-shulkers.settings.item_count_size.tooltip")));
		}

		double countSize() {
			return MIN + this.value * (MAX - MIN);
		}

		@Override
		protected void updateMessage() {
			setMessage(Component.translatable("screen.builders-shulkers.settings.item_count_size",
					Math.round(countSize() * 100) + "%"));
		}

		@Override
		protected void applyValue() {
			// The value lives in this.value and is harvested on screen close; nothing to apply live.
		}
	}
}
