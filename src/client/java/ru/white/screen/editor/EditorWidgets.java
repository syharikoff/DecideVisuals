package ru.white.screen.editor;

import ru.white.lang.Lang;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.font.Fonts;

import java.util.HashMap;
import java.util.Map;

import static ru.white.screen.editor.EditorTheme.TEXT;

/**
 * Общие примитивы оверлей-редакторов: строки, тумблеры, кнопки и кэш анимаций к ним.
 * Каждый редактор держит свой экземпляр, чтобы ховеры разных экранов не смешивались.
 */
public final class EditorWidgets {

    /** Ховеры и переключатели строк — по ключу «модуль:сеттинг». */
    private final Map<String, Animation> anims = new HashMap<>();
    private final Map<String, float[]> smoothVals = new HashMap<>();

    private float mouseX;
    private float mouseY;

    public void setMouse(float mouseX, float mouseY) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    public float mouseX() {
        return mouseX;
    }

    public float mouseY() {
        return mouseY;
    }

    public Animation anim(String key) {
        return anims.computeIfAbsent(key, k -> new EaseInOutQuad(300, 1));
    }

    public float rowHover(String key, Rect bounds) {
        Animation animation = anim(key + ":hover");
        animation.setDirection(bounds.contains(mouseX, mouseY) ? Direction.FORWARDS : Direction.BACKWARDS);
        return animation.getOutput();
    }

    /** Плавная интерполяция произвольного значения — как smooth() в меню. */
    public float smooth(String key, float target) {
        float[] value = smoothVals.computeIfAbsent(key, k -> new float[]{target});
        value[0] += (target - value[0]) * 0.2F;
        return value[0];
    }

    public void drawRowBackground(Rect b, float hover, float alpha) {
        RenderUtil.Render2D.glow(b.x(), b.y(), b.width(), b.height(), ColorUtil.getColor(0, 0.06F * alpha), 4, 6, 1);
        RenderUtil.Render2D.rect(b.x(), b.y(), b.width(), b.height(), ColorUtil.getColor(40, 0.15F * alpha), 4);
        RenderUtil.Render2D.outline(b.x(), b.y(), b.width(), b.height(), 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(), alpha * hover), 4);
    }

    /** Строка-переключатель: тумблер справа, как у BooleanSetting в меню. */
    public void drawToggleRow(Rect b, String label, boolean value, String key, float alpha) {
        if (b.width() <= 0F || alpha <= 0.01F) return;

        float hover = rowHover(key, b);

        drawRowBackground(b, hover, alpha);

        Fonts.sf_regular.draw(label, b.x() + 6, b.y() + 3.2F, TEXT,
                ColorUtil.getColor(255, alpha * (0.5F + 0.5F * hover)));

        Animation state = anim(key + ":on");
        state.setDirection(value ? Direction.FORWARDS : Direction.BACKWARDS);
        float on = state.getOutput();

        RenderUtil.Render2D.rect(b.x() + b.width() - 6 - 11, b.y() + 3.5F, 11, 6, ColorUtil.overCol(
                ColorUtil.getColor(0, 0.2F * alpha),
                ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.5F + 0.5F * hover)), on), 3F);

        RenderUtil.Render2D.rect(b.x() + b.width() - 6 - 11 + 1F + 4.5F * on, b.y() + 4.5F, 4, 4,
                ColorUtil.getColor(255, alpha * (0.2F + 0.4F * on + 0.4F * hover)), 2F);
    }

    public void drawButton(EditorButton button, float alpha) {
        float hover = button.hoverValue();
        float press = button.pressValue();
        float accent = Math.max(hover, press);

        RenderUtil.Render2D.glow(button.x(), button.y(), button.width(), button.height(),
                ColorUtil.getColor(0, 0.15F * alpha), 5, 10, 1);

        RenderUtil.Blur.blur(button.x(), button.y(), button.width(), button.height(), alpha, 5,
                ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), alpha));

        if (button.accent()) {
            RenderUtil.Render2D.rect(button.x(), button.y(), button.width(), button.height(),
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.2F + 0.2F * accent)), 5);
        }

        RenderUtil.Render2D.outline(button.x(), button.y(), button.width(), button.height(), 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(),
                        alpha * (button.accent() ? 0.5F + 0.5F * accent : accent)), 5);

        Fonts.sf_regular.drawCentered(Lang.tr(button.label()), button.x() + button.width() / 2F,
                button.y() + button.height() / 2F - 3.8F, 6.5F,
                ColorUtil.getColor(255, alpha * (0.6F + 0.4F * accent)));
    }
}
