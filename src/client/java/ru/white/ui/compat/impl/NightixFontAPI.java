package ru.white.ui.compat.impl;

import ru.white.ui.compat.FontAPI;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

/**
 * DecideVisuals реализация FontAPI.
 * Делегирует в {@link Font} / {@link Fonts}.
 */
public final class NightixFontAPI implements FontAPI {

    private Font resolve(String name) {
        return switch (name.toLowerCase()) {
            case "montserrat-medium", "mont", "sf_medium" -> Fonts.sf_medium;
            case "small-pixel", "small", "sf_bold" -> Fonts.sf_bold;
            case "decidevisuals", "dv", "icongui" -> Fonts.gui;
            case "category" -> Fonts.category;
            default -> Fonts.sf_medium;
        };
    }

    @Override
    public void draw(String font, String text, float x, float y, float size, int color) {
        resolve(font).draw(text, x, y, size, color);
    }

    @Override
    public void drawCentered(String font, String text, float x, float y, float size, int color) {
        resolve(font).drawCentered(text, x, y, size, color);
    }

    @Override
    public float width(String font, String text, float size) {
        return resolve(font).getWidth(text, size);
    }

    @Override
    public float height(String font, float size) {
        return resolve(font).getHeight(size);
    }

    @Override
    public void msdf(String font, String text, float x, float y, float size, int color) {
        resolve(font).draw(text, x, y, size, color);
    }

    @Override
    public float msdfWidth(String font, String text, float size) {
        return resolve(font).getWidth(text, size);
    }

    @Override
    public void msdfFade(String font, String text, float x, float y, float size, int color,
                         float clipStartX, float clipEndX, float fadeWidth,
                         float fadeIn, float fadeOut) {
        resolve(font).drawFadingText(text, x, y, clipEndX - clipStartX, color, size);
    }

    @Override
    public void shimmer(String font, String text, float x, float y, float size,
                        float offset, float width, float strength, float alpha) {
        resolve(font).draw(text, x, y, size, 0xFFFFFF | (Math.round(alpha) << 24));
    }

    @Override
    public void wave(String font, String text, float x, float y, float size, int color, float amp) {
        float xOff = 0;
        for (int i = 0; i < text.length(); i++) {
            float waveY = (float) (y + Math.sin((xOff + offset()) * 0.05) * amp);
            resolve(font).draw(String.valueOf(text.charAt(i)), x + xOff, waveY, size, color);
            xOff += resolve(font).getWidth(String.valueOf(text.charAt(i)), size);
        }
    }

    private float offset() {
        return System.nanoTime() / 1_000_000f;
    }

    @Override
    public void drawFadingText(String font, String text, float x, float y,
                               float maxWidth, int color, float size) {
        resolve(font).drawFadingText(text, x, y, maxWidth, color, size);
    }

    @Override
    public void drawFadingTextReverse(String font, String text, float x, float y,
                                      float maxWidth, int color, float size) {
        resolve(font).drawFadingTextReverse(text, x, y, maxWidth, color, size);
    }

    @Override
    public void drawWrappedText(String font, String text, float x, float y,
                                float maxWidth, int color, float size) {
        resolve(font).drawWrappedText(text, x, y, maxWidth, color, size);
    }

    @Override
    public float getWrappedHeight(String font, String text, float maxWidth, float size) {
        return resolve(font).getWrappedHeight(text, maxWidth, size);
    }

    @Override
    public void drawGRS(String font, String text, float x, float y,
                        int color1, int color2, float size) {
        resolve(font).drawGRS(text, x, y, color1, color2, size);
    }

    @Override
    public void drawRightAlignFadeLeft(String font, String text, float rightX, float y,
                                       float fadeStartX, int color, float size) {
        resolve(font).drawRightAlignFadeLeft(text, rightX, y, fadeStartX, color, size);
    }
}
