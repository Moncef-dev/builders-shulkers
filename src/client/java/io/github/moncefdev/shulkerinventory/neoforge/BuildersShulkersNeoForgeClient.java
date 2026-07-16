package io.github.moncefdev.shulkerinventory.neoforge;

import io.github.moncefdev.shulkerinventory.client.BuildersShulkersKeybinds;
import io.github.moncefdev.shulkerinventory.client.ClientConfig;
import io.github.moncefdev.shulkerinventory.client.PocketBuildClient;
import io.github.moncefdev.shulkerinventory.client.PocketBuildOverlay;
import io.github.moncefdev.shulkerinventory.client.ShulkerInventoryClient;
import io.github.moncefdev.shulkerinventory.client.platform.ClientPlatform;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

// NeoForge client entrypoint (dist-gated: never constructed on a dedicated server): installs the NeoForge client
// networking implementation and the config directory, then wires the shared client-side logic into NeoForge's
// client events (tick, login/logout, interact, gui layers, key mappings).
@Mod(value = BuildersShulkersNeoForge.MOD_ID, dist = Dist.CLIENT)
public final class BuildersShulkersNeoForgeClient {
	public BuildersShulkersNeoForgeClient(IEventBus modBus) {
		// Install the loader services FIRST, so every later step (and anything it triggers) can use them.
		ClientPlatform.setConfigDir(FMLPaths.CONFIGDIR.get());
		ClientPlatform.setNetwork(new ClientPlatform.Network() {
			@Override
			public void send(CustomPacketPayload payload) {
				ClientPacketDistributor.sendToServer(payload);
			}

			@Override
			public boolean canSend(CustomPacketPayload.Type<?> type) {
				var connection = Minecraft.getInstance().getConnection();
				return connection != null && connection.hasChannel(type);
			}
		});

		// Load the client cosmetic settings (animations, dissolve, sounds, ...) from the config directory.
		ClientConfig.load();

		modBus.addListener(RegisterKeyMappingsEvent.class, event -> {
			// Settings keybind (Options -> Controls -> Builder's Shulkers): default B, opens the settings screen.
			for (KeyMapping mapping : BuildersShulkersKeybinds.createKeyMappings()) {
				event.register(mapping);
			}
			event.registerCategory(BuildersShulkersKeybinds.category());
		});

		// Peek overlay: draw the shulker contents grid right above the vanilla hotbar (shown while Peek is held).
		// PocketBuildOverlay.render(GuiGraphicsExtractor, DeltaTracker) matches GuiLayer.render exactly.
		modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAbove(VanillaGuiLayers.HOTBAR,
				Identifier.fromNamespaceAndPath("builders-shulkers", "pocket_build_overlay"),
				PocketBuildOverlay::render));

		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class,
				event -> ShulkerInventoryClient.onJoin());
		NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class,
				event -> ShulkerInventoryClient.onDisconnect());

		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Pre.class,
				event -> PocketBuildClient.startClientTick(Minecraft.getInstance()));
		// One Post listener calling the shared end-of-tick handlers in the same order the fabric entrypoint
		// registers them: lid animations, Pocket-Build exit checks, keybind handling, deferred server-mod check.
		NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> {
			Minecraft mc = Minecraft.getInstance();
			ShulkerInventoryClient.tickAnimations(mc);
			PocketBuildClient.endClientTick(mc);
			BuildersShulkersKeybinds.endClientTick(mc);
			ShulkerInventoryClient.tickServerModCheck(mc);
		});

		// Pocket-Build entry/exit on right-click. A right-click routes through one of three events depending on
		// the target (block / air / entity); all three feed the SAME toggle handler, like the three use callbacks
		// on fabric. The handler itself ignores non-client and non-main-hand calls (these events also fire on the
		// logical server), and a non-PASS result cancels the vanilla use exactly like returning it from a fabric
		// use callback.
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, event -> {
			if (PocketBuildClient.handleUse(event.getEntity(), event.getLevel(), event.getHand()) != InteractionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.FAIL);
			}
		});
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickItem.class, event -> {
			if (PocketBuildClient.handleUse(event.getEntity(), event.getLevel(), event.getHand()) != InteractionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.FAIL);
			}
		});
		NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.EntityInteract.class, event -> {
			if (PocketBuildClient.handleUse(event.getEntity(), event.getLevel(), event.getHand()) != InteractionResult.PASS) {
				event.setCanceled(true);
				event.setCancellationResult(InteractionResult.FAIL);
			}
		});
	}
}
