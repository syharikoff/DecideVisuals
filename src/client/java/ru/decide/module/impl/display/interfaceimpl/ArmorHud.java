package ru.decide.module.impl.display.interfaceimpl;

import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import ru.decide.module.api.settings.impl.DragSetting;
import ru.decide.module.impl.display.InterFace;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.animation.satoshi.Direction;
import ru.decide.utils.animation.satoshi.EaseInOutQuad;
import ru.decide.utils.annotation.IMinecraft;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.render.ItemRender;
import ru.decide.utils.render.RenderUtil;
import ru.decide.utils.render.font.Font;
import ru.decide.utils.render.font.Fonts;

public class ArmorHud implements element {

    private static float S = 1.0F;
    private static float RADIUS = 4F * S;
    private static float ROW_HEIGHT = 16F * S;
    private static float ROW_TEXT = 6.5F * S;
    private static float ICON_SIZE = 8F * S;
    private static float PAD_X = 5F * S;
    private static float PAD_Y = 3F * S;
    private static float BAR_H = 2F * S;
    private static float BAR_GAP = 1.5F * S;
    private static float ICON_TEXT_GAP = 3F * S;

    private final Animation[] rowAnims = {new Animation(), new Animation(), new Animation(), new Animation()};
    private final float[] smoothDur = {0, 0, 0, 0};

    private ru.decide.utils.animation.satoshi.Animation openAnim = new EaseInOutQuad(300, 1);

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {
        S = InterFace.getInstance().sizeHud.getValue();
        RADIUS = 4F * S;
        ROW_HEIGHT = 16F * S;
        ROW_TEXT = 6.5F * S;
        ICON_SIZE = 8F * S;
        PAD_X = 5F * S;
        PAD_Y = 3F * S;
        BAR_H = 2F * S;
        BAR_GAP = 1.5F * S;
        ICON_TEXT_GAP = 3F * S;

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
        float alpha = 0;
        for (int i = 0; i < 4; i++) {
            rowAnims[i].update();
            ItemStack stack = mc.player.getEquippedStack(slots[i]);
            boolean hasItem = !stack.isEmpty();
            rowAnims[i].run(hasItem ? 1f : 0f, 0.15f, Easings.SINE_OUT);
            alpha = Math.max(alpha, rowAnims[i].get());
        }

        boolean closeCondition = alpha < 0.01f && !(mc.currentScreen instanceof ChatScreen);
        openAnim.setDirection(closeCondition ? Direction.BACKWARDS : Direction.FORWARDS);
        float oa = openAnim.getOutput();

        dragSetting.active = oa > 0.01f;
        if (oa <= 0.01f) return;

        float totalH = 0;
        for (int i = 0; i < 4; i++) totalH += ROW_HEIGHT * rowAnims[i].get();
        if (totalH < ROW_HEIGHT) totalH = ROW_HEIGHT;
        totalH += PAD_Y * 2 * oa;

        float maxW = 50F * S;
        Font font = Fonts.sf_medium;
        for (int i = 0; i < 4; i++) {
            float a = rowAnims[i].get();
            if (a <= 0.01f) continue;
            ItemStack stack = mc.player.getEquippedStack(slots[i]);
            int dur = stack.isEmpty() ? 0 : stack.getMaxDamage() - stack.getDamage();
            smoothDur[i] += (dur - smoothDur[i]) * 0.15f;
            String durStr = String.valueOf(Math.round(smoothDur[i]));
            float w = ICON_SIZE + ICON_TEXT_GAP + font.getWidth(durStr, ROW_TEXT) + PAD_X * 2;
            if (w > maxW) maxW = w;
        }

        float barMaxW = maxW - PAD_X * 2;

        RenderUtil.Render2D.glow(x, y, maxW, totalH - 0.5F, ColorUtil.getColor(0, 0.1f * oa), RADIUS, 8, 1);
        RenderUtil.Blur.blur(x, y, maxW, totalH, 1f, RADIUS,
                ColorUtil.replAlpha(ColorUtil.background(), Math.min(oa * InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

        float cy = y + PAD_Y * oa;
        for (int i = 0; i < 4; i++) {
            float a = rowAnims[i].get();
            if (a <= 0.01f) continue;
            ItemStack stack = mc.player.getEquippedStack(slots[i]);
            int dur = stack.isEmpty() ? 0 : stack.getMaxDamage() - stack.getDamage();
            int maxDur = stack.isEmpty() ? 1 : stack.getMaxDamage();

            float pct = stack.isEmpty() ? 0f : (float) dur / maxDur;

            int durColor;
            if (stack.isEmpty()) durColor = ColorUtil.getColor(150, a * oa * 0.3f);
            else if (pct > 0.5f) durColor = ColorUtil.replAlpha(ColorUtil.client(), a * oa);
            else if (pct > 0.2f) durColor = ColorUtil.getColor(255, 200, 50, a * oa);
            else durColor = ColorUtil.getColor(255, 70, 70, a * oa);

            float textY = cy + 1.5F * S;

            float iconX = x + PAD_X;
            float iconY = textY + (ROW_TEXT - ICON_SIZE) * 0.5F;
            ItemRender.drawItem(stack, iconX, iconY, ICON_SIZE / 16F, a * oa);

            float textX = x + PAD_X + ICON_SIZE + ICON_TEXT_GAP;
            font.draw(String.valueOf(Math.round(smoothDur[i])), textX, textY, ROW_TEXT, durColor);

            float barY = textY + ROW_TEXT + BAR_GAP;
            float barW = barMaxW * pct * a;

            int barBg = ColorUtil.getColor(255, 0.12f * a * oa);
            int barFill;
            if (stack.isEmpty()) barFill = ColorUtil.getColor(150, a * oa * 0.3f);
            else if (pct > 0.5f) barFill = ColorUtil.replAlpha(ColorUtil.client(), a * oa);
            else if (pct > 0.2f) barFill = ColorUtil.getColor(255, 200, 50, a * oa);
            else barFill = ColorUtil.getColor(255, 70, 70, a * oa);

            RenderUtil.Render2D.rect(x + PAD_X, barY, barMaxW, BAR_H, barBg, BAR_H / 2F);
            if (barW > 0.5F) {
                RenderUtil.Render2D.rect(x + PAD_X, barY, Math.max(barW, BAR_H), BAR_H, barFill, BAR_H / 2F);
            }

            cy += ROW_HEIGHT * a;
        }

        dragSetting.size.set(maxW, totalH);
    }
}
