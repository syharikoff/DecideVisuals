package ru.white.screen.editor;

import ru.white.utils.math.MathUtil;

/** Прямоугольник в координатах оверлея (фиксированный GUI scale 2, как в Menu). */
public record Rect(float x, float y, float width, float height) {

    public static final Rect EMPTY = new Rect(0F, 0F, 0F, 0F);

    public boolean contains(float mouseX, float mouseY) {
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    public float centerX() {
        return x + width / 2F;
    }

    public float centerY() {
        return y + height / 2F;
    }

    public float distanceSquaredToCenter(float mouseX, float mouseY) {
        float dx = mouseX - centerX();
        float dy = mouseY - centerY();
        return dx * dx + dy * dy;
    }
}
