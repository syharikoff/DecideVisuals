package ru.white.utils.math;


import ru.white.utils.annotation.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;

import java.util.stream.IntStream;

@UtilityClass
public class InvUtil implements IMinecraft {

    public int getItemInHotBar(Item item) {
        return IntStream.range(0, 9).filter(i -> mc.player.getInventory().getStack(i).getItem().equals(item)).findFirst().orElse(-1);
    }

    public static int getItemIndex(Item item) {
        if (mc.player == null) return -1;

        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return i;
            }
        }
        return -1;
    }
    public void addTaskWithLock(Runnable task, long delayMs) {
        if (mc.player == null) return;


        setMovementKeys(false);

        new Thread(() -> {
            try {
                Thread.sleep(30);

                mc.execute(() -> {
                    if (task != null) {
                        task.run();
                    }
                });

                Thread.sleep(delayMs);


                mc.execute(() -> {
                    setMovementKeys(true);
                });

            } catch (InterruptedException e) {
                e.printStackTrace();

                mc.execute(() -> setMovementKeys(true));
            }
        }).start();
    }
    private void setMovementKeys(boolean state) {
        KeyBinding[] movementKeys = {
                mc.options.forwardKey,
                mc.options.backKey,
                mc.options.leftKey,
                mc.options.rightKey,
                mc.options.jumpKey,
                mc.options.sprintKey
        };

        long handle = mc.getWindow().getHandle();

        for (KeyBinding keyBinding : movementKeys) {
            boolean pressed = state && InputUtil.isKeyPressed(mc.getWindow(), keyBinding.getDefaultKey().getCode());
            keyBinding.setPressed(pressed);
        }
    }
    public static boolean doesHotbarHaveItem(Item item) {
        if (mc.player == null) return false;

        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item) {
                return true;
            }
        }
        return false;
    }

    public static void inventorySwapClick(Item item, boolean rotation) {
        if (mc.player == null || mc.interactionManager == null) return;

        int index = getItemIndex(item);
        if (index == -1) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();

        // если предмет уже в хотбаре
        if (index < 9) {
            if (index != currentSlot) {
                mc.player.networkHandler.sendPacket(
                        new UpdateSelectedSlotC2SPacket(index)
                );
            }

            mc.player.networkHandler.sendPacket(
                    new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0,mc.player.getYaw(),mc.player.getPitch())
            );

            if (index != currentSlot) {
                mc.player.networkHandler.sendPacket(
                        new UpdateSelectedSlotC2SPacket(currentSlot)
                );
            }
            return;
        }

        // если предмет в инвентаре
        int swapSlot = currentSlot;

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                index,
                swapSlot,
                SlotActionType.SWAP,
                mc.player
        );

        mc.player.networkHandler.sendPacket(
                new UpdateSelectedSlotC2SPacket(swapSlot)
        );

        mc.player.networkHandler.sendPacket(
                new PlayerInteractItemC2SPacket(Hand.MAIN_HAND, 0,mc.player.getYaw(),mc.player.getPitch())
        );

        mc.player.networkHandler.sendPacket(
                new UpdateSelectedSlotC2SPacket(currentSlot)
        );

        mc.interactionManager.clickSlot(
                mc.player.currentScreenHandler.syncId,
                index,
                swapSlot,
                SlotActionType.SWAP,
                mc.player
        );
    }


    public int find(Item item) {
        int slot = -1;



        // Инвентарь + хотбар (0–35)
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                slot = i;
                break;
            }
        }

        // Хотбар → контейнерные слоты
        if (slot < 9 && slot != -1) {
            slot += 36;
        }

        return slot;
    }

    public int find(Item item, boolean ignoreEnchanted) {
        return find(item, ignoreEnchanted, false);
    }

    public int find(Item item, boolean ignoreEnchanted, boolean onlyEnchanted) {
        int slot = -1;



        // Только зачарованные
        if (onlyEnchanted) {
            for (int i = 0; i < 36; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == item && stack.hasEnchantments()) {
                    slot = i;
                    break;
                }
            }
        } else {
            // Сначала незачарованные
            for (int i = 0; i < 36; ++i) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (!stack.isEmpty() && stack.getItem() == item && !stack.hasEnchantments()) {
                    slot = i;
                    break;
                }
            }

            // Если не нашли — любые
            if (slot == -1 && !ignoreEnchanted) {
                for (int i = 0; i < 36; ++i) {
                    ItemStack stack = mc.player.getInventory().getStack(i);
                    if (!stack.isEmpty() && stack.getItem() == item) {
                        slot = i;
                        break;
                    }
                }
            }
        }

        if (slot < 9 && slot != -1) {
            slot += 36;
        }

        return slot;
    }

}

