package ru.decide.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.animation.satoshi.Direction;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.manager.event_impl.EventDisplay;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.ItemRender;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;

import java.util.*;

public class Cooldowns implements IMinecraft {

    private static float S = 1.0F;
    private static float MIN_W = 50F * S;
    private static float RADIUS = 5F * S;

    private static float PILL_H = 14F * S;
    private static float PILL_GAP = 3F * S;
    private static float PILL_RADIUS = 7F * S;
    private static float PILL_TEXT = 6.5F * S;

    private static float ROW_HEIGHT = 14F * S;
    private static float ROW_TEXT = 6.5F * S;
    private static float ICON_SIZE = 7F * S;
    private static float ROW_PADDING_X = 5F * S;
    private static float ROW_START_Y = 5F * S;
    private static float ICON_PADDING = 4F * S;

    private final ru.decide.utils.animation.satoshi.Animation animation1 = new EaseInOutQuad(300, 1);
    private final ru.decide.utils.animation.satoshi.Animation animation2 = new EaseInOutQuad(300, 1);

    private final Map<String, CooldownData> displayedCooldowns = new LinkedHashMap<>();
    private final List<CooldownData> sortedBuffer = new ArrayList<>();
    private final List<String> removeBuffer = new ArrayList<>();

    private static final Item[] TRACKED_ITEMS = {
            Items.ENDER_PEARL,
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.CHORUS_FRUIT,
            Items.DRIED_KELP,
            Items.SHIELD
    };

    private static final Object[][] PLACEHOLDERS = {
            {"Эндер жемчуг", "0:03", Items.ENDER_PEARL},
            {"Золотое яблоко", "0:05", Items.GOLDEN_APPLE},
            {"Зачар. золотое яблоко", "0:10", Items.ENCHANTED_GOLDEN_APPLE},
            {"Плод хоруса", "0:02", Items.CHORUS_FRUIT},
            {"Щит", "0:10", Items.SHIELD},
            {"Суш. ламинария", "0:01", Items.DRIED_KELP},
    };

    private int placeholderIndex = 0;
    private long placeholderLastTick = 0;
    private static final long PLACEHOLDER_INTERVAL_TICKS = 40;

    private final Map<String, Long> cooldownStartTick = new LinkedHashMap<>();

