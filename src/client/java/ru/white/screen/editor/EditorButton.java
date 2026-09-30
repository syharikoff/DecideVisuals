package ru.white.screen.editor;

import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.math.MathUtil;

/** Кнопка оверлей-редактора: ховер и вспышка нажатия живут внутри самой кнопки. */
public final class EditorButton {

    private final String label;
    private final boolean accent;
    private final Animation hover = new Animation();
    private final Animation press = new Animation();

    private float x;
    private float y;
    private float width;
    private float height;

    public EditorButton(String label, boolean accent) {
        this.label = label;
        this.accent = accent;
    }

    public String label() {
        return label;
    }

    public boolean accent() {
        return accent;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float hoverValue() {
        return hover.get();
    }

    public float pressValue() {
        return press.get();
    }

    public void set(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void update(float mouseX, float mouseY) {
        hover.update();
        hover.run(contains(mouseX, mouseY) ? 1F : 0F, 0.18F, Easings.QUAD_OUT, true);
        press.update();
    }

    public boolean contains(float mouseX, float mouseY) {
        return MathUtil.isHovered(mouseX, mouseY, x, y, width, height);
    }

    public void press() {
        press.set(1F);
        press.run(0F, 0.35F, Easings.QUAD_OUT);
    }
}
