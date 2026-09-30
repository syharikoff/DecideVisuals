package ru.white.module.impl.player;

import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.other.Instance;

@ModuleInfo(name = "Lock Slot", desc = "Блокирует смену слота хотбара и защищает предметы от выброса", category = Category.UTILITIES)
public class LockSlot extends Module {

    public static LockSlot get() {
        return Instance.get(LockSlot.class);
    }

    public final BooleanSetting slot1 = new BooleanSetting(this, "Слот 1", false);
    public final BooleanSetting slot2 = new BooleanSetting(this, "Слот 2", false);
    public final BooleanSetting slot3 = new BooleanSetting(this, "Слот 3", false);
    public final BooleanSetting slot4 = new BooleanSetting(this, "Слот 4", false);
    public final BooleanSetting slot5 = new BooleanSetting(this, "Слот 5", false);
    public final BooleanSetting slot6 = new BooleanSetting(this, "Слот 6", false);
    public final BooleanSetting slot7 = new BooleanSetting(this, "Слот 7", false);
    public final BooleanSetting slot8 = new BooleanSetting(this, "Слот 8", false);
    public final BooleanSetting slot9 = new BooleanSetting(this, "Слот 9", false);

    public final BooleanSetting onlyPvp = new BooleanSetting(this, "Только PvP", true);
    public final BooleanSetting swords = new BooleanSetting(this, "Мечи", false);
    public final BooleanSetting mace = new BooleanSetting(this, "Булава", false);
    public final BooleanSetting shulkers = new BooleanSetting(this, "Шалкеры", false);

    private final BooleanSetting[] slots = {slot1, slot2, slot3, slot4, slot5, slot6, slot7, slot8, slot9};

    public boolean isSlotLocked(int slot) {
        if (slot < 0 || slot > 8) return false;
        return slots[slot].getValue();
    }

    public boolean isDropProtected(ItemStack stack, int inventorySlot) {
        if (!isEnabled() || stack == null || stack.isEmpty()) return false;
        if (onlyPvp.getValue() && !ServerUtil.isPvp()) return false;

        if (isShulker(stack)) {
            return shulkers.getValue();
        }

        if (inventorySlot < 0 || inventorySlot > 8) return false;
        return (swords.getValue() && isSword(stack))
                || (mace.getValue() && stack.isOf(Items.MACE));
    }

    private boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private boolean isSword(ItemStack stack) {
        return Registries.ITEM.getId(stack.getItem()).getPath().endsWith("_sword");
    }
}
