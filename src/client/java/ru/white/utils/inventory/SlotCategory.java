package ru.white.utils.inventory;

import net.minecraft.item.ItemStack;

public enum SlotCategory {
    POTION(SlotItem.Group.POTION, 16724530);

    private final SlotItem.Group itemGroup;
    private final int defaultColor;

    SlotCategory(SlotItem.Group itemGroup, int defaultColor) {
        this.itemGroup = itemGroup;
        this.defaultColor = defaultColor;
    }

    public SlotItem.Group getItemGroup() {
        return itemGroup;
    }

    public int getDefaultColor() {
        return defaultColor;
    }

    public static SlotCategory classify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        } else if (SlotItem.match(stack, SlotItem.Group.POTION) != null) {
            return POTION;
        }
        return null;
    }
}