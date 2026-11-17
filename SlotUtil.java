package com.foxclient.util;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory slot arithmetic and item moves.
 *
 * <p>Everything here goes through vanilla's own container click path
 * ({@code MultiPlayerGameMode#handleContainerInput}), which updates the client container and
 * <b>sends a container-click packet</b>. That is deliberate and is the only way an item can
 * actually end up in your offhand or hotbar as far as the server is concerned; a purely local
 * inventory edit would instantly desync.</p>
 *
 * <p>Slot ids: inventory indices {@code 0-8} are the hotbar and {@code 9-35} the main inventory.
 * The <em>menu</em> slot ids the server uses are offset: {@code InventoryMenu.USE_ROW_SLOT_START}
 * (36) for the hotbar, and the inventory index itself for the main inventory.</p>
 */
public final class SlotUtil {

	/** SWAP button value that targets the offhand slot; vanilla uses 40 for it. */
	public static final int OFFHAND_SWAP_BUTTON = 40;

	private SlotUtil() {
	}

	/** Inventory index (0-35) -> menu slot id used by container clicks. */
	public static int menuSlot(int inventoryIndex) {
		if (inventoryIndex < 9) {
			return InventoryMenu.USE_ROW_SLOT_START + inventoryIndex;
		}
		return inventoryIndex;
	}

	public static int findInInventory(Player player, Item item) {
		Inventory inventory = player.getInventory();
		int size = Math.min(inventory.getContainerSize(), 36);
		for (int i = 0; i < size; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				return i;
			}
		}
		return -1;
	}

	public static int findInHotbar(Player player, Item item) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				return i;
			}
		}
		return -1;
	}

	public static int count(Player player, Item item) {
		Inventory inventory = player.getInventory();
		int total = 0;
		int size = Math.min(inventory.getContainerSize(), 36);
		for (int i = 0; i < size; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && stack.getItem() == item) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/**
	 * Makes sure the given item is the one in your hand right now.
	 *
	 * <p>If it is already in the selected hotbar slot nothing happens (no packet). Otherwise the
	 * found stack is swapped into the selected hotbar slot with a single container click, so no
	 * carried-item packet is needed and both sides agree on what you are holding.</p>
	 *
	 * @return true if the item is now in the selected slot
	 */
	public static boolean holdItem(Minecraft client, Player player, Item item) {
		if (client.gameMode == null) {
			return false;
		}
		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		ItemStack held = inventory.getItem(selected);
		if (!held.isEmpty() && held.getItem() == item) {
			return true;
		}
		int slot = findInInventory(player, item);
		if (slot < 0) {
			return false;
		}
		if (slot == selected) {
			return true;
		}
		client.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, menuSlot(slot), selected,
				ContainerInput.SWAP, player);
		return true;
	}

	/** Swaps an inventory stack into the offhand slot with one container click. */
	public static boolean equipOffhand(Minecraft client, Player player, int inventoryIndex) {
		if (client.gameMode == null || inventoryIndex < 0) {
			return false;
		}
		client.gameMode.handleContainerInput(InventoryMenu.CONTAINER_ID, menuSlot(inventoryIndex),
				OFFHAND_SWAP_BUTTON, ContainerInput.SWAP, player);
		return true;
	}

	public static boolean holding(Player player, Item item) {
		ItemStack held = player.getMainHandItem();
		return !held.isEmpty() && held.getItem() == item;
	}

	public static boolean offhandIs(Player player, Item item) {
		ItemStack offhand = player.getOffhandItem();
		return !offhand.isEmpty() && offhand.getItem() == item;
	}
}
