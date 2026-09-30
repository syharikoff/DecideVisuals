package ru.white.module.impl.utils;

import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import ru.white.manager.event_impl.EventKey;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BindSetting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.screen.AutoSwapScreen;
import ru.white.utils.notification.NotificationManager;

import java.util.function.Predicate;

@ModuleInfo(name = "Auto Swap", category = Category.UTILITIES, desc = "Свап двух предметов или выбор через круговое меню")
public final class AutoSwapModule extends Module {
    private static final String[] ITEMS = {"Щит", "Тотем", "Фейерверки", "Любая еда", "Геплы", "Сфера", "Руны Sunrise"};

    public final BindSetting swapKey = new BindSetting(this, "Клавиша свапа", -1);
    public final ModeSetting mode = new ModeSetting(this, "Режим", "Обычный", "Мульти");
    public final ModeSetting firstItem = new ModeSetting(this, "Первый предмет", ITEMS)
            .setVisible(() -> mode.is("Обычный"));
    public final ModeSetting secondItem = new ModeSetting(this, "Второй предмет", ITEMS)
            .setVisible(() -> mode.is("Обычный"));
    public final SliderSetting slotCount = new SliderSetting(this, "Кол-во слотов", 3.0f, 3.0f, 6.0f, 1.0f)
            .setVisible(() -> mode.is("Мульти"));
    public final SliderSetting delay = new SliderSetting(this, "Задержка (тик)", 4.0f, 0.0f, 20.0f, 1.0f);
    public final BooleanSetting logSwappedItem = new BooleanSetting(this, "Показывать предмет", false);

    private final AutoSwapItems savedItems = new AutoSwapItems();
    private boolean pendingDefault;
    private int pickSlot = -1;
    private int ticks;
    private int lastHotbarSwap;
    private int freezeTicks;
    private int scheduledSlot = -1;
    private boolean[] savedKeyState;

    public AutoSwapItems savedItems() {
        return savedItems;
    }

    public int getSlotCount() {
        return slotCount.getValue().intValue();
    }

    @EventHandler
    public void onKey(EventKey event) {
        if (event.getKey() == -1 || event.getKey() != swapKey.get() || mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.player.isSpectator() || !mc.player.isAlive()) return;

        if (mode.is("Мульти")) {
            savedItems.setCount(getSlotCount());
            savedItems.load();
            mc.setScreen(new AutoSwapScreen(this, swapKey.get()));
        } else {
            if (freezeTicks > 0) return;
            Predicate<ItemStack> first = matcher(firstItem.getValue());
            Predicate<ItemStack> second = matcher(secondItem.getValue());
            ItemStack offhand = mc.player.getOffHandStack();
            Predicate<ItemStack> target;
            if (first.test(offhand)) {
                target = second;
            } else if (second.test(offhand)) {
                target = first;
            } else {
                int s = find(first, true);
                target = s >= 0 ? first : second;
            }
            int slot = find(target, true);
            if (slot >= 0) scheduleSwap(slot);
        }
    }

    public void scheduleSwap(int slot) {
        if (slot < 0 || freezeTicks > 0) return;
        freezeTicks = Math.max(1, delay.getValue().intValue());
        scheduledSlot = slot;

        KeyBinding[] keys = getMovementKeys();
        savedKeyState = new boolean[keys.length];
        for (int i = 0; i < keys.length; i++) {
            savedKeyState[i] = keys[i].isPressed();
        }
    }

    @EventHandler
    public void onTick(EventTick event) {
        ticks++;

        if (freezeTicks > 0) {
            freezeTicks--;

            KeyBinding[] keys = getMovementKeys();
            for (KeyBinding key : keys) {
                key.setPressed(false);
            }

            if (mc.player != null) {
                long handle = mc.getWindow().getHandle();
                if (org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                        != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                    mc.player.setSprinting(false);
                }
            }

            if (freezeTicks <= 0 && scheduledSlot >= 0) {
                int slot = scheduledSlot;
                scheduledSlot = -1;
                restoreKeys();
                performSwap(slot);
            }
            return;
        }

        if (!canSwap() || mc.currentScreen != null) return;
        if (pendingDefault) {
            pendingDefault = false;
            swapDefault();
        }
    }

    public void startPick(int index) {
        if (index < 0 || index >= getSlotCount() || mc.player == null) return;
        pickSlot = index;
        savedItems.setCount(getSlotCount());
        savedItems.load();
        mc.setScreen(new InventoryScreen(mc.player));
    }

    public boolean isPicking() {
        return pickSlot >= 0;
    }

    public void finishPick(ItemStack stack) {
        if (pickSlot < 0 || pickSlot >= getSlotCount() || stack.isEmpty()) return;
        if (savedItems.set(pickSlot, stack)) {
            NotificationManager.send("Предмет " + (pickSlot + 1) + " сохранён в Auto Swap",
                    NotificationManager.Type.MODULE);
        }
        pickSlot = -1;
    }

    public void removeAt(int index) {
        if (index < 0 || index >= getSlotCount()) return;
        if (savedItems.set(index, ItemStack.EMPTY)) {
            NotificationManager.send("Предмет " + (index + 1) + " удалён из Auto Swap",
                    NotificationManager.Type.INFO);
        }
    }

