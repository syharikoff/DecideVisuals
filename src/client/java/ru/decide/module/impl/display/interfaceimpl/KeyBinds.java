package ru.decide.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.Identifier;
import ru.decide.Client;
import ru.decide.module.api.Module;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.animation.satoshi.Direction;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.math.Keyboard;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;

import java.util.*;

public class KeyBinds implements element {

    private static float S = 1.0F;

    private static float PILL_H = 14F * S;
    private static float PILL_GAP = 3F * S;
    private static float PILL_RADIUS = 7F * S;
    private static float PILL_TEXT = 6.5F * S;

    private static float MIN_W = 50F * S;
    private static float RADIUS = 5F * S;

    private static float ROW_TEXT = 6.5F * S;
    private static float ROW_HEIGHT = 14F * S;
    private static float ROW_PADDING_X = 5F * S;
    private static float ROW_START_Y = 5F * S;

    private static float ICON_PADDING = 4F * S;
    private static float ICON_OFFSET_Y = 0.9F * S;

    private final ru.decide.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);

    private static final String[][] PLACEHOLDERS = {
            {"Атмосфера", "R", "Q"},
            {"Спринт", "X", "Q"},
            {"Полная яркость", "H", "Q"},
            {"Клик ГУИ", "RIGHT_SHIFT", "H"},
            {"Без падения", "V", "Q"},
            {"Полёт", "G", "L"},
            {"Скорость", "B", "L"},
            {"Отдача", "J", "L"},
            {"Анти-голод", "N", "L"},
            {"Быстрый блок", "F", "L"},
    };

    private int placeholderIndex = 0;
    private long placeholderLastTick = 0;
    private static final long PLACEHOLDER_INTERVAL_TICKS = 50;
    private final List<Module> modulesBuffer = new ArrayList<>();

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        S = InterFace.getInstance().sizeHud.getValue();

        PILL_H = 14F * S;
        PILL_GAP = 3F * S;
        PILL_RADIUS = 7F * S;
        PILL_TEXT = 6.5F * S;
        MIN_W = 50F * S;
        RADIUS = 5F * S;
        ROW_TEXT = 6.5F * S;
        ROW_HEIGHT = 14F * S;
        ROW_PADDING_X = 5F * S;
        ROW_START_Y = 5F * S;
        ICON_PADDING = 4F * S;
        ICON_OFFSET_Y = 0.9F * S;

        modulesBuffer.clear();
        for (Module m : Client.get().moduleManager().values()) {
            if (m.getKey() > 0
                    && !m.getName().equalsIgnoreCase("ClickGui")
                    && (m.isEnabled() || m.getAnimation().getOutput() > 0)) {
                modulesBuffer.add(m);
            }
        }
        modulesBuffer.sort(Comparator.comparingInt((Module m) ->
                m.getName().length() + Keyboard.keyName(m.getKey()).length()
        ).reversed());
        List<Module> modules = modulesBuffer;

        boolean isEmpty = modules.isEmpty();
        boolean inChat = mc.currentScreen instanceof ChatScreen;
        boolean showPlaceholders = inChat;

        float x = dragSetting.position.x;
        float pillY = dragSetting.position.y;
        float y = pillY + PILL_H + PILL_GAP;

        boolean closeCondition = isEmpty && !inChat;

        animation1.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        dragSetting.active = !closeCondition;

        float alpha = animation1.getOutput();
        if (closeCondition && alpha == 0.0F) return;

        Font font = Fonts.sf_regular;
        Font cat = Fonts.decide_2;

        float rowPad = 1.5F * S;
        float h = rowPad;
        float w = 0;

        if (showPlaceholders) {
            long currentTick = mc.world != null ? mc.world.getTime() : 0;
            if (currentTick - placeholderLastTick >= PLACEHOLDER_INTERVAL_TICKS) {
                placeholderLastTick = currentTick;
                placeholderIndex = (placeholderIndex + 1) % PLACEHOLDERS.length;
            }
            for (int i = 0; i < 2; i++) {
                int idx = (placeholderIndex + i) % PLACEHOLDERS.length;
                String pName = PLACEHOLDERS[idx][0];
                String pKey = PLACEHOLDERS[idx][1];
                String pIcon = PLACEHOLDERS[idx][2];
                float rowW = ROW_PADDING_X + cat.getWidth(pIcon, ROW_TEXT) + ICON_PADDING
                        + font.getWidth(pName, ROW_TEXT) + ICON_PADDING
                        + font.getWidth(pKey, ROW_TEXT) + ROW_PADDING_X;
                w = Math.max(w, rowW);
                h += ROW_HEIGHT;
            }
        } else {
            for (Module m : modules) {
                m.animation.setDirection(m.isEnabled() ? Direction.FORWARDS : Direction.BACKWARDS);
                float mAnim = m.getAnimation().getOutput();
                if (mAnim <= 0) continue;

                String icon = m.getCategory().getIcon();
                float rowW = ROW_PADDING_X + cat.getWidth(icon, ROW_TEXT) + ICON_PADDING
                        + font.getWidth(m.getBigName(), ROW_TEXT) + ICON_PADDING
                        + font.getWidth(Keyboard.keyName(m.getKey()), ROW_TEXT) + ROW_PADDING_X;
                w = Math.max(w, rowW * mAnim);
                h += ROW_HEIGHT * mAnim;
            }
        }

        h -= ROW_HEIGHT - ROW_TEXT - 3.5F * S;
        w = Math.max(w, MIN_W);

        String pillText = "Бинды";
        float pillTextW = font.getWidth(pillText, PILL_TEXT);
        float pillIconSize = 8F * S;
        float pillIconW = pillIconSize;
        float totalPillContentW2 = pillIconW + 3F * S + pillTextW + 8F * S;
        float pillW = Math.max(60F * S, totalPillContentW2);
        float pillX = x + (w - pillW) / 2;

        RenderUtil.Render2D.glow(pillX, pillY, pillW, PILL_H, ColorUtil.getColor(0, 0.1f * alpha), PILL_RADIUS, 8, 1);
        float chatBoost = (inChat) ? 0.15F : 0F;
        RenderUtil.Blur.blur(pillX, pillY, pillW, PILL_H, alpha, PILL_RADIUS,
                ColorUtil.replAlpha(ColorUtil.background(), Math.min(alpha * InterFace.getInstance().alphaHUD.getValue() + 0.5F + chatBoost, 1.0F)));
        float pillContentX = pillX + (pillW - pillIconW - 3F * S - pillTextW) / 2;
        try { RenderUtil.Images.texture(Identifier.of("decide", "textures/frame/binds.png"), pillContentX, pillY + (PILL_H - pillIconSize) / 2, pillIconSize, pillIconSize, ColorUtil.replAlpha(ColorUtil.client(), alpha)); } catch (Exception ignored) {}
        font.draw(pillText, pillContentX + pillIconW + 3F * S, pillY + (PILL_H - PILL_TEXT) / 2, PILL_TEXT, ColorUtil.multAlpha(ColorUtil.getColor(240), alpha));

        RenderUtil.Render2D.glow(x, y, w, h - 0.5F, ColorUtil.replAlpha(ColorUtil.getColor(0), alpha * 0.1F), RADIUS, 12, 1);
        RenderUtil.Blur.blur(x, y, w, h, alpha, RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(alpha * InterFace.getInstance().alphaHUD.getValue() + 0.5F + chatBoost, 1.0F)));

        float offsetY = y + rowPad;

        if (showPlaceholders) {
            for (int i = 0; i < 2; i++) {
                int idx = (placeholderIndex + i) % PLACEHOLDERS.length;
                String pName = PLACEHOLDERS[idx][0];
                String pKey = PLACEHOLDERS[idx][1];
                String pIcon = PLACEHOLDERS[idx][2];

                float textX = x + ROW_PADDING_X;
                cat.draw(pIcon, textX, offsetY + ICON_OFFSET_Y, ROW_TEXT, ColorUtil.replAlpha(ColorUtil.client(), alpha));
                textX += cat.getWidth(pIcon, ROW_TEXT) + ICON_PADDING;

                font.draw(pName, textX, offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha));

                float keyWidth = font.getWidth(pKey, ROW_TEXT);
                font.draw(pKey, x + w - ROW_PADDING_X - keyWidth, offsetY, ROW_TEXT, ColorUtil.getColor(200, alpha));

                offsetY += ROW_HEIGHT;
            }
        } else {
            for (Module m : modules) {
                float mAnim = m.getAnimation().getOutput();
                if (mAnim <= 0) continue;

                String icon = m.getCategory().getIcon();
                float textX = x + ROW_PADDING_X;
                cat.draw(icon, textX, offsetY + ICON_OFFSET_Y, ROW_TEXT, ColorUtil.replAlpha(ColorUtil.client(), alpha * mAnim));
                textX += cat.getWidth(icon, ROW_TEXT) + ICON_PADDING;

                font.draw(m.getBigName(), textX, offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha * mAnim));

                String key = Keyboard.keyName(m.getKey());
                float keyWidth = font.getWidth(key, ROW_TEXT);
                font.draw(key, x + w - ROW_PADDING_X - keyWidth, offsetY, ROW_TEXT, ColorUtil.getColor(200, alpha * mAnim));

                offsetY += ROW_HEIGHT * mAnim;
            }
        }

        float totalW = Math.max(w, MIN_W);
        float totalX = Math.min(x, pillX);
        float totalRight = Math.max(x + totalW, pillX + pillW);
        float totalWidth = totalRight - totalX;

        dragSetting.size.set(totalWidth, h + PILL_H + PILL_GAP);
    }
}
