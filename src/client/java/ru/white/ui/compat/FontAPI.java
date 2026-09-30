package ru.white.ui.compat;

/**
 * Абстрактный API шрифтов.
 * Поддерживает MSDF-шрифты с shimmer/wave/fade эффектами.
 */
public interface FontAPI {

    // ── Базовые ──────────────────────────────────────────────────────
    void draw(String font, String text, float x, float y, float size, int color);
    void drawCentered(String font, String text, float x, float y, float size, int color);
    float width(String font, String text, float size);
    float height(String font, float size);

    // ── MSDF ─────────────────────────────────────────────────────────
    void msdf(String font, String text, float x, float y, float size, int color);
    float msdfWidth(String font, String text, float size);

    // ── MSDF Fade ────────────────────────────────────────────────────
    void msdfFade(String font, String text, float x, float y, float size, int color,
                 float clipStartX, float clipEndX, float fadeWidth,
                 float fadeIn, float fadeOut);

    // ── Shimmer ──────────────────────────────────────────────────────
    void shimmer(String font, String text, float x, float y, float size,
                 float offset, float width, float strength, float alpha);

    // ── Wave ─────────────────────────────────────────────────────────
    void wave(String font, String text, float x, float y, float size, int color, float amp);

    // ── Fading ───────────────────────────────────────────────────────
    void drawFadingText(String font, String text, float x, float y,
                        float maxWidth, int color, float size);
    void drawFadingTextReverse(String font, String text, float x, float y,
                               float maxWidth, int color, float size);

    // ── Wrapped ──────────────────────────────────────────────────────
    void drawWrappedText(String font, String text, float x, float y,
                         float maxWidth, int color, float size);
    float getWrappedHeight(String font, String text, float maxWidth, float size);

    // ── GRS (gradient per-character) ─────────────────────────────────
    void drawGRS(String font, String text, float x, float y,
                 int color1, int color2, float size);

    // ── Right-align fade ─────────────────────────────────────────────
    void drawRightAlignFadeLeft(String font, String text, float rightX, float y,
                                float fadeStartX, int color, float size);
}