    public void select(int index) {
        if (index < 0 || index >= getSlotCount() || savedItems.get(index).isEmpty()) return;
        int slot = find(savedItems.predicate(index), false);
        if (slot >= 0) scheduleSwap(slot);
    }

    public boolean available(int index) {
        return find(savedItems.predicate(index), false) >= 0;
    }

    private boolean canSwap() {
        return mc.player != null && mc.world != null && mc.interactionManager != null
                && mc.getNetworkHandler() != null && !mc.player.isSpectator() && mc.player.isAlive()
                && mc.player.currentScreenHandler == mc.player.playerScreenHandler
                && mc.player.currentScreenHandler.getCursorStack().isEmpty();
    }

    private void swapDefault() {
        Predicate<ItemStack> first = matcher(firstItem.getValue());
        Predicate<ItemStack> second = matcher(secondItem.getValue());
        ItemStack offhand = mc.player.getOffHandStack();
        if (first.test(offhand)) {
            swap(find(second, true));
        } else if (second.test(offhand)) {
            swap(find(first, true));
        } else {
            int slot = find(first, true);
            swap(slot >= 0 ? slot : find(second, true));
        }
    }

    private int find(Predicate<ItemStack> predicate, boolean preferEnchanted) {
        if (mc.player == null) return -1;
        int first = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getStack(slot);
            if (stack.isEmpty() || !predicate.test(stack)) continue;
            if (!preferEnchanted || stack.hasEnchantments()) return slot;
            if (first == -1) first = slot;
        }
        return first;
    }

    private void performSwap(int slot) {
        if (slot < 0 || !canSwap()) return;
        if (logSwappedItem.getValue()) {
            NotificationManager.send(mc.player.getInventory().getStack(slot).getName().getString(),
                    NotificationManager.Type.INFO, mc.player.getInventory().getStack(slot).copy(), 1500);
        }
        if (slot < 9 && ticks - lastHotbarSwap > 15) {
            int selected = mc.player.getInventory().getSelectedSlot();
            if (selected != slot) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(slot));
            mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            if (selected != slot) mc.getNetworkHandler().sendPacket(new UpdateSelectedSlotC2SPacket(selected));
            lastHotbarSwap = ticks;
        } else {
            mc.interactionManager.clickSlot(mc.player.playerScreenHandler.syncId,
                    slot < 9 ? slot + 36 : slot, 40, SlotActionType.SWAP, mc.player);
        }
    }

    private void swap(int slot) {
        performSwap(slot);
    }

    private KeyBinding[] getMovementKeys() {
        return new KeyBinding[]{
                mc.options.forwardKey, mc.options.backKey,
                mc.options.leftKey, mc.options.rightKey,
                mc.options.jumpKey, mc.options.sprintKey, mc.options.sneakKey
        };
    }

    private void restoreKeys() {
        if (savedKeyState == null) return;
        KeyBinding[] keys = getMovementKeys();
        int[] glKeys = {
                org.lwjgl.glfw.GLFW.GLFW_KEY_W, org.lwjgl.glfw.GLFW.GLFW_KEY_S,
                org.lwjgl.glfw.GLFW.GLFW_KEY_A, org.lwjgl.glfw.GLFW.GLFW_KEY_D,
                org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,
                org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT
        };
        long handle = mc.getWindow().getHandle();
        for (int i = 0; i < keys.length && i < savedKeyState.length; i++) {
            keys[i].setPressed(false);
        }
        for (int i = 0; i < keys.length && i < savedKeyState.length; i++) {
            if (savedKeyState[i] && i < glKeys.length
                    && org.lwjgl.glfw.GLFW.glfwGetKey(handle, glKeys[i]) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                keys[i].setPressed(true);
            }
        }
        savedKeyState = null;
    }

    private Predicate<ItemStack> matcher(String name) {
        return switch (name) {
            case "Щит" -> stack -> stack.isOf(Items.SHIELD);
            case "Тотем" -> stack -> stack.isOf(Items.TOTEM_OF_UNDYING);
            case "Фейерверки" -> stack -> stack.isOf(Items.FIREWORK_ROCKET);
            case "Любая еда" -> stack -> stack.getItem().getComponents().contains(DataComponentTypes.FOOD);
            case "Геплы" -> stack -> stack.isOf(Items.GOLDEN_APPLE);
            case "Руны Sunrise" -> stack -> (stack.isOf(Items.FIREWORK_STAR) || stack.isOf(Items.POPPED_CHORUS_FRUIT))
                    && stack.hasEnchantments();
            case "Сфера" -> stack -> stack.isOf(Items.PLAYER_HEAD)
                    && (stack.contains(DataComponentTypes.ATTRIBUTE_MODIFIERS)
                    || stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, net.minecraft.component.type.NbtComponent.DEFAULT)
                    .copyNbt().contains("sphereEffect"));
            default -> stack -> false;
        };
    }

    @Override
    public void onDisable() {
        reset();
    }

    private void reset() {
        pendingDefault = false;
        pickSlot = -1;
        freezeTicks = 0;
        scheduledSlot = -1;
        restoreKeys();
        if (mc.currentScreen instanceof AutoSwapScreen screen && screen.belongsTo(this)) mc.setScreen(null);
    }
}
