package ru.white.module.impl.display.interfaceimpl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;

import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.Theme;
import ru.white.theme.ThemeColor;
import ru.white.theme.ThemeManager;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.RollingText;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.awt.*;

public class WaterMark implements element {

    /** Общий масштаб плашки: один множитель на шрифты, иконки и все отступы. */
    private static  float S = 1.0F;

    private static float TEXT = 5.5F * S;
    private static float ICON = 5F * S;
    private static float LOGO = 6F * S;

    private static float H = 16F * S;
    private static float RADIUS = 5F * S;

    private static float ICON_X = 6F * S;
    private static float ICON_Y = 5.5F * S;
    private static float TEXT_X = 14F * S;
    private static float TEXT_Y = 4.4F * S;

    private static float BLOCK = 15F * S;
    private static float AFTER_SEP = 7F * S;
    private static float AFTER_ICON = 9F * S;

    /** Цифры прокручиваются при смене значения — как таймеры в Potions. */
    private final RollingText fpsText = new RollingText(3F);
    private final RollingText pingText = new RollingText(3F);

    private float bossBarOffset = 0F;
    private static final float BOSS_BAR_HEIGHT = 19F;
    private static final float LERP_SPEED = 0.15F;

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;

        boolean hasBossBar = mc.inGameHud != null
                && mc.inGameHud.getBossBarHud() != null
                && !mc.inGameHud.getBossBarHud().bossBars.isEmpty();
        float target = hasBossBar ? BOSS_BAR_HEIGHT : 0F;
        bossBarOffset += (target - bossBarOffset) * LERP_SPEED;
        if (Math.abs(bossBarOffset - target) < 0.1F) bossBarOffset = target;
        y += bossBarOffset;

         S = InterFace.getInstance().sizeHud.getValue() * 1.1F;
         TEXT = 5.5F * S;
         ICON = 6F * S;
         LOGO = 6F * S;
         H = 16F * S;
         RADIUS = 5F * S;
         ICON_X = 6F * S;
         ICON_Y = 5.5F * S;
         TEXT_X = 14F * S;
         TEXT_Y = 4.4F * S;
         BLOCK = 15F * S;
         AFTER_SEP = 7F * S;
         AFTER_ICON = 9F * S;

        Font fonts = Fonts.sf_medium;

        float logoSize = 14F * S;

        RenderUtil.Render2D.glow(x, y, H, H - 0.5F, ColorUtil.getColor(0, 0.1F), RADIUS, 8, 1);
        RenderUtil.Blur.blur(x, y, H, H, 1, RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

        RenderUtil.Images.texture(Identifier.of("client", "textures/frame/logo.png"), x + (H - logoSize) / 2, y + (H - logoSize) / 2, logoSize, logoSize, ColorUtil.getClientColor(1));

        x += 18.5F * S;

        String user = InterFace.getInstance().userName.getValue();

        int pings = 0;

        if (mc.getNetworkHandler() != null) {
            var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            if (entry != null) {
                pings = entry.getLatency();
            }
        }

        fpsText.set(String.valueOf(mc.getCurrentFps()));
        pingText.set(String.valueOf(pings));

        float fpsW = fpsText.width(fonts, TEXT) + fonts.getWidth("fps", TEXT);
        float pingW = pingText.width(fonts, TEXT) + fonts.getWidth("ms", TEXT);

        float w = BLOCK + fonts.getWidth(user,TEXT);

        // 15 + 5 + 15 + 3 + 4 + 15 отступов исходной вёрстки
        float w2 = 57F * S + fonts.getWidth(user,TEXT) + fpsW + pingW;

        RenderUtil.Render2D.glow(x,y,w2,H - 0.5F,ColorUtil.getColor(0,0.1F),RADIUS,8,1);
        RenderUtil.Blur.blur(x,y,w2,H,1,RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

        Fonts.wvisual_2.draw("N",x + ICON_X,y + ICON_Y,ICON,ColorUtil.getClientColor(1));



       fonts.draw(user,x + TEXT_X,y + TEXT_Y,TEXT,ThemeColor.getTextColor());

       float x2 = x + w + 2 * S;


        Fonts.icon.draw("C",x2 ,y + ICON_Y,ICON, ThemeColor.getSeparatorColor());

        x2+= AFTER_SEP;

        RenderUtil.Images.texture(Identifier.of("client", "textures/hud/fps.png"), x2 + 0.5F * S, y + (H - ICON) / 2, ICON, ICON, ColorUtil.getClientColor(1));

        x2+= AFTER_ICON;

        drawValue(fonts, fpsText, "fps", x2, y + TEXT_Y);


        float x3 = x + w + 20F * S + fpsW;


        Fonts.icon.draw("C",x3 ,y + ICON_Y,ICON, ThemeColor.getSeparatorColor());

        x3+= AFTER_SEP;

        Fonts.wvisual_2.draw("R", x3 + 0.5F * S, y + ICON_Y, ICON, ColorUtil.getClientColor(1));

        x3+= AFTER_ICON;

        drawValue(fonts, pingText, "ms", x3, y + TEXT_Y);


        dragSetting.size.set(18.5F * S + w2,H);

    }

    /**
     * Число рисуется прокруткой, подпись — обычным текстом: RollingText выводит символы по
     * одному, и цветовой код внутри строки вылез бы буквами.
     */
    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, TEXT, ThemeColor.getTextColor());

        float width = value.width(font, TEXT);

        font.draw(suffix, x + width, y, TEXT, ColorUtil.getColor(200));

        return width + font.getWidth(suffix, TEXT);
    }
}
