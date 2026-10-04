package ru.white.module.impl.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.utils.other.Instance;

/**
 * Портированный Kimiko Better Minecraft — набор небольших улучшений ванильного
 * рендера. Сам модуль ничего не рисует: он только хранит флаги и считает
 * смещения, а потребляют их миксины InGameHud / HandledScreen / PlayerTabOverlay / Chat.
 *
 * Плащи (cape waves) не переносятся — это правка чужого рендерера плащей.
 */
@ModuleInfo(
        name = "Better Minecraft",
        desc = "Небольшие визуальные улучшения ванильного рендера",
        category = Category.VISUALS
)
public final class BetterMinecraft extends Module {

    private static BetterMinecraft getInstanceIfReady() {
        try {
            return Instance.get(BetterMinecraft.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public final BooleanSetting chatAnimations = new BooleanSetting(this, "Анимации чата", true);
    public final BooleanSetting tabAnimation = new BooleanSetting(this, "Анимация таба", true);
    public final BooleanSetting inventoryAnimation = new BooleanSetting(this, "Анимация инвентаря", true);
    public final BooleanSetting itemMoveAnimation = new BooleanSetting(this, "Перетаскивание предметов", true);
    public final BooleanSetting hotbarAnimation = new BooleanSetting(this, "Анимация хотбара", true);
    public final BooleanSetting hotbarChatLift = new BooleanSetting(this, "Хотбар при чате", true);
    public final BooleanSetting saturationDisplay = new BooleanSetting(this, "Отображение насыщенности", true);

    private static final Identifier SATURATION_FULL_SPRITE = Identifier.ofVanilla("hud/food_full");
    private static final Identifier SATURATION_HALF_SPRITE = Identifier.ofVanilla("hud/food_half");

    private static final float CHAT_LIFT_PX = 14.0f;
    private static final long HOTBAR_STALE_NANOS = 250_000_000L;

    private static final DecelerateValue chatLift = new DecelerateValue(260);
    private static final DecelerateValue hotbarSelection = new DecelerateValue(180);

    private static long hotbarSelectionFrameNanos;
    private static long inventoryOpenTime;

    // ================= Флаги для миксинов =================

    public static boolean chatAnimationsEnabled() {
        BetterMinecraft m = getInstanceIfReady();
        return m != null && m.isEnabled() && m.chatAnimations.getValue();
    }

    public static boolean tabAnimationEnabled() {
        BetterMinecraft m = getInstanceIfReady();
        return m != null && m.isEnabled() && m.tabAnimation.getValue();
    }

    public static boolean inventoryAnimationEnabled() {
        BetterMinecraft m = getInstanceIfReady();
        return m != null && m.isEnabled() && m.inventoryAnimation.getValue();
    }

    public static boolean itemMoveAnimationEnabled() {
        BetterMinecraft m = getInstanceIfReady();
        return m != null && m.isEnabled() && m.itemMoveAnimation.getValue();
    }

    public static boolean hotbarAnimationEnabled() {
        BetterMinecraft m = getInstanceIfReady();
        return m != null && m.isEnabled() && m.hotbarAnimation.getValue();
    }

    public static boolean hotbarChatLiftEnabled() {
        BetterMinecraft m = getInstanceIfReady();
        return m != null && m.isEnabled() && m.hotbarChatLift.getValue();
    }

    // ================= Насыщенность =================

    /** Показывает насыщение над ванильной строкой еды. */
    public static void renderSaturation(DrawContext graphics, PlayerEntity player, int top, int right) {
        BetterMinecraft m = getInstanceIfReady();
        if (m == null || !m.isEnabled() || !m.saturationDisplay.getValue()) return;

        float saturation = player.getHungerManager().getSaturationLevel();
        int size = 9;

        for (int index = 0; index < 10; index++) {
            float units = saturation - index * 2.0f;
            if (units <= 0.0f) break;

            Identifier sprite = units > 1.0f ? SATURATION_FULL_SPRITE : SATURATION_HALF_SPRITE;
            int x = right - index * 8 - 10 + (10 - size) / 2;
            graphics.drawGuiTexture(RenderPipelines.GUI_TEXTURED, sprite, x, top - size - 1, size, size);
        }
    }

    // ================= Хотбар =================

    /** Насколько хотбар поднят при открытом чате, в пикселях. */
    public static float chatHotbarLiftOffset() {
        boolean lifted = hotbarChatLiftEnabled() && MinecraftClient.getInstance().currentScreen instanceof ChatScreen;
        return chatLift.update(lifted ? CHAT_LIFT_PX : 0.0f);
    }

    /** Сглаженная X позиция рамки выбранного слота. */
    public static int animateHotbarSelectionX(int targetX) {
        long now = System.nanoTime();
        long elapsed = now - hotbarSelectionFrameNanos;
        hotbarSelectionFrameNanos = now;

        if (!hotbarAnimationEnabled() || elapsed <= 0L || elapsed > HOTBAR_STALE_NANOS) {
            hotbarSelection.snap(targetX);
            return targetX;
        }
        return Math.round(hotbarSelection.update(targetX));
    }

    // ================= Инвентарь =================

    public static void markInventoryOpen() {
        inventoryOpenTime = System.currentTimeMillis();
    }

    /** Сдвиг панелей инвентаря при открытии (от -50 до 0). */
    public static float inventorySlideOffset() {
        if (!inventoryAnimationEnabled()) return 0.0f;

        if (inventoryOpenTime == 0L) inventoryOpenTime = System.currentTimeMillis();

        float progress = Math.min(1.0f, (System.currentTimeMillis() - inventoryOpenTime) / 350.0f);

        // back-ease переподгонки
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        float t = progress - 1.0f;
        float ease = 1.0f + c3 * t * t * t + c1 * t * t;
        return -50.0f * (1.0f - ease);
    }

    // ================= Анимация =================

    /** Порт Kimiko DecelerateValue: затухающая интерполяция за фиксированное время. */
    private static final class DecelerateValue {
        private final long ms;
        private float from;
        private float to;
        private float current;
        private boolean initialised;
        private long startNanos;

        DecelerateValue(int ms) {
            this.ms = ms;
        }

        float update(float target) {
            if (!initialised) {
                initialised = true;
                from = target;
                to = target;
                current = target;
                startNanos = System.nanoTime();
                return current;
            }
            if (target != to) {
                from = current;
                to = target;
                startNanos = System.nanoTime();
            }

            double value = (System.nanoTime() - startNanos) / 1_000_000.0;
            // прогресс обязательно клампим единицей: кривая 1-(x-1)^2 после x>1 начинает убывать,
            // и без клампа анимация съезжала бы обратно к начальному значению и не доезжала до цели
            double x = Math.min(1.0, value / ms);
            double output = 1.0 - (x - 1.0) * (x - 1.0);

            current = (float) (from + (to - from) * output);
            return current;
        }

        void snap(float target) {
            from = target;
            to = target;
            current = target;
            initialised = true;
            startNanos = System.nanoTime();
        }
    }
}