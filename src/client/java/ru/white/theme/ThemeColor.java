package ru.white.theme;

import ru.white.Client;
import ru.white.utils.colors.ColorUtil;

/**
 * Static color getters for the active theme.
 *
 * NOTE: these are intentionally NOT wired into the existing rendering yet —
 * they exist so the rest of the client can start reading theme colors when needed.
 */
public final class ThemeColor {

    private ThemeColor() {}

    /** Цвет визуалов (акцент). */
    public static int getVisualColor() {
        return ColorUtil.client();
    }

    /** Цвет худа. */
    public static int getHudColor() {
        return ColorUtil.client();
    }

    public static int getHudColor(float alpha) {
        return ColorUtil.replAlpha(ColorUtil.client(),alpha);
    }


    /** Светлый текст. */
    public static int getTextColor() {
        return ColorUtil.getColor(255);
    }

    /** Тёмный текст. */
    public static int getDarkTextColor() {
        return ColorUtil.getColor(175);
    }

    /** Цвет фона. */
    public static int getBackgroundColor() {
        return ThemeManager.get().getActive().get(Theme.BACKGROUND);
    }

    /** Цвет обводки. */
    public static int getOutlineColor() {
        return ThemeManager.get().getActive().get(Theme.OUTLINE);
    }

    /** Цвет разделителей. */
    public static int getSeparatorColor() {
        return ColorUtil.getColor(255,0.1F);
    }

    /** Цвет светлого фона. */
    public static int getLightBackgroundColor() {
        return ThemeManager.get().getActive().get(Theme.LIGHT_BG);
    }


    /** Theme color by index (0..5), see constants in {@link Theme}. */
    public static int byIndex(int index) {
        return ThemeManager.get().getActive().get(index);
    }

    /** Прозрачность темы (0.1..1.0). */
    public static float getOpacity() {
        return ThemeManager.get().getActive().getSlider(Theme.OPACITY);
    }

    /** Сила размытия темы (0..6), округлённая до int. */
    public static int getBlur() {
        return Math.round(ThemeManager.get().getActive().getSlider(Theme.BLUR));
    }

    /** Эффект тени темы. */
    public static boolean getShadow() {
        return ThemeManager.get().getActive().getBool(Theme.SHADOW);
    }

    /** Эффект искажения темы. */
    public static boolean getDistortion() {
        return ThemeManager.get().getActive().getBool(Theme.DISTORTION);
    }


    public static int getSeparatorColor(float alpha) {
        return ColorUtil.multAlpha(ThemeManager.get().getActive().get(Theme.SEPARATOR),alpha);
    }

    /** Светлый текст. */
    public static int getTextColor(float alpha) {
        return ColorUtil.replAlpha(ThemeManager.get().getActive().get(Theme.TEXT),alpha);
    }
    public static int getLightBackgroundColor(float alpha) {
        return ColorUtil.multAlpha(ThemeManager.get().getActive().get(Theme.LIGHT_BG),alpha);
    }
    public static int getOutlineColor(float alpha) {
        return ColorUtil.multAlpha(ThemeManager.get().getActive().get(Theme.OUTLINE),alpha);
    }

    /** Тёмный текст. */
    public static int getDarkTextColor(float alpha) {
        return ColorUtil.replAlpha(ThemeManager.get().getActive().get(Theme.TEXT2),alpha);
    }

    /** Цвет фона. */
    public static int getBackgroundColor(float alpha) {
        return ColorUtil.replAlpha(ThemeManager.get().getActive().get(Theme.BACKGROUND),alpha);
    }
}
