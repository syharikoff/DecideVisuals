package ru.white.utils.render;

import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Порт Kimiko ItemMoveAnimator — предметы плавно перелетают в новый слот
 * при перекладывании во всех экранах с инвентарём.
 *
 * Логика: запоминаем содержимое и позиции слотов прошлого кадра. Если в слоте
 * появился предмет, которого там не было, ищем, откуда он пришёл (другой слот
 * с тем же предметом или курсор), и запускаем анимацию перелёта на 140 мс.
 * Если источник не нашли — предмет «появляется» с лёгким scale.
 */
public final class ItemMoveAnimator {

    private static final float DURATION = 0.14f;

    private static ScreenHandler lastMenu;
    private static long lastNs;
    private static ItemStack lastCarried = ItemStack.EMPTY;

    private static final Map<Slot, ItemStack> lastItems = new HashMap<>();
    private static final Map<Slot, int[]> lastPos = new HashMap<>();
    private static final Map<Slot, Anim> anims = new HashMap<>();
    private static final Map<Slot, float[]> offsets = new HashMap<>();

    private ItemMoveAnimator() {
    }

    /** Вызывать один раз на кадр перед отрисовкой слотов. */
    public static void beginFrame(ScreenHandler menu, int mouseX, int mouseY, int leftPos, int topPos) {
        offsets.clear();

        if (menu != lastMenu) {
            lastMenu = menu;
            lastItems.clear();
            lastPos.clear();
            anims.clear();
            lastCarried = ItemStack.EMPTY;
            lastNs = 0L;
        }

        long now = System.nanoTime();
        float dt = lastNs == 0L ? 0.016f : Math.min(0.1f, (now - lastNs) / 1.0E9f);
        lastNs = now;

        Map<Slot, ItemStack> cur = new HashMap<>();
        Map<Slot, int[]> pos = new HashMap<>();
        for (Slot slot : menu.slots) {
            cur.put(slot, slot.getStack());
            pos.put(slot, new int[]{slot.x, slot.y});
        }
        ItemStack carried = menu.getCursorStack();

        lastItems.keySet().retainAll(cur.keySet());
        lastPos.keySet().retainAll(cur.keySet());
        anims.keySet().retainAll(cur.keySet());

        for (Map.Entry<Slot, ItemStack> entry : cur.entrySet()) {
            Slot slot = entry.getKey();
            ItemStack current = entry.getValue();
            if (current.isEmpty()) {
                anims.remove(slot);
                continue;
            }

            ItemStack previous = lastItems.getOrDefault(slot, ItemStack.EMPTY);
            if (sameKey(current, previous) || anims.containsKey(slot)) continue;

            Anim anim = new Anim();
            int[] targetPos = pos.get(slot);
            if (targetPos == null) continue;

            Slot fromSlot = findFrom(cur, slot, current, pos, targetPos);
            if (fromSlot != null) {
                int[] fromPos = lastPos.getOrDefault(fromSlot, pos.get(fromSlot));
                if (fromPos != null) {
                    anim.fromX = fromPos[0] - targetPos[0];
                    anim.fromY = fromPos[1] - targetPos[1];
                }
            } else if (sameKey(current, lastCarried) && !sameKey(carried, lastCarried)) {
                // предмет прилетел с курсора мыши
                anim.fromX = (mouseX - leftPos - targetPos[0]) - 8.0f;
                anim.fromY = (mouseY - topPos - targetPos[1]) - 8.0f;
            } else {
                anim.pop = true;
            }

            anims.put(slot, anim);
        }

        Iterator<Map.Entry<Slot, Anim>> it = anims.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Slot, Anim> entry = it.next();
            Anim anim = entry.getValue();
            anim.progress += dt / DURATION;
            if (anim.progress >= 1.0f) {
                it.remove();
                continue;
            }

            float eased = easeOut(anim.progress);
            if (anim.pop) {
                offsets.put(entry.getKey(), new float[]{0.0f, 0.0f, 0.55f + 0.45f * eased});
                continue;
            }

            float f = 1.0f - eased;
            offsets.put(entry.getKey(), new float[]{anim.fromX * f, anim.fromY * f, 1.0f});
        }

        lastItems.clear();
        for (Map.Entry<Slot, ItemStack> entry : cur.entrySet()) {
            lastItems.put(entry.getKey(), entry.getValue().copy());
        }
        lastPos.clear();
        lastPos.putAll(pos);
        lastCarried = carried.copy();
    }

    /** {offsetX, offsetY, scale} для слота, либо null. */
    public static float[] offset(Slot slot) {
        return offsets.get(slot);
    }

    /** Ближайший слот, откуда предмет мог прийти. */
    private static Slot findFrom(Map<Slot, ItemStack> cur, Slot target, ItemStack item,
                                 Map<Slot, int[]> pos, int[] targetPos) {
        Slot best = null;
        long bestDist = Long.MAX_VALUE;

        for (Map.Entry<Slot, ItemStack> entry : lastItems.entrySet()) {
            Slot slot = entry.getKey();
            ItemStack value = entry.getValue();
            if (slot == target || !sameKey(value, item)) continue;

            ItemStack nowThere = cur.get(slot);
            if (nowThere != null && sameKey(nowThere, item)) continue;

            int[] fromPos = lastPos.getOrDefault(slot, pos.get(slot));
            if (fromPos == null) continue;

            long dx = fromPos[0] - targetPos[0];
            long dy = fromPos[1] - targetPos[1];
            long dist = dx * dx + dy * dy;
            if (dist >= bestDist) continue;

            bestDist = dist;
            best = slot;
        }
        return best;
    }

    private static boolean sameKey(ItemStack a, ItemStack b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        if (a.isEmpty() || b.isEmpty()) return false;
        return ItemStack.areItemsAndComponentsEqual(a, b);
    }

    private static float easeOut(float t) {
        float clamped = t < 0.0f ? 0.0f : (t > 1.0f ? 1.0f : t);
        float u = 1.0f - clamped;
        return 1.0f - u * u * u;
    }

    private static final class Anim {
        float fromX;
        float fromY;
        float progress;
        boolean pop;
    }
}