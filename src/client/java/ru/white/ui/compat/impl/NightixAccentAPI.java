package ru.white.ui.compat.impl;

import ru.white.ui.compat.AccentAPI;
import ru.white.ui.theme.AccentGradient;

public final class NightixAccentAPI implements AccentAPI {

    @Override
    public void msdfIcon(String fontName, String glyph, float x, float y, float size, float alpha) {
        AccentGradient.msdfIcon(fontName, glyph, x, y, size, alpha);
    }

    @Override
    public void msdfIcon(String fontName, String glyph, float x, float y, float size, float alpha, float position) {
        AccentGradient.msdfIcon(fontName, glyph, x, y, size, alpha, position);
    }

    @Override
    public void fillHorizontal(float x, float y, float w, float h, float radii, float alphaInt) {
        AccentGradient.fillHorizontal(x, y, w, h, radii, alphaInt);
    }

    @Override
    public void fillVertical(float x, float y, float w, float h, float radii, float alphaInt) {
        AccentGradient.fillVertical(x, y, w, h, radii, alphaInt);
    }

    @Override
    public void fillHorizontalLoop(float x, float y, float w, float h, float radii, float alphaInt) {
        AccentGradient.fillHorizontalLoop(x, y, w, h, radii, alphaInt);
    }

    @Override
    public void overlayTrackShade(float x, float y, float w, float h, float radii, float alphaInt) {
        AccentGradient.overlayTrackShade(x, y, w, h, radii, alphaInt);
    }

    @Override
    public void overlayKnobFade(float left, float right, float y, float h, float radii, float alphaInt) {
        AccentGradient.overlayKnobFade(left, right, y, h, radii, alphaInt);
    }

    @Override
    public void fillKnob(float x, float y, float w, float h, float radii, float alphaInt) {
        AccentGradient.fillKnob(x, y, w, h, radii, alphaInt);
    }

    @Override
    public float categoryListIndexT(String categoryName) {
        return AccentGradient.categoryListIndexT(categoryName);
    }

    @Override
    public boolean usesDual() {
        return AccentGradient.usesDual();
    }
}
