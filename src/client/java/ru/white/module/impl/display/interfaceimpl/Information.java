package ru.white.module.impl.display.interfaceimpl;


import ru.white.module.api.settings.impl.DragSetting;
import ru.white.module.impl.display.InterFace;
import ru.white.theme.ThemeColor;
import ru.white.utils.colors.ColorFormatting;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.ServerUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.RollingText;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

public class Information implements element {

    /** Общий масштаб плашки: один множитель на шрифты, иконки и все отступы. */
    private static  float S = 1.0F;

    private static  float TEXT = 5.5F * S;
    private static  float ICON = 4.5F * S;

    private static  float H = 16F * S;
    private static  float RADIUS = 5F * S;

    private static  float ICON_X = 6F * S;
    private static  float ICON_Y = 5.7F * S;
    private static  float TEXT_X = 14F * S;
    private static  float TEXT_Y = 4.4F * S;

    private static  float BLOCK = 15F * S;
    private static  float AFTER_SEP = 7F * S;
    private static  float AFTER_ICON = 9F * S;

    private static final String SEPARATOR = " ; ";

    private final RollingText coordX = new RollingText(3F);
    private final RollingText coordY = new RollingText(3F);
    private final RollingText coordZ = new RollingText(3F);
    private final RollingText bpsText = new RollingText(3F);
    private final RollingText tpsText = new RollingText(3F);

    @Override
    public void onRender(DragSetting dragSetting, InterFace interFace) {


        S = InterFace.getInstance().sizeHud.getValue()* 1.1F;
        TEXT = 5.5F * S;
        ICON = 4.5F * S;
        H = 16F * S;
        RADIUS = 5F * S;
        ICON_X = 6F * S;
        ICON_Y = 5.7F * S;
        TEXT_X = 14F * S;
        TEXT_Y = 4.4F * S;
        BLOCK = 15F * S;
        AFTER_SEP = 7F * S;
        AFTER_ICON = 9F * S;

        float x = dragSetting.position.x;
        float y = dragSetting.position.y;



        double dx = mc.player.getX() - mc.player.lastX;
        double dz = mc.player.getZ() - mc.player.lastZ;

        float bps = (float) (Math.sqrt(dx * dx + dz * dz) * 20.0F);

        Font fonts = Fonts.sf_regular;


        coordX.set(String.valueOf((int) mc.player.getX()));
        coordY.set(String.valueOf((int) mc.player.getY()));
        coordZ.set(String.valueOf((int) mc.player.getZ()));

        bpsText.set(String.format(java.util.Locale.US, "%.1f", bps));
        tpsText.set(String.format(java.util.Locale.US, "%.1f", ServerUtil.TPS));

        float coordsW = coordX.width(fonts, TEXT) + coordY.width(fonts, TEXT) + coordZ.width(fonts, TEXT)
                + fonts.getWidth("xyz", TEXT) + fonts.getWidth(SEPARATOR, TEXT) * 2;

        float bpsW = bpsText.width(fonts, TEXT) + fonts.getWidth("bps", TEXT);
        float tpsW = tpsText.width(fonts, TEXT) + fonts.getWidth("tps", TEXT);


        float w = BLOCK + coordsW;

        // 15 + 5 + 15 + 3 + 4 + 15 отступов исходной вёрстки
        float w2 = 57F * S + coordsW + bpsW + tpsW;

        RenderUtil.Render2D.glow(x,y,w2,H - 0.5F,ColorUtil.getColor(0,0.1F),RADIUS,8,1);
        RenderUtil.Blur.blur(x,y,w2,H,1,RADIUS, ColorUtil.replAlpha(ColorUtil.background(), Math.min(InterFace.getInstance().alphaHUD.getValue() + 0.35F, 1.0F)));

        Fonts.wvisual_2.draw("W",x + ICON_X,y + ICON_Y,ICON,ColorUtil.getClientColor(1));


        float cursor = x + TEXT_X;

        cursor += drawValue(fonts, coordX, "x", cursor, y + TEXT_Y);
        cursor += drawSeparator(fonts, cursor, y + TEXT_Y);
        cursor += drawValue(fonts, coordY, "y", cursor, y + TEXT_Y);
        cursor += drawSeparator(fonts, cursor, y + TEXT_Y);
        cursor += drawValue(fonts, coordZ, "z", cursor, y + TEXT_Y);

        float x2 = x + w + 2 * S;


        Fonts.icon.draw("C",x2 ,y + ICON_Y,ICON, ThemeColor.getSeparatorColor());

        x2+= AFTER_SEP;

        Fonts.wvisual_2.draw("H",x2 ,y + ICON_Y,ICON, ThemeColor.getHudColor());

        x2+= AFTER_ICON;

        drawValue(fonts, bpsText, "bps", x2, y + TEXT_Y);


        float x3 = x + w + 20F * S + bpsW;


        Fonts.icon.draw("C",x3 ,y + ICON_Y,ICON, ThemeColor.getSeparatorColor());

        x3+= AFTER_SEP;

        Fonts.wvisual_2.draw("V",x3 ,y + ICON_Y,ICON, ThemeColor.getHudColor());

        x3+= AFTER_ICON;

        drawValue(fonts, tpsText, "tps", x3, y + TEXT_Y);


        dragSetting.size.set(w2,H);

    }

    /**
     * Число рисуется прокруткой, подпись — обычным текстом: RollingText выводит символы по
     * одному, и цветовой код внутри строки вылез бы буквами.
     */
    private float drawValue(Font font, RollingText value, String suffix, float x, float y) {
        value.draw(font, x, y, TEXT, ThemeColor.getTextColor());

        float width = value.width(font, TEXT);

        font.draw(suffix, x + width, y, TEXT, ColorUtil.getColor(220));

        return width + font.getWidth(suffix, TEXT);
    }

    private float drawSeparator(Font font, float x, float y) {
        font.draw(SEPARATOR, x, y, TEXT, ThemeColor.getDarkTextColor());

        return font.getWidth(SEPARATOR, TEXT);
    }
}
