package ru.white.ui.compat;

/**
 * Абстрактный API для акцентных градиентов и иконок.
 */
public interface AccentAPI {

    void msdfIcon(String fontName, String glyph, float x, float y, float size, float alpha);
    void msdfIcon(String fontName, String glyph, float x, float y, float size, float alpha, float position);
    void fillHorizontal(float x, float y, float w, float h, float radii, float alphaInt);
    void fillVertical(float x, float y, float w, float h, float radii, float alphaInt);
    void fillHorizontalLoop(float x, float y, float w, float h, float radii, float alphaInt);
    void overlayTrackShade(float x, float y, float w, float h, float radii, float alphaInt);
    void overlayKnobFade(float left, float right, float y, float h, float radii, float alphaInt);
    void fillKnob(float x, float y, float w, float h, float radii, float alphaInt);
    float categoryListIndexT(String categoryName);
    boolean usesDual();
}
