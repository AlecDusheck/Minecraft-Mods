package me.wheelershigley.shulkeropen;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ContainerComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;

/**
 * An inventory that wraps a shulker box ItemStack.
 * Changes are automatically saved back to the ItemStack's container component.
 */
public class ShulkerItemInventory extends SimpleInventory {
    private final ItemStack shulkerStack;

    public ShulkerItemInventory(ItemStack shulkerStack, int size) {
        super(size);
        this.shulkerStack = shulkerStack;
    }

    @Override
    public void markDirty() {
        super.markDirty();
        saveToStack();
    }

    /**
     * Saves the current inventory contents back to the shulker ItemStack.
     */
    private void saveToStack() {
        shulkerStack.set(
            DataComponentTypes.CONTAINER,
            ContainerComponent.fromStacks(this.getHeldStacks())
        );
    }

    /**
     * Gets the held stacks list for direct manipulation during loading.
     */
    public DefaultedList<ItemStack> getHeldStacks() {
        return this.heldStacks;
    }
}
