package ru.decide.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.module.impl.combat.UseTracker;
import ru.decide.utils.other.UseCooldowns;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.animation.satoshi.Direction;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.RollingText;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class UseTrackerHud implements element {

    /** Общий масштаб плашки. */
    private static  float S = 1.0F;

    // Базовые размеры
    private static  float H = 16F * S;
    private static  float MIN_W = 60F * S;
    private static  float RADIUS = 5F * S;
    private static  float BASE_H = 4F * S;

    // Размеры шриф иконок
    private static  float TITLE_TEXT = 7F * S;
    private static  float TITLE_ICON = 5F * S;
    private static  float ROW_TEXT = 6.5F * S;

    // Отступы для овка
    private static  float TITLE_ICON_X = 5F * S;
    private static  float TITLE_ICON_Y = 5.5F * S;
    private static  float TITLE_TEXT_X = 12.5F * S;
    private static  float TITLE_TEXT_Y = 3.4F * S;

    // Отступы и ра для строк
    private static  float ROW_HEIGHT = 14F * S;
    private static  float ROW_BASE_W = 22F * S;
    private static  float ROW_PADDING_X = 5F * S;
    private static  float ROW_START_Y = 5F * S;
    private static  float ROW_ANIM_OFFSET = 15F * S;

    // Дополнительнступы для заголовка-игрока
    private static  float HEAD_TEXT_X = 15F * S;
    private static  float HEAD_ICON_Y = 0.9F * S;

    // Отступы для лителя
    private static  float SEP_PADDING_X = 4F * S;
    private static  float SEP_OFFSET_Y = 3.2F * S;


    private final ru.decide.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    private final ru.decide.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);

    /** Плавная ширина фона — подгоняется под самую длинную строку. */
    private float widthAnim = 0;

    /** Появление строки с ником цели — ей же двигаются все строки ниже. */
    private final Animation headAnimation = new Animation();
    private String head = "";

    private final Map<String, Row> rows = new LinkedHashMap<>();

    private static class Row {
        final RollingText time = new RollingText();
        final Animation animation = new Animation();
        boolean active;
        String label = "";
        int color;
    }

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {

        UseTracker tracker = UseTracker.getInstance();
        S = InterFace.getInstance().sizeHud.getValue();
        H = 16F * S;
        MIN_W = 60F * S;
        RADIUS = 5F * S;
        BASE_H = 4F * S;
        TITLE_TEXT = 7F * S;
        TITLE_ICON = 5F * S;
        ROW_TEXT = 6.5F * S;
        TITLE_ICON_X = 5F * S;
        TITLE_ICON_Y = 5.5F * S;
        TITLE_TEXT_X = 12.5F * S;
        TITLE_TEXT_Y = 3.4F * S;

        ROW_HEIGHT = 14F * S;
        ROW_BASE_W = 22F * S;
        ROW_PADDING_X = 5F * S;
        ROW_START_Y = 5F * S;
        ROW_ANIM_OFFSET = 15F * S;

        HEAD_TEXT_X = 15F * S;
        HEAD_ICON_Y = 0.9F * S;
        SEP_PADDING_X = 4F * S;
        SEP_OFFSET_Y = 3.2F * S;
        rows.values().forEach(row -> row.active = false);

        boolean hasTarget = tracker.hasTarget();

        if (hasTarget) {
            for (UseCooldowns.Item item : UseCooldowns.Item.values()) {
                int left = tracker.remaining(item);
                if (left <= 0) continue;

                Row row = rows.computeIfAbsent(item.name(), k -> new Row());
                row.label = item.label;
                row.color = ColorUtil.getColor(240);
                row.time.set(time(left));
                row.active = true;
            }

            PlayerEntity player = tracker.getTargetPlayer();

            if (player != null) {
                for (UseCooldowns.Buff buff : UseCooldowns.Buff.values()) {
                    if (!tracker.buffs.getValue(buff.label)) continue;

                    StatusEffectInstance effect = player.getStatusEffect(buff.effect);
                    if (effect == null) continue;

                    Row row = rows.computeIfAbsent(buff.name(), k -> new Row());
                    row.label = buff.label + roman(effect.getAmplifier() + 1);
                    row.color = buff.color;
                    row.time.set(time(effect.getDuration() / 20));
                    row.active = true;
                }
            }
        }

        // ник живёт только пока цель актуальна, но уезжает плавно — поэтому текст запоминаем
        String targetName = hasTarget ? tracker.getTargetName() : "";

        if (!targetName.isEmpty()) head = targetName;

        headAnimation.update();
        headAnimation.run(targetName.isEmpty() ? 0F : 1F, 0.12F, Easings.QUAD_OUT);

        float ha = headAnimation.get();

        boolean isEmpty = ha <= 0.01F && rows.values().stream().noneMatch(row -> row.active);

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        boolean closeCondition = isEmpty && !(mc.currentScreen instanceof ChatScreen);

        animation1.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        animation2.setDirection((mc.currentScreen instanceof ChatScreen) && isEmpty ? Direction.FORWARDS : Direction.BACKWARDS);

        dragSetting.active = !closeCondition;

        float alpha = animation1.getOutput();

        if (closeCondition && alpha == 0.0F) {
            rows.clear();
            head = "";
            return;
        }

        float alpha2 = animation2.getOutput();

        // Отрисовка состояния пустого списка (в чате)
        RenderUtil.Render2D.glow(x, y, MIN_W, H, ColorUtil.replAlpha(ColorUtil.getColor(0), alpha2 * 0.1F), RADIUS, 12, 1);
        RenderUtil.Blur.blur(x, y, MIN_W, H, alpha2, RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(alpha2 * InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

        Font font = Fonts.sf_regular;

        Fonts.decide_2.draw("V", x + TITLE_ICON_X, y + TITLE_ICON_Y, TITLE_ICON, ColorUtil.replAlpha(ColorUtil.client(), alpha2));
        font.draw("User Tracker", x + TITLE_TEXT_X, y + TITLE_TEXT_Y, TITLE_TEXT, ColorUtil.multAlpha(ColorUtil.getColor(240), alpha2));

        float h = BASE_H;
        float w = 0;

        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, Row> entry : rows.entrySet()) {
            Row row = entry.getValue();

            row.animation.update();
            row.animation.run(row.active ? 1F : 0F, 0.12F, Easings.QUAD_OUT);

            float a = row.animation.get();

            if (a <= 0.01F && !row.active) {
                toRemove.add(entry.getKey());
                continue;
            }

            float rowW = ROW_BASE_W + font.getWidth(row.label, ROW_TEXT) + row.time.width(font, ROW_TEXT);

            w = Math.max(w, rowW * a);
            h += ROW_HEIGHT * a;
        }

        toRemove.forEach(rows::remove);

        if (ha > 0.01F) {
            w = Math.max(w, (ROW_BASE_W + font.getWidth(head, ROW_TEXT)) * ha);
            h += ROW_HEIGHT * ha;
        }

        widthAnim += (w - widthAnim) * 0.2F;

        // Отрисовка основного фона
        RenderUtil.Render2D.glow(x, y, widthAnim, h - 0.5F, ColorUtil.replAlpha(ColorUtil.getColor(0), alpha * 0.1F), RADIUS, 12, 1);
        RenderUtil.Blur.blur(x, y, widthAnim, h, alpha, RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(alpha * InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

        float offsetY = y + ROW_START_Y;
        float offsetY2 = 0;

        // Отрисовка заголовка (ника)
        if (ha > 0.01F) {
            float addX =0;

            Fonts.category.draw("Q", x + ROW_PADDING_X - addX, offsetY + HEAD_ICON_Y, ROW_TEXT, ColorUtil.replAlpha(ColorUtil.client(), alpha * ha));
            font.draw(head, x + HEAD_TEXT_X - addX, offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha * ha));

            offsetY += ROW_HEIGHT * ha;
            offsetY2 += ROW_HEIGHT * ha;
        }

        boolean firstRow = ha <= 0.01F;

        // Отрисовка строк
        for (Row row : rows.values()) {

            float a = row.animation.get();
            if (a <= 0.01F && !row.active) continue;

            float addX = ROW_ANIM_OFFSET - ROW_ANIM_OFFSET * a;

            font.draw(row.label, x + ROW_PADDING_X - addX, offsetY, ROW_TEXT, ColorUtil.replAlpha(row.color, alpha * a));

            row.time.draw(font, x + addX - ROW_PADDING_X + widthAnim - row.time.width(font, ROW_TEXT), offsetY, ROW_TEXT,
                    ColorUtil.getColor(200, alpha * a));

            if (!firstRow) {
                RenderUtil.Render2D.rect(x + SEP_PADDING_X + addX, offsetY - SEP_OFFSET_Y, widthAnim - (SEP_PADDING_X * 2) - addX, 0.5F,
                        ColorUtil.getColor(255, 0.05F * alpha * a), 1);
            }

            firstRow = false;

            offsetY += ROW_HEIGHT * a;
            offsetY2 += ROW_HEIGHT * a;
        }

        dragSetting.size.set(ColorUtil.overCol((int) Math.max(widthAnim, 20 * S), (int) MIN_W, alpha2),
                ColorUtil.overCol((int) offsetY2, (int) H, alpha2));
    }

    private static String time(int seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }

    private static String roman(int level) {
        return switch (level) {
            case 1 -> "";
            case 2 -> " II";
            case 3 -> " III";
            case 4 -> " IV";
            case 5 -> " V";
            default -> " " + level;
        };
    }
}