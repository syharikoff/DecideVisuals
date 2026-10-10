package ru.decide.module.impl.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.lwjgl.glfw.GLFW;
import ru.decide.manager.event_impl.EventKey;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.mixin.MinecraftClientAccessor;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BindSetting;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.SliderSetting;

/**
 * Spear Helper (порт мода SpearHelper 1.0.0 от s0ftpulse).
 * <p>
 * Упрощает удар копьём с рывком: при полной заряженности атаки модуль сам
 * достаёт копьё из хотбара, наносит удар и возвращает обратно выбранный слот.
 * Клавиша задаётся в настройках модуля ({@link #swapKey}) — отдельная,
 * чтобы не зависеть от ванильного бинда и работать в любом меню.
 */
@ModuleInfo(
        name = "Spear Helper",
        desc = "Автоудар копьём с рывком: достаёт копьё, бьёт и возвращает слот",
        category = Category.UTILITIES
)
public final class SpearHelper extends Module {

    private static final Item[] LUNGE_SPEARS = {
            Items.WOODEN_SPEAR,
            Items.STONE_SPEAR,
            Items.COPPER_SPEAR,
            Items.GOLDEN_SPEAR,
            Items.IRON_SPEAR,
            Items.DIAMOND_SPEAR,
            Items.NETHERITE_SPEAR
    };

    /** Клавиша свапа и удара. -1 = не назначена. */
    public final BindSetting swapKey = new BindSetting(this, "Клавиша копья", GLFW.GLFW_KEY_V);
    /** Требовать полную заряженность атаки (как в оригинальном моде). */
    public final BooleanSetting requireFullCharge = new BooleanSetting(this, "Только полный заряд", true);
    /** Порог заряда, если требование полного заряда выключено (0..1). */
    public final SliderSetting chargeThreshold = new SliderSetting(this, "Порог заряда", 1.0F, 0.1F, 1.0F, 0.05F)
            .setVisible(() -> !requireFullCharge.getValue());
    /** Искать копьё в хотбаре и во второй руке. */
    public final BooleanSetting includeOffhand = new BooleanSetting(this, "Искать во второй руке", false);

    /** Слот, который был выбран до свапа: -1 = сейчас ничего не свапали. */
    private int originalSlot = -1;
    private boolean pressed;

    @Override
    protected void onDisable() {
        releaseSlot();
        pressed = false;
    }

    @EventHandler
    public void onKey(EventKey event) {
        int key = swapKey.get();
        if (key <= 0 || event.getKey() != key) return;
        if (mc.currentScreen != null) return;
        pressed = true;
    }

    @EventHandler
    public void onTick(EventTick event) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || !player.isAlive()) {
            releaseSlot();
            return;
        }

        int key = swapKey.get();
        if (key <= 0) {
            releaseSlot();
            return;
        }
        if (!pressed && !isKeyDown(client, key)) {
            releaseSlot();
            return;
        }
        pressed = false;

        float charge = player.getAttackCooldownProgress(0.0F);
        float threshold = requireFullCharge.getValue() ? 1.0F : chargeThreshold.getValue();
        if (charge < threshold) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        int spearSlot = findSpearSlot(inventory);

        if (spearSlot == -1) {
            // копья в хотбаре нет - бьём из второй руки, слот не трогаем
            if (spearInOffhand()) {
                ((MinecraftClientAccessor) client).decide$doAttack();
            }
            return;
        }

        // Слот запоминаем ДО свапа и восстанавливаем в finally. Значение -1
        // сюда попасть не должно: setSelectedSlot(-1) бросает
        // IllegalArgumentException ("Invalid selected slot").
        int previous = originalSlot;
        if (previous == -1) {
            previous = inventory.getSelectedSlot();
            originalSlot = previous;
        }

        try {
            inventory.setSelectedSlot(spearSlot);
            // doAttack() в 1.21.11 - тот же startAttack, что звал исходный мод
            ((MinecraftClientAccessor) client).decide$doAttack();
        } finally {
            inventory.setSelectedSlot(previous);
            originalSlot = -1;
        }
    }

    /**
     * Возвращает слот, если модуль выключили прямо во время свапа.
     */
    private void releaseSlot() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (originalSlot != -1 && player != null) {
            player.getInventory().setSelectedSlot(originalSlot);
        }
        originalSlot = -1;
    }

    private static boolean isKeyDown(MinecraftClient client, int key) {
        return InputUtil.isKeyPressed(client.getWindow(), key);
    }

    /**
     * Ищет любое копьё в хотбаре. Если хотбар пуст, но копьё лежит во второй
     * руке и включена соответствующая настройка - бьём из неё (слот не трогаем).
     *
     * @return слот в хотбаре или -1, если копьё нет / оно во второй руке
     */
    private int findSpearSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (isSpear(inventory.getStack(slot))) {
                return slot;
            }
        }
        return -1;
    }

    private boolean spearInOffhand() {
        if (!includeOffhand.getValue()) {
            return false;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && isSpear(player.getOffHandStack());
    }

    private boolean isSpear(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return isLungeSpear(stack.getItem());
    }

    private static boolean isLungeSpear(Item item) {
        for (Item spear : LUNGE_SPEARS) {
            if (spear == item) {
                return true;
            }
        }
        return false;
    }
}