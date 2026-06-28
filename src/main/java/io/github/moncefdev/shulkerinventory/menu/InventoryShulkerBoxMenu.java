package io.github.moncefdev.shulkerinventory.menu;

import io.github.moncefdev.shulkerinventory.ShulkerContents;
import io.github.moncefdev.shulkerinventory.ShulkerInventory;
import io.github.moncefdev.shulkerinventory.network.OpenPlayerInventoryPayload;
import io.github.moncefdev.shulkerinventory.network.PocketBuildRemoteContentPayload;
import io.github.moncefdev.shulkerinventory.network.RemoteShulkerAnimationPayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.HashedStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// Reuses the vanilla ShulkerBoxMenu (free mod compatibility and vanilla slot handling), backed by a working
// copy of the source stack's CONTAINER contents. The working copy is written back to the stack on close or on
// any interaction that disturbs the source slot, so all item moves stay server-authoritative.
public class InventoryShulkerBoxMenu extends ShulkerBoxMenu {
	private final SimpleContainer shulkerContent;
	private final int sourceSlotIndex;
	private final int sourceSlotInMenu;
	private final long animationId;
	private boolean alreadySaved = false;
	// Set when this session ends because the source shulker stack itself was disturbed (grabbed off its slot, swapped,
	// or dropped). A normal close (toggle or Escape) leaves this false.
	private boolean disturbed = false;
	// Set when the disturbance was a DROP (the source shulker thrown to the world): unlike a grab/swap, the box stays
	// VISIBLE to viewers as a dropped item entity, so its closing animation keeps its sound instead of being silent.
	private boolean droppedToWorld = false;

	public InventoryShulkerBoxMenu(int syncId, Inventory playerInventory, SimpleContainer shulkerContent, int sourceSlotIndex, long animationId) {
		super(syncId, playerInventory, shulkerContent);
		this.shulkerContent = shulkerContent;
		this.sourceSlotIndex = sourceSlotIndex;
		this.sourceSlotInMenu = findInventorySlot(this.slots, sourceSlotIndex);
		this.animationId = animationId;
	}

	// Mirror a lid animation to the OTHER players who can see the holder, so they see the lid move on the held
	// shulker too (the holder animates locally and is excluded). Cosmetic broadcast; in singleplayer there are no
	// other tracking players, so this is a no-op and solo behavior is unchanged.
	public static void broadcastAnimation(ServerPlayer holder, long animationId, boolean opening, boolean playSound) {
		RemoteShulkerAnimationPayload payload = new RemoteShulkerAnimationPayload(animationId, opening, holder.getId(), playSound);
		for (ServerPlayer viewer : PlayerLookup.tracking(holder)) {
			// Only send to viewers that run the mod and have registered our channel. This skips vanilla clients
			// (compatibility: never push our payload to a client that cannot handle it) and any viewer not yet
			// ready to receive it (a just-joined player mid-handshake), which would otherwise error the send and
			// disconnect that viewer.
			if (viewer != holder && ServerPlayNetworking.canSend(viewer, RemoteShulkerAnimationPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	// Mirror the Pocket-Build selected content (drawn inside the held box) to the OTHER players who can see the holder,
	// so they get the dissolving lid + the block inside, matching what the holder sees, instead of just the plain lid
	// animation. Sent on enter and on every scroll. Cosmetic; the holder draws its own content locally and is excluded,
	// and in singleplayer there are no other tracking players so this is a no-op. Skips viewers without the channel
	// (vanilla clients, or one still mid-handshake), exactly like broadcastAnimation.
	public static void broadcastPocketBuildContent(ServerPlayer holder, long animationId, ItemStack content) {
		PocketBuildRemoteContentPayload payload = new PocketBuildRemoteContentPayload(animationId, holder.getId(), content);
		for (ServerPlayer viewer : PlayerLookup.tracking(holder)) {
			if (viewer != holder && ServerPlayNetworking.canSend(viewer, PocketBuildRemoteContentPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}

	// Finds the menu slot index backed by the given player-inventory container slot, or -1. The shulker content is a
	// SimpleContainer (not an Inventory), so matching "the slot whose container is an Inventory" uniquely targets the
	// player-inventory slots. Shared by the constructor (our own menu) and the disturbance replay (the player's
	// inventory menu).
	private static int findInventorySlot(List<Slot> slots, int containerSlot) {
		for (int i = 0; i < slots.size(); i++) {
			Slot s = slots.get(i);
			if (s.container instanceof Inventory && s.getContainerSlot() == containerSlot) {
				return i;
			}
		}
		return -1;
	}

	public int getSourceSlotIndex() {
		return sourceSlotIndex;
	}

	public void closeAndReturnToInventory(ServerPlayer serverPlayer) {
		saveContents(serverPlayer);
		alreadySaved = true;
		closeMenuSilently(serverPlayer);
		finishReturnToInventory(serverPlayer);
	}

	// Syncs the inventory menu to the client and asks it to reopen the inventory screen. The authoritative cursor is
	// kept in sync by closeMenuSilently (which transfers the cursor onto the inventory menu, including an empty
	// cursor) plus this broadcast, so the client never keeps a stale cursor item.
	private void finishReturnToInventory(ServerPlayer serverPlayer) {
		serverPlayer.inventoryMenu.broadcastFullState();
		ServerPlayNetworking.send(serverPlayer, OpenPlayerInventoryPayload.INSTANCE);
		dropCreativeCursorShadow(serverPlayer);
	}

	// In creative the cursor is client-authoritative. The session-close paths can leave a stack on the player
	// inventory menu's SERVER-side carried: the commit-on-disturbance replay picks the source shulker up, and a
	// normal close transfers the shulker-menu cursor over. That server carried is only a shadow of the client's
	// real cursor and has no creative lifecycle (creative slot/drop actions never update it). Left in place it is
	// re-given by the vanilla inventory close (duplication); and clearing it at close time instead would clobber
	// the client's still-held cursor (item loss). Since the client already received the item on its cursor from the
	// broadcast above and stays authoritative over it, we drop the server shadow here WITHOUT resyncing: empty the
	// carried and tell the remote tracker the client holds empty, so no clearing cursor update is sent. Survival is
	// untouched (the cursor is server-authoritative there and must keep its carried so the vanilla close re-gives).
	private static void dropCreativeCursorShadow(ServerPlayer serverPlayer) {
		if (!serverPlayer.isCreative() || serverPlayer.inventoryMenu.getCarried().isEmpty()) {
			return;
		}
		serverPlayer.inventoryMenu.setCarried(ItemStack.EMPTY);
		serverPlayer.inventoryMenu.setRemoteCarried(HashedStack.EMPTY);
	}

	@Override
	public void clicked(int slotId, int button, ClickType input, Player player) {
		// Commit-on-disturbance (anti-dup): if the action targets the source shulker stack itself, save the
		// worked contents into it, end the session, then replay the action on the inventory menu. Saving always
		// happens before the move, so the up-to-date stack is what gets picked up or relocated.
		// Exception: a drop (THROW) on the source slot while the cursor is NON-EMPTY is a vanilla no-op (the drop key
		// only drops from the hovered slot when the cursor is empty - verified in AbstractContainerMenu.doClick), so it
		// does not disturb the source. Let it fall through to vanilla (which does nothing) instead of pointlessly
		// closing the session.
		boolean noOpDrop = input == ClickType.THROW && !getCarried().isEmpty();
		if (touchesSourceSlot(slotId, button, input) && !noOpDrop) {
			disturbed = true;
			// A THROW on the source slot (drop key) sends the box to the world as a visible item; a PICKUP/SWAP sends it
			// to the cursor or another slot. Only the drop keeps the close sound for viewers (see removed()).
			droppedToWorld = input == ClickType.THROW;
			broadcastFullState();
			if (player instanceof ServerPlayer serverPlayer) {
				saveContents(serverPlayer);
				alreadySaved = true;
				closeMenuSilently(serverPlayer);
				replayClickOnInventoryMenu(serverPlayer, slotId, button, input);
				finishReturnToInventory(serverPlayer);
			}
			return;
		}
		super.clicked(slotId, button, input, player);
		if (player instanceof ServerPlayer) {
			saveContents(player);
		}
	}

	private void closeMenuSilently(ServerPlayer serverPlayer) {
		ItemStack carriedSnapshot = this.getCarried();
		this.setCarried(ItemStack.EMPTY);
		serverPlayer.doCloseContainer();
		// Always transfer the cursor onto the inventory menu, INCLUDING an empty cursor. The shulker menu carried
		// is the authoritative cursor at close. Re-injecting only when non-empty (the old behavior) left any stale
		// inventoryMenu carried in place: a phantom from an earlier source-slot pickup that creative's
		// set-creative-slot placement never clears. That value then never changes, so vanilla never re-syncs it and
		// the client commits it as a real duplicate in creative. Setting it unconditionally flushes the phantom (to
		// empty) and, because the value changes, triggers the cursor re-sync to the client.
		serverPlayer.inventoryMenu.setCarried(carriedSnapshot);
	}

	private void replayClickOnInventoryMenu(ServerPlayer serverPlayer, int slotId, int button, ClickType input) {
		if (slotId < 0 || slotId >= this.slots.size()) {
			return;
		}
		Slot source = this.slots.get(slotId);
		if (!(source.container instanceof Inventory)) {
			return;
		}
		int targetSlot = findInventorySlot(serverPlayer.inventoryMenu.slots, source.getContainerSlot());
		if (targetSlot < 0) {
			return;
		}
		serverPlayer.inventoryMenu.clicked(targetSlot, button, input, serverPlayer);
	}

	private boolean touchesSourceSlot(int slotId, int button, ClickType input) {
		if (slotId == sourceSlotInMenu) {
			return true;
		}
		if (input == ClickType.SWAP && button == sourceSlotIndex) {
			return true;
		}
		return false;
	}

	@Override
	public void removed(Player player) {
		super.removed(player);
		if (!alreadySaved) {
			saveContents(player);
		}
		// Mirror the closing lid animation to the other players who can see the holder. removed() is the single close
		// chokepoint (toggle, commit-on-disturbance, Escape, swap all route through it). Broadcast it ALWAYS, so a viewer
		// drains/animates the box even if it only became visible mid-animation. The SOUND plays only when the box is
		// visible at the close: still held (a normal toggle/Escape close), or just dropped to the world (a visible item
		// entity). A grab/swap moves it to the cursor or another slot (invisible to viewers) and stays silent. The
		// opener's own close sound is played on their own client.
		if (player instanceof ServerPlayer serverPlayer) {
			boolean playSound = disturbed ? droppedToWorld : isHeld(serverPlayer);
			broadcastAnimation(serverPlayer, animationId, false, playSound);
		}
	}

	private boolean isHeld(ServerPlayer player) {
		return isHeldSlot(player.getInventory(), sourceSlotIndex);
	}

	// Whether a player-inventory slot is currently "held", i.e. visible in hand: the selected hotbar slot or the
	// off-hand. Shared with the open handler so the "what counts as held" rule lives in one place.
	public static boolean isHeldSlot(Inventory inventory, int slot) {
		return slot == inventory.getSelectedSlot() || slot == Inventory.SLOT_OFFHAND;
	}

	// Writes the working container back into the source stack's CONTAINER component. Guards that the source slot
	// still holds a shulker (it may have moved), to avoid writing onto the wrong item.
	private void saveContents(Player player) {
		ItemStack sourceStack = player.getInventory().getItem(sourceSlotIndex);
		if (!ShulkerContents.isShulker(sourceStack)) {
			ShulkerInventory.LOGGER.warn(
					"Cannot save shulker contents: source slot {} no longer contains a shulker (item: {})",
					sourceSlotIndex, sourceStack);
			return;
		}

		List<ItemStack> items = new ArrayList<>(shulkerContent.getContainerSize());
		for (int i = 0; i < shulkerContent.getContainerSize(); i++) {
			items.add(shulkerContent.getItem(i));
		}
		ShulkerContents.write(sourceStack, items);
	}
}