    public void onRender(DragSetting dragSetting, InterFace interFace, EventDisplay eventDisplay) {
        S = InterFace.getInstance().sizeHud.getValue();
        MIN_W = 50F * S;
        RADIUS = 5F * S;
        PILL_H = 14F * S;
        PILL_GAP = 3F * S;
        PILL_RADIUS = 7F * S;
        PILL_TEXT = 6.5F * S;
        ROW_HEIGHT = 14F * S;
        ROW_TEXT = 6.5F * S;
        ICON_SIZE = 7F * S;
        ROW_PADDING_X = 5F * S;
        ROW_START_Y = 5F * S;
        ICON_PADDING = 4F * S;

        float chatBoost = (mc.currentScreen instanceof ChatScreen) ? 0.15F : 0F;

        displayedCooldowns.values().forEach(data -> data.active = false);

        if (mc.player != null) {
            long currentTick = mc.world != null ? mc.world.getTime() : 0;
            for (Item item : TRACKED_ITEMS) {
                ItemStack stack = new ItemStack(item);
                String name = item.getName().getString();
                if (mc.player.getItemCooldownManager().isCoolingDown(stack)) {
                    float progress = mc.player.getItemCooldownManager().getCooldownProgress(stack, 1.0f);
                    if (progress > 0f) {
                        if (!cooldownStartTick.containsKey(name)) {
                            cooldownStartTick.put(name, currentTick);
                        }
                        long startTick = cooldownStartTick.get(name);
                        long elapsed = currentTick - startTick;
                        String timeStr;
                        if (elapsed > 0 && progress < 1.0f) {
                            long totalTicks = Math.round(elapsed / (1.0f - progress));
                            long remaining = Math.max(0, totalTicks - elapsed);
                            int secs = (int) Math.round(remaining / 20.0);
                            timeStr = String.format("%d:%02d", secs / 60, secs % 60);
                        } else {
                            timeStr = "0:01";
                        }
                        CooldownData data = displayedCooldowns.computeIfAbsent(name, k -> new CooldownData(name, timeStr, stack));
                        data.time = timeStr;
                        data.progress = progress;
                        data.active = true;
                    }
                } else {
                    cooldownStartTick.remove(name);
                }
            }
        }

        boolean isEmpty = displayedCooldowns.isEmpty();
        boolean inChat = mc.currentScreen instanceof ChatScreen;
        boolean showPlaceholders = inChat;

        float x = dragSetting.position.x;
        float pillY = dragSetting.position.y;
        float y = pillY + PILL_H + PILL_GAP;

        boolean closeCondition = isEmpty && !inChat;
        animation1.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        animation2.setDirection(inChat && isEmpty ? Direction.FORWARDS : Direction.BACKWARDS);

        dragSetting.active = !closeCondition;
        float alpha = animation1.getOutput();
        float alpha2 = animation2.getOutput();

        Font font = Fonts.sf_regular;

        float pillW = 60F * S;
        String pillText = "Кулдауны";
        float pillTextW = font.getWidth(pillText, PILL_TEXT);
        float pillIconSize = 8F * S;
        float pillIconW = pillIconSize;
        float totalPillW2 = pillIconW + 3F * S + pillTextW + 8F * S;
        pillW = Math.max(pillW, totalPillW2);
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
                String pName = (String) PLACEHOLDERS[idx][0];
                String pTime = (String) PLACEHOLDERS[idx][1];
                float rowW = ROW_PADDING_X + ICON_SIZE + ICON_PADDING + font.getWidth(pName, ROW_TEXT) + ICON_PADDING + font.getWidth(pTime, ROW_TEXT) + ROW_PADDING_X;
                w = Math.max(w, rowW);
                h += ROW_HEIGHT;
            }
        } else {
            sortedBuffer.clear();
            sortedBuffer.addAll(displayedCooldowns.values());
            for (CooldownData data : sortedBuffer) {
                data.animation.update();
                data.animation.run(data.active ? 1f : 0f, 0.12f, Easings.QUAD_OUT);
                float a = data.animation.get();
                if (a <= 0.01f && !data.active) continue;

                float rowW = ROW_PADDING_X + ICON_SIZE + ICON_PADDING + font.getWidth(data.name, ROW_TEXT) + ICON_PADDING + font.getWidth(data.time, ROW_TEXT) + ROW_PADDING_X;
                w = Math.max(w, rowW * a);
                h += ROW_HEIGHT * a;
            }
        }

        h -= ROW_HEIGHT - ROW_TEXT - 3.5F * S;
        w = Math.max(w, MIN_W);
        float pillX = x + (w - pillW) / 2;

        float totalW = Math.max(w, MIN_W);
        float totalX = Math.min(x, pillX);
        float totalRight = Math.max(x + totalW, pillX + pillW);
        float totalWidth = totalRight - totalX;

        dragSetting.size.set(totalWidth, h + PILL_H + PILL_GAP);

        if (closeCondition && alpha == 0.0F && alpha2 == 0.0F) return;

        float renderAlpha = Math.max(alpha, alpha2);
        RenderUtil.Render2D.glow(pillX, pillY, pillW, PILL_H, ColorUtil.getColor(0, 0.1f * renderAlpha), PILL_RADIUS, 8, 1);
        RenderUtil.Blur.blur(pillX, pillY, pillW, PILL_H, renderAlpha, PILL_RADIUS,
                ColorUtil.replAlpha(ColorUtil.background(), Math.min(renderAlpha * InterFace.getInstance().alphaHUD.getValue() + 0.5F + chatBoost, 1.0F)));

        float pillContentX = pillX + (pillW - pillIconW - 3F * S - pillTextW) / 2;
        try { RenderUtil.Images.texture(Identifier.of("decide", "textures/frame/cooldown.png"), pillContentX, pillY + (PILL_H - pillIconSize) / 2, pillIconSize, pillIconSize, ColorUtil.replAlpha(ColorUtil.client(), renderAlpha)); } catch (Exception ignored) {}
        font.draw(pillText, pillContentX + pillIconW + 3F * S, pillY + (PILL_H - PILL_TEXT) / 2, PILL_TEXT, ColorUtil.multAlpha(ColorUtil.getColor(240), renderAlpha));

        RenderUtil.Render2D.glow(x, y, w, h - 0.5F, ColorUtil.getColor(0, 0.1f * renderAlpha), RADIUS, 8, 1);
        RenderUtil.Blur.blur(x, y, w, h, renderAlpha, RADIUS,
                ColorUtil.replAlpha(ColorUtil.background(), Math.min(renderAlpha * InterFace.getInstance().alphaHUD.getValue() + 0.5F + chatBoost, 1.0F)));

        float offsetY = y + rowPad;

        if (showPlaceholders) {
            for (int i = 0; i < 2; i++) {
                int idx = (placeholderIndex + i) % PLACEHOLDERS.length;
                String pName = (String) PLACEHOLDERS[idx][0];
                String pTime = (String) PLACEHOLDERS[idx][1];
                Item pItem = (Item) PLACEHOLDERS[idx][2];

                float iconX = x + ROW_PADDING_X;
                float iconY = offsetY + (ROW_TEXT - ICON_SIZE) * 0.3F;
                ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), new ItemStack(pItem), iconX, iconY, ICON_SIZE / 16F, alpha);

                float textX = iconX + ICON_SIZE + ICON_PADDING;
                font.draw(pName, textX, offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha));

                float timeW = font.getWidth(pTime, ROW_TEXT);
                font.draw(pTime, x + w - ROW_PADDING_X - timeW, offsetY, ROW_TEXT, ColorUtil.getColor(200, alpha));

                offsetY += ROW_HEIGHT;
            }
        } else {
            sortedBuffer.clear();
            sortedBuffer.addAll(displayedCooldowns.values());
            removeBuffer.clear();
            for (CooldownData data : sortedBuffer) {
                float a = data.animation.get();
                if (a <= 0.01f && !data.active) {
                    removeBuffer.add(data.name);
                    continue;
                }

                float iconX = x + ROW_PADDING_X;
                float iconY = offsetY + (ROW_TEXT - ICON_SIZE) * 0.3F;
                ItemRender.drawItemWithContext(eventDisplay.getDrawContext(), data.itemStack, iconX, iconY, ICON_SIZE / 16F, a * alpha);

                float textX = iconX + ICON_SIZE + ICON_PADDING;
                font.draw(data.name, textX, offsetY, ROW_TEXT, ColorUtil.getColor(240, alpha * a));

                float timeW = font.getWidth(data.time, ROW_TEXT);
                font.draw(data.time, x + w - ROW_PADDING_X - timeW, offsetY, ROW_TEXT, ColorUtil.getColor(200, alpha * a));

                offsetY += ROW_HEIGHT * a;
            }
            removeBuffer.forEach(displayedCooldowns::remove);
        }
    }



    private static class CooldownData {
        final String name;
        final ItemStack itemStack;
        String time;
        float progress;
        boolean active;
        final Animation animation = new Animation();

        CooldownData(String name, String time, ItemStack itemStack) {
            this.name = name;
            this.time = time;
            this.itemStack = itemStack;
            this.active = true;
        }
    }
}
