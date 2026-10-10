package ru.decide.module.impl.display.interfaceimpl;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.Identifier;
import ru.decide.manager.event_impl.EventDisplay;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.animation.satoshi.Direction;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.ItemRender;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;

import java.util.*;

public class Potions implements IMinecraft {

    private static float S = 1.0F;

    private static float PILL_H = 14F * S;
    private static float PILL_GAP = 3F * S;
    private static float PILL_RADIUS = 7F * S;
    private static float PILL_TEXT = 6.5F * S;

    private static float MIN_W = 44F * S;
    private static float RADIUS = 5F * S;

    private static float ROW_TEXT = 6.5F * S;
    private static float ICON = 7F * S;
    private static float ROW_HEIGHT = 14F * S;
    private static float ROW_PADDING_X = 5F * S;
    private static float ROW_START_Y = 5F * S;
    private static float ICON_PADDING = 4F * S;
    private static float SCROLL_OFFSET_Y = 4F * S;

    private final ru.decide.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);

    private final Map<String, EffectData> displayedEffects = new LinkedHashMap<>();
    private final List<EffectData> sortedBuffer = new ArrayList<>();
    private final List<String> removeBuffer = new ArrayList<>();

    private static final String[][] PLACEHOLDERS = {
            {"Удача", "0:23", "mob_effect/luck"},
            {"Спешка", "1:49", "mob_effect/haste"},
            {"Скорость", "0:45", "mob_effect/speed"},
            {"Регенерация", "0:33", "mob_effect/regeneration"},
            {"Сила", "0:18", "mob_effect/strength"},
            {"Ночное зрение", "4:10", "mob_effect/night_vision"},
            {"Сопротивление", "0:55", "mob_effect/resistance"},
            {"Огн. сопротивление", "3:20", "mob_effect/fire_resistance"},
    };

    private int placeholderIndex = 0;
    private long placeholderLastTick = 0;
    private static final long PLACEHOLDER_INTERVAL_TICKS = 50;

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        S = InterFace.getInstance().sizeHud.getValue();

        PILL_H = 14F * S;
        PILL_GAP = 3F * S;
        PILL_RADIUS = 7F * S;
        PILL_TEXT = 6.5F * S;
        MIN_W = 44F * S;
        RADIUS = 5F * S;
        ROW_TEXT = 6.5F * S;
        ICON = 7F * S;
        ROW_HEIGHT = 14F * S;
        ROW_PADDING_X = 5F * S;
        ROW_START_Y = 5F * S;
        ICON_PADDING = 4F * S;
        SCROLL_OFFSET_Y = 4F * S;

        float chatBoost = (mc.currentScreen instanceof ChatScreen) ? 0.15F : 0F;

        Collection<StatusEffectInstance> currentEffects = mc.player.getStatusEffects();
        displayedEffects.values().forEach(data -> data.active = false);

        for (StatusEffectInstance effect : currentEffects) {
            String name = effect.getEffectType().value().getName().getString();
            int amplifier = effect.getAmplifier() + 1;
            String duration = getDurationString(effect);
            boolean isNegative = effect.getEffectType().value().getCategory() == StatusEffectCategory.HARMFUL;
            EffectData data = displayedEffects.computeIfAbsent(name, k -> new EffectData(name, duration, amplifier, isNegative, effect.getDuration(), effect));

            if (!duration.equals(data.duration)) {
                data.prevDuration = data.duration;
                data.duration = duration;
                data.digitAnim.set(0);
                data.digitAnim.run(1, 0.18, Easings.QUAD_OUT);
            }

            data.durationTicks = effect.getDuration();
            data.negative = isNegative;
            data.effectInstance = effect;
            data.active = true;
        }

        boolean isEmpty = displayedEffects.isEmpty();
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

        float rowPad = 1.5F * S;
        float h = rowPad;
        float w = 0;

        sortedBuffer.clear();
        sortedBuffer.addAll(displayedEffects.values());
        sortedBuffer.sort(Comparator.comparingInt((EffectData data) ->
                data.name.length() + data.duration.length()
        ).reversed());

        if (showPlaceholders) {
            long currentTick = mc.world != null ? mc.world.getTime() : 0;
            if (currentTick - placeholderLastTick >= PLACEHOLDER_INTERVAL_TICKS) {
                placeholderLastTick = currentTick;
                placeholderIndex = (placeholderIndex + 1) % PLACEHOLDERS.length;
            }
            for (int i = 0; i < 2; i++) {
                int idx = (placeholderIndex + i) % PLACEHOLDERS.length;
                String pName = PLACEHOLDERS[idx][0];
                String pTime = PLACEHOLDERS[idx][1];
                float nameW = font.getWidth(pName, ROW_TEXT);
                float durW = font.getWidth(pTime, ROW_TEXT);
                float rowW = ROW_PADDING_X + ICON + ICON_PADDING + nameW + ICON_PADDING + durW + ROW_PADDING_X;
                w = Math.max(w, rowW);
                h += ROW_HEIGHT;
            }
        } else {
            removeBuffer.clear();
            for (EffectData data : sortedBuffer) {
                data.animation.update();
                data.animation.run(data.active ? 1f : 0f, 0.12f, Easings.QUAD_OUT);
                data.digitAnim.update();

                float a = data.animation.get();

                if (a <= 0.01f && !data.active) {
                    removeBuffer.add(data.name);
                    continue;
                }

                float nameW = font.getWidth(label(data), ROW_TEXT);
                float durW = font.getWidth(data.duration, ROW_TEXT);
                float rowW = ROW_PADDING_X + ICON + ICON_PADDING + nameW + ICON_PADDING + durW + ROW_PADDING_X;
                w = Math.max(w, rowW * a);
                h += ROW_HEIGHT * a;
            }
        }

        h -= ROW_HEIGHT - ROW_TEXT - 3.5F * S;
        w = Math.max(w, MIN_W);

        float pillW = 55F * S;
        String pillText = "Зелья";
        float pillTextW = font.getWidth(pillText, PILL_TEXT);
        float pillIconSize = 8F * S;
        float pillIconW = pillIconSize;
        float pillX = x + (w - pillW) / 2;

        RenderUtil.Render2D.glow(pillX, pillY, pillW, PILL_H, ColorUtil.getColor(0, 0.1f * alpha), PILL_RADIUS, 8, 1);
        RenderUtil.Blur.blur(pillX, pillY, pillW, PILL_H, alpha, PILL_RADIUS,
                ColorUtil.replAlpha(ColorUtil.background(), Math.min(alpha * InterFace.getInstance().alphaHUD.getValue() + 0.5F + chatBoost, 1.0F)));

        float pillContentX = pillX + (pillW - pillIconW - 3F * S - pillTextW) / 2;
        ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), new net.minecraft.item.ItemStack(net.minecraft.item.Items.POTION), pillContentX, pillY + (PILL_H - pillIconSize) / 2, pillIconSize / 16F, alpha);
        font.draw(pillText, pillContentX + pillIconW + 3F * S, pillY + (PILL_H - PILL_TEXT) / 2, PILL_TEXT, ColorUtil.multAlpha(ColorUtil.getColor(240), alpha));

        RenderUtil.Render2D.glow(x, y, w, h - 0.5F, ColorUtil.replAlpha(ColorUtil.getColor(0), alpha * 0.1F), RADIUS, 12, 1);
        RenderUtil.Blur.blur(x, y, w, h, alpha, RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(alpha * InterFace.getInstance().alphaHUD.getValue() + 0.5F + chatBoost, 1.0F)));

        float offsetY = y + rowPad;

        if (showPlaceholders) {
            for (int i = 0; i < 2; i++) {
                int idx = (placeholderIndex + i) % PLACEHOLDERS.length;
                String pName = PLACEHOLDERS[idx][0];
                String pTime = PLACEHOLDERS[idx][1];
                String pTexId = PLACEHOLDERS[idx][2];

                Identifier effectTex = Identifier.of("minecraft", pTexId);
                var matrices = eventDisplay.getDrawContext().getMatrices();
                matrices.pushMatrix();
                float iconX = x + ROW_PADDING_X;
                float iconY = offsetY + (ROW_TEXT - ICON) * 0.3F;
                matrices.translate(iconX, iconY);
                matrices.scale(ICON / 12F, ICON / 12F);
                eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectTex,
                        0, 0, 12, 12, ColorUtil.getColor(255, alpha));
                matrices.popMatrix();

                font.draw(pName, x + ROW_PADDING_X + ICON + ICON_PADDING, offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha));

                float timeWidth = font.getWidth(pTime, ROW_TEXT);
                float timeX = x + w - ROW_PADDING_X - timeWidth;
                font.draw(pTime, timeX, offsetY, ROW_TEXT, ColorUtil.getColor(200, alpha));

                offsetY += ROW_HEIGHT;
            }
        } else {
            for (EffectData data : sortedBuffer) {
                float a = data.animation.get();
                if (a <= 0.01f && !data.active) continue;

                boolean bad = data.negative;
                int nameColor = bad ? ColorUtil.getColor(235, 70, 70, alpha * a) : ColorUtil.getColor(240, alpha * a);
                int timeColor = bad ? ColorUtil.getColor(200, 60, 60, alpha * a) : ColorUtil.getColor(200, alpha * a);

                Identifier effectTex = data.effectInstance.getEffectType().getKey()
                        .map(key -> key.getValue().withPrefixedPath("mob_effect/"))
                        .orElse(Identifier.ofVanilla("mob_effect/speed"));
                var matrices = eventDisplay.getDrawContext().getMatrices();
                matrices.pushMatrix();
                float iconX = x + ROW_PADDING_X;
                float iconY = offsetY + (ROW_TEXT - ICON) * 0.3F;
                matrices.translate(iconX, iconY);
                matrices.scale(ICON / 12F, ICON / 12F);
                eventDisplay.getDrawContext().drawGuiTexture(RenderPipelines.GUI_TEXTURED, effectTex,
                        0, 0, 12, 12, ColorUtil.getColor(255, alpha * a));
                matrices.popMatrix();

                String effectname = label(data);
                font.draw(effectname, x + ROW_PADDING_X + ICON + ICON_PADDING, offsetY, ROW_TEXT, nameColor);

                String key = data.duration;
                float timeWidth = font.getWidth(key, ROW_TEXT);
                float timeX = x + w - ROW_PADDING_X - timeWidth;
                drawDuration(font, data, timeX, offsetY, ROW_TEXT, timeColor);

                offsetY += ROW_HEIGHT * a;
            }
            removeBuffer.forEach(displayedEffects::remove);
        }

        float totalW = Math.max(w, MIN_W);
        float totalX = Math.min(x, pillX);
        float totalRight = Math.max(x + totalW, pillX + pillW);
        float totalWidth = totalRight - totalX;

        dragSetting.size.set(totalWidth, h + PILL_H + PILL_GAP);
    }

    private void drawDuration(Font font, EffectData data, float x, float y, float size, int color) {
        float t = data.digitAnim.get();
        String now = data.duration;
        String was = data.prevDuration == null ? now : data.prevDuration;
        float cx = x;

        for (int i = 0; i < now.length(); i++) {
            String ch = now.substring(i, i + 1);
            int j = was.length() - (now.length() - i);
            String old = j >= 0 && j < was.length() ? was.substring(j, j + 1) : null;

            if (t >= 1F || ch.equals(old)) {
                font.draw(ch, cx, y, size, color);
            } else {
                font.draw(ch, cx, y + SCROLL_OFFSET_Y * (1 - t), size, ColorUtil.multAlpha(color, t));
                if (old != null) font.draw(old, cx, y - SCROLL_OFFSET_Y * t, size, ColorUtil.multAlpha(color, 1 - t));
            }
            cx += font.getWidth(ch, size);
        }
    }

    private String label(EffectData data) {
        int lvl = data.effectInstance.getAmplifier() + 1;
        return lvl > 1 ? data.name + " " + lvl : data.name;
    }

    private String getDurationString(StatusEffectInstance effect) {
        if (effect.isInfinite()) return "\u221e";
        int t = effect.getDuration() / 20;
        return String.format("%d:%02d", t / 60, t % 60);
    }

    private static class EffectData {
        String name;
        String duration;
        String prevDuration;
        final Animation digitAnim = new Animation();
        Animation animation;
        boolean active;
        boolean negative;
        int durationTicks;
        int lvl;
        StatusEffectInstance effectInstance;

        public EffectData(String name, String duration, int lvl, boolean negative, int durationTicks, StatusEffectInstance effectInstance) {
            this.name = name;
            this.duration = duration;
            this.prevDuration = duration;
            this.lvl = lvl;
            this.negative = negative;
            this.durationTicks = durationTicks;
            this.animation = new Animation();
            this.active = true;
            this.effectInstance = effectInstance;
        }
    }
}
