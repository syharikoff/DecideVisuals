package ru.white.ui.fonts;

import ru.white.utils.render.font.Font;

public class LvFont {
    private final Font delegate;

    public LvFont(Font delegate) {
        this.delegate = delegate;
    }

    public void draw(String text, float x, float y, float size, int color) {
        delegate.draw(text, x, y, size, color);
    }

    public float width(String text, float size) {
        return delegate.getWidth(text, size);
    }

    public float msdfWidth(String text, float size) {
        return delegate.getWidth(text, size);
    }

    public void msdf(String text, float x, float y, float size, int color) {
        delegate.draw(text, x, y, size, color);
    }

    public void msdfFade(String text, float x, float y, float size, int color, float clipStartX, float clipEndX, float fadeWidth, float fadeIn, float fadeOut) {
        delegate.drawFadingText(text, x, y, clipEndX - clipStartX, color, size);
    }

    public void drawCentered(String text, float x, float y, float size, int color) {
        delegate.drawCentered(text, x, y, size, color);
    }

    public void drawCenterGradient(String text, float x, float y, float size, int c1, int c2) {
        delegate.drawCenterGradient(text, x, y, size, c1, c2);
    }

    public void drawGRS(String text, double x, double y, int c1, int c2, float size) {
        delegate.drawGradientB(text, (float) x, (float) y, c1, c2, size);
    }

    public float getHeight(float size) {
        return delegate.getHeight(size);
    }

    public void drawFadingText(String text, float x, float y, float maxWidth, int color, float size) {
        delegate.drawFadingText(text, x, y, maxWidth, color, size);
    }

    public void drawFadingTextReverse(String text, float x, float y, float maxWidth, int color, float size) {
        delegate.drawFadingTextReverse(text, x, y, maxWidth, color, size);
    }

    public void drawWrappedText(String text, float x, float y, float maxWidth, int color, float size) {
        delegate.drawWrappedText(text, x, y, maxWidth, color, size);
    }

    public float getWrappedHeight(String text, float maxWidth, float size) {
        return delegate.getWrappedHeight(text, maxWidth, size);
    }

    public String getName() {
        return delegate.getName();
    }
}