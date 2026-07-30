package com.qlm.zombie.entity;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class FakePlayerMenu extends AbstractContainerMenu {
    private static final int INVENTORY_SIZE = 27;
    private static final int ARMOR_SIZE = 4;
    private static final int OFFHAND_SIZE = 1;
    private static final int TOTAL_SIZE = INVENTORY_SIZE + ARMOR_SIZE + OFFHAND_SIZE;

    private final Container inventory;
    private final Container armorInventory;
    private final Container offhandInventory;

    public FakePlayerMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(INVENTORY_SIZE),
                new SimpleContainer(ARMOR_SIZE), new SimpleContainer(OFFHAND_SIZE));
    }

    public FakePlayerMenu(int containerId, Inventory playerInventory,
                           Container inventory, Container armorInventory, Container offhandInventory) {
        super(net.minecraft.world.inventory.MenuType.GENERIC_9x3, containerId);
        this.inventory = inventory;
        this.armorInventory = armorInventory;
        this.offhandInventory = offhandInventory;

        checkContainerSize(inventory, INVENTORY_SIZE);
        checkContainerSize(armorInventory, ARMOR_SIZE);
        checkContainerSize(offhandInventory, OFFHAND_SIZE);

        int i;

        for (i = 0; i < INVENTORY_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            this.addSlot(new Slot(inventory, i, 8 + col * 18, 84 + row * 18));
        }

        for (i = 0; i < ARMOR_SIZE; i++) {
            int slotIndex = INVENTORY_SIZE + i;
            int x = 8 + i * 18;
            int y = 10;
            this.addSlot(new ArmorSlot(armorInventory, i, x, y));
        }

        for (i = 0; i < OFFHAND_SIZE; i++) {
            int slotIndex = INVENTORY_SIZE + ARMOR_SIZE + i;
            this.addSlot(new Slot(offhandInventory, i, 8 + 4 * 18, 10));
        }

        for (i = 0; i < 3; i++) {
            for (int j = 0; j < 9; j++) {
                this.addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 142 + i * 18));
            }
        }

        for (i = 0; i < 9; i++) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 200));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int containerSlots = this.inventory.getContainerSize()
                    + this.armorInventory.getContainerSize()
                    + this.offhandInventory.getContainerSize();
            if (index < containerSlots) {
                if (!this.moveItemStackTo(itemstack1, containerSlots, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(itemstack1, 0, containerSlots, false)) {
                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return itemstack;
    }

    public Container getInventory() {
        return inventory;
    }

    public Container getArmorInventory() {
        return armorInventory;
    }

    public Container getOffhandInventory() {
        return offhandInventory;
    }

    public static class ArmorSlot extends Slot {
        public ArmorSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof net.minecraft.world.item.ArmorItem;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }
}
