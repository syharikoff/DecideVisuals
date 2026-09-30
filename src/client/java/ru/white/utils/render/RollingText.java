package ru.white.utils.render;

import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.font.Font;

/**
 * Строка с прокруткой: изменившийся символ уезжает вверх, новый приезжает снизу.
 * Одна инстанция — одна строка (таймер, счётчик и т.п.).
 */
public class RollingText {

    private final float slide;

    private String value = "";
    private String prev = "";

    private final Animation animation = new Animation();

    public RollingText() {
        this(4F);
    }

    public RollingText(float slide) {
        this.slide = slide;
    }

    /** Меняет значение и запускает прокрутку, если оно отличается от текущего. */
    public void set(String next) {
        if (next == null || next.equals(value)) return;

        prev = value;
        value = next;

        animation.set(0);
        animation.run(1, 0.18, Easings.QUAD_OUT);
    }

    public String get() {
        return value;
    }

    public float width(Font font, float size) {
        return font.getWidth(value, size);
    }

    public void draw(Font font, float x, float y, float size, int color) {
        animation.update();

        float t = animation.get();

        float cx = x;

        for (int i = 0; i < value.length(); i++) {
            String ch = value.substring(i, i + 1);

            // символы сопоставляются с конца строки, чтобы «1:09» → «1:10» двигало только младшие разряды
            int j = prev.length() - (value.length() - i);
            String old = j >= 0 && j < prev.length() ? prev.substring(j, j + 1) : null;

            if (t >= 1F || ch.equals(old)) {
                font.draw(ch, cx, y, size, color);
            } else {
                font.draw(ch, cx, y + slide * (1 - t), size, ColorUtil.multAlpha(color, t));

                if (old != null) font.draw(old, cx, y - slide * t, size, ColorUtil.multAlpha(color, 1 - t));
            }

            cx += font.getWidth(ch, size);
        }
    }
}
