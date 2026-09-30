package ru.white.manager;

import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;

import java.awt.*;

public enum Theme {
    Purple("Purple",
            new Color(216, 68, 234).getRGB()
            ,   new Color(0x99240D2B, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(216, 68, 234).getRGB()),
    NIGHT("Blue",
            new Color(0x8BA2FF).getRGB(),
             new Color(0x9912192B, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0x8BA2FF).getRGB()),
    AKAR("Red",
            new Color(0xFF8B8B).getRGB()
            ,   new Color(0x991B0C0C, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0xFF8B8B).getRGB()),
    VIOLKA("Violet",
            new Color(0xA08BFF).getRGB()
            ,   new Color(0x99151024, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0xA08BFF).getRGB()),

    TOXIS("Toxis",
                   new Color(0x97FF8B).getRGB()
            ,   new Color(0x99101E0D, true).getRGB()
            , ColorUtil.getColor(240)
                    ,ColorUtil.getColor(160)
                    ,ColorUtil.getColor(24,24,27),
                    new Color(0xB9FF8B).getRGB()),
    White("White",
            new Color(0xDFDFDF).getRGB()
            ,   new Color(0x99000000, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(0xDFDFDF).getRGB()),
    Turquoise("Turquoise",
            new Color(96, 255, 198).getRGB()
            ,   new Color(0x99132B2E, true).getRGB()
            , ColorUtil.getColor(240)
            ,ColorUtil.getColor(160)
            ,ColorUtil.getColor(24,24,27),
            new Color(96, 255, 198).getRGB());
    private final String name;
    private final int client;
    private final int rect;
    private final int text;
    private final int text_dark;
    private final int rect_two;
    private final int text_client_c;

    public Animation animation = new EaseInOutQuad(300, 1);

    Theme(String name, int client,int rect,int text,int text_dark,int rect_two,int text_client_c) {
        this.name = name;
        this.client = client;
        this.rect = rect;
        this.text = text;
        this.text_dark = text_dark;
        this.rect_two = rect_two;
        this.text_client_c = text_client_c;
    }

    public String getName() {
        return name;
    }

    public int getClient() {
        return client;
    }

    public int getRect() {
        return rect;
    }
    public int getText() {
        return text;
    }
    public int getText_dark() {
        return text_dark;
    }
    public int getRect_two() {
        return rect_two;
    }
    public int getText_client_c() {
        return text_client_c;
    }
}
