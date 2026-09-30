package ru.white.screen.editor;

import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import ru.white.lang.Lang;
import ru.white.module.api.Module;
import ru.white.module.api.settings.Setting;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.ButtonSetting;
import ru.white.module.api.settings.impl.ColorSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.utils.animation.satoshi.Animation;
import ru.white.utils.animation.satoshi.Direction;
import ru.white.utils.animation.satoshi.EaseInOutQuad;
import ru.white.utils.colors.ColorUtil;
import ru.white.utils.math.MathUtil;
import ru.white.utils.render.RenderUtil;
import ru.white.utils.render.Scissor;
import ru.white.utils.render.font.Font;
import ru.white.utils.render.font.Fonts;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ru.white.screen.editor.EditorTheme.CHIP_H;
import static ru.white.screen.editor.EditorTheme.CHIP_STEP;
import static ru.white.screen.editor.EditorTheme.DELIMITER_H;
import static ru.white.screen.editor.EditorTheme.HEADER_H;
import static ru.white.screen.editor.EditorTheme.PANEL_PAD;
import static ru.white.screen.editor.EditorTheme.PANEL_W;
import static ru.white.screen.editor.EditorTheme.PICKER_H;
import static ru.white.screen.editor.EditorTheme.ROW_GAP;
import static ru.white.screen.editor.EditorTheme.ROW_H;
import static ru.white.screen.editor.EditorTheme.SLIDER_H;
import static ru.white.screen.editor.EditorTheme.TEXT;
import static ru.white.screen.editor.EditorTheme.VALUE_TEXT;

/**
 * Плавающая панель настроек модуля для оверлей-редакторов: тумблер модуля плюс все его
 * видимые сеттинги строками того же вида, что и в ClickGUI. Панель сама держит своё
 * состояние перетаскивания, поэтому редактору достаточно раздать ей события мыши.
 */
public final class SettingsPanel {

    private Module module;
    private String title = "";

    /** Позиция задаётся при первой раскладке, дальше ей управляет перетаскивание. */
    private float x;
    private float y;
    private boolean placed;

    private Rect bounds = Rect.EMPTY;
    private Rect headerBounds = Rect.EMPTY;
    private Rect enableBounds = Rect.EMPTY;

    private final List<PanelRow> rows = new ArrayList<>();
    private final Set<String> expandedModes = new HashSet<>();
    private final java.util.HashMap<String, Animation> expandAnims = new java.util.HashMap<>();

    private Animation expandAnim(String key) {
        return expandAnims.computeIfAbsent(key, k -> {
            Animation a = new EaseInOutQuad(300, 1);
            a.setDirection(Direction.BACKWARDS);
            return a;
        });
    }

    private float scroll;
    private float scrollTarget;
    private float maxScroll;

    /** Сколько места снизу экрана занято кнопками редактора. */
    private float bottomInset = 82F;
    private boolean showEnableRow = true;

    private boolean draggingSelf;
    private float dragOffsetX;
    private float dragOffsetY;

    private SliderSetting draggingSlider;
    private Rect draggingSliderBounds = Rect.EMPTY;
    private ColorSetting draggingColor;
    private int draggingColorBar;
    private Rect draggingColorBounds = Rect.EMPTY;

    public Module module() {
        return module;
    }

    public Rect bounds() {
        return bounds;
    }

    public void setBottomInset(float bottomInset) {
        this.bottomInset = bottomInset;
    }

    public void setShowEnableRow(boolean showEnableRow) {
        this.showEnableRow = showEnableRow;
    }

    public void reset() {
        scroll = 0F;
        scrollTarget = 0F;
        releaseDrag();
        closePickers();
    }

    /** Сброс запомненной позиции — панель снова встанет в место по умолчанию. */
    public void unplace() {
        placed = false;
    }

    public boolean isDragging() {
        return draggingSelf || draggingSlider != null || draggingColor != null;
    }

    public void releaseDrag() {
        draggingSelf = false;
        draggingSlider = null;
        draggingColor = null;
    }

    public void closePickers() {
        for (PanelRow row : rows) {
            if (row.setting instanceof ColorSetting colorSetting) colorSetting.pickerOpen = false;
        }
        draggingColor = null;
    }

    // ───────────────────────────── раскладка ─────────────────────────────

    /** Панель = тумблер модуля + все его видимые сеттинги, высоты строк как в меню. */
    public void layout(Module module, String title, float defaultX, float defaultY,
                       float screenW, float screenH, EditorWidgets widgets) {
        // скролл догоняет цель — колесо не дёргает список рывками
        scroll += (scrollTarget - scroll) * 0.2F;

        rows.clear();
        this.module = module;
        this.title = title;

        if (module == null) {
            bounds = Rect.EMPTY;
            enableBounds = Rect.EMPTY;
            headerBounds = Rect.EMPTY;
            return;
        }

        if (!placed) {
            x = defaultX;
            y = defaultY;
            placed = true;
        }

        // после смены разрешения перетащенная панель могла оказаться за краем
        x = MathUtil.clamp(x, 4F, Math.max(4F, screenW - PANEL_W - 4F));
        y = MathUtil.clamp(y, 4F, Math.max(4F, screenH - HEADER_H - 30F));

        float rowX = x + PANEL_PAD;
        float rowW = PANEL_W - PANEL_PAD * 2;
        float top = y + 5F + HEADER_H + 3F;

        headerBounds = new Rect(rowX, y + 5F, rowW, HEADER_H);

        float maxBottom = screenH - bottomInset;
        float viewH = Math.max(60F, maxBottom - top);

        float rowY = top - scroll;

        if (showEnableRow) {
            enableBounds = new Rect(rowX, rowY, rowW, ROW_H);
            rowY += ROW_H + ROW_GAP;
        } else {
            enableBounds = Rect.EMPTY;
        }

        for (Setting<?> setting : module.getSettings()) {
            // рисуем только то, для чего есть строка — иначе в панели повиснет пустой промежуток
            boolean supported = setting instanceof BooleanSetting
                    || setting instanceof SliderSetting
                    || setting instanceof ModeSetting
                    || setting instanceof ColorSetting
                    || setting instanceof ButtonSetting
                    || setting instanceof DelimiterSetting;
            if (!supported) continue;

            // строка не пропадает мгновенно: пока анимация не догорела, она занимает часть места
            Animation appear = widgets.anim(module.getName() + ":" + setting.getName() + ":vis");
            appear.setDirection(setting.getVisible().get() ? Direction.FORWARDS : Direction.BACKWARDS);

            float vis = appear.getOutput();
            if (vis <= 0.01F) continue;

            PanelRow row = new PanelRow(setting, setting.getName());
            row.vis = vis;

            float height = ROW_H;

            if (setting instanceof SliderSetting) {
                height = SLIDER_H;
            } else if (setting instanceof DelimiterSetting) {
                height = DELIMITER_H;
            } else if (setting instanceof ModeSetting mode) {
                boolean expanded = expandedModes.contains(mode.getName());
                Animation ea = expandAnim(module.getName() + ":" + mode.getName());
                ea.setDirection(expanded ? Direction.FORWARDS : Direction.BACKWARDS);
                float expandT = ea.getOutput();
                height = ROW_H + (2F + chipsHeight(mode.values, rowW - 12F) + 2F) * expandT;
            } else if (setting instanceof ColorSetting color && color.pickerOpen) {
                height = ROW_H + PICKER_H;
            }

            row.bounds = new Rect(rowX, rowY, rowW, height);

            if (setting instanceof ModeSetting mode) {
                Animation ea = expandAnim(module.getName() + ":" + mode.getName());
                ea.setDirection(expandedModes.contains(mode.getName()) ? Direction.FORWARDS : Direction.BACKWARDS);
                if (ea.getOutput() > 0.01F) {
                    layoutChips(row, mode, rowX + 6F, rowY + ROW_H + 2F, rowW - 12F);
                }
            }

            rows.add(row);
            rowY += (height + ROW_GAP) * vis;
        }

        float content = rowY + scroll - top;
        maxScroll = Math.max(0F, content - viewH);
        scrollTarget = MathUtil.clamp(scrollTarget, 0F, maxScroll);

        float panelH = 5F + HEADER_H + 3F + Math.min(content, viewH) + 5F;
        bounds = new Rect(x, y, PANEL_W, panelH);
    }

    private void layoutChips(PanelRow row, ModeSetting mode, float chipX, float chipY, float width) {
        row.chips.clear();

        Font font = Fonts.sf_regular;
        float px = 0F;
        float py = 0F;

        for (String value : mode.values) {
            float tw = font.getWidth(Lang.tr(value), VALUE_TEXT) + 7F;
            if (px + tw > width && px > 0F) {
                px = 0F;
                py += CHIP_STEP;
            }

            row.chips.add(new Rect(chipX + px, chipY + py, tw, CHIP_H));
            px += tw + 2.5F;
        }
    }

    private float chipsHeight(List<String> values, float width) {
        Font font = Fonts.sf_regular;
        float px = 0F;
        float py = 0F;

        for (String value : values) {
            float tw = font.getWidth(Lang.tr(value), VALUE_TEXT) + 7F;
            if (px + tw > width && px > 0F) {
                px = 0F;
                py += CHIP_STEP;
            }
            px += tw + 2.5F;
        }

        return py + CHIP_H;
    }

    // ───────────────────────────── отрисовка ─────────────────────────────

    public void draw(EditorWidgets widgets, float alpha) {
        if (module == null || bounds.width() <= 0F) return;

        Rect b = bounds;

        RenderUtil.Render2D.glow(b.x(), b.y(), b.width(), b.height() - 0.5F,
                ColorUtil.getColor(0, 0.15F * alpha), 8, 15, 1);

        RenderUtil.Blur.blur(b.x(), b.y(), b.width(), b.height(), alpha, 8,
                ColorUtil.multAlpha(ColorUtil.multDark(ColorUtil.background(), 0.6F), alpha));

        RenderUtil.Render2D.outline(b.x(), b.y(), b.width(), b.height(), 0.5F,
                ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.35F), 8);

        // шапка панели живёт на таком же фоне, что и строки настроек, и служит ручкой для переноса
        Rect header = headerBounds;

        widgets.drawRowBackground(header, widgets.rowHover(module.getName() + ":header", header), alpha);

        Fonts.category.draw("U", header.x() + 6F, header.y() + 5.2F, 6.5F,
                ColorUtil.multAlpha(ColorUtil.client(), alpha));
        Fonts.sf_regular.draw(title, header.x() + 17F, header.y() + 4.4F, 6.5F,
                ColorUtil.getColor(200, alpha));

        Scissor.enable(b.x(), b.y() + 5 + HEADER_H + 3, b.width(), b.height() - (5 + HEADER_H + 3) - 5, 2);

        // имя модуля уже в шапке панели — в строке тумблера дублировать его незачем
        if (showEnableRow) {
            widgets.drawToggleRow(enableBounds, Lang.tr("Вкл/Выкл"), module.isEnabled(),
                    module.getName() + ":enabled", alpha);
        }

        for (PanelRow row : rows) {
            drawRow(widgets, row, alpha);
        }

        Scissor.disable();
    }

    private void drawRow(EditorWidgets widgets, PanelRow row, float parentAlpha) {
        String key = module.getName() + ":" + row.label;
        Setting<?> setting = row.setting;
        Rect b = row.bounds;

        // строка проявляется вместе со своей анимацией видимости
        float alpha = parentAlpha * row.vis;
        if (alpha <= 0.01F) return;

        if (setting instanceof DelimiterSetting) {
            Font font = Fonts.sf_regular;
            String label = Lang.tr(row.label);
            float labelW = font.getWidth(label, VALUE_TEXT);

            font.draw(label, b.x() + 2F, b.y() + 4F, VALUE_TEXT,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * 0.85F));
            RenderUtil.Render2D.rect(b.x() + labelW + 6F, b.y() + 5.75F,
                    Math.max(0F, b.width() - labelW - 8F), 0.5F, ColorUtil.getColor(255, alpha * 0.12F), 0.25F);
            return;
        }

        if (setting instanceof BooleanSetting booleanSetting) {
            widgets.drawToggleRow(b, Lang.tr(row.label), booleanSetting.getValue(), key, alpha);
            return;
        }

        if (setting instanceof SliderSetting slider) {
            float hover = widgets.rowHover(key, b);

            widgets.drawRowBackground(b, hover, alpha);

            Font font = Fonts.sf_regular;
            font.draw(Lang.tr(row.label), b.x() + 6, b.y() + 3.2F, TEXT,
                    ColorUtil.getColor(255, alpha * (0.5F + 0.5F * hover)));

            String value = String.valueOf(Math.round(slider.getValue() * 100.0) / 100.0);
            font.draw(value, b.x() + b.width() - 6 - font.getWidth(value, VALUE_TEXT), b.y() + 3.5F, VALUE_TEXT,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.6F + 0.4F * hover)));

            float track = b.width() - 12;
            float percent = MathHelper.clamp(
                    (slider.getValue() - slider.min) / (slider.max - slider.min), 0F, 1F);
            float shown = widgets.smooth(key + ":pc", percent);

            RenderUtil.Render2D.rect(b.x() + 6, b.y() + 13F, track, 2.5F,
                    ColorUtil.getColor(0, alpha * 0.15F), 1.25F);
            RenderUtil.Render2D.rect(b.x() + 6, b.y() + 13F, track * shown, 2.5F,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.5F + 0.5F * hover)), 1.25F);
            RenderUtil.Render2D.rect(b.x() + 6 + track * shown - 2.5F, b.y() + 11.75F, 5, 5,
                    ColorUtil.getColor((int) (200 + 55 * hover), alpha), 5);
            RenderUtil.Render2D.rect(b.x() + 6 + track * shown - 1.75F, b.y() + 12.5F, 3.5F, 3.5F,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.7F + 0.3F * hover)), 5);
            return;
        }

        if (setting instanceof ModeSetting mode) {
            float hover = widgets.rowHover(key, b);
            boolean expanded = expandedModes.contains(mode.getName());
            Animation ea = expandAnim(module.getName() + ":" + mode.getName());
            ea.setDirection(expanded ? Direction.FORWARDS : Direction.BACKWARDS);
            float expandT = ea.getOutput();

            widgets.drawRowBackground(b, hover, alpha);

            Font font = Fonts.sf_regular;
            String current = Lang.tr(mode.getValue());

            font.drawFadingText(Lang.tr(row.label), b.x() + 6, b.y() + 3.2F,
                    b.width() - font.getWidth(current, VALUE_TEXT) - 16,
                    ColorUtil.getColor(255, alpha * (0.5F + 0.5F * hover)), TEXT);

            String arrow = expanded ? "\u25B2" : "\u25BC";
            font.draw(arrow + " " + current,
                    b.x() + b.width() - 6 - font.getWidth(arrow + " " + current, VALUE_TEXT),
                    b.y() + 3.5F, VALUE_TEXT,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.6F + 0.4F * hover)));

            if (expandT > 0.01F) {
                for (int i = 0; i < row.chips.size(); i++) {
                    Rect chip = row.chips.get(i);
                    String value = mode.values.get(i);

                    Animation anim = widgets.anim(key + ":chip:" + value);
                    anim.setDirection(value.equals(mode.getValue()) ? Direction.FORWARDS : Direction.BACKWARDS);
                    float selected = anim.getOutput();

                    RenderUtil.Render2D.rect(chip.x(), chip.y(), chip.width(), chip.height(), ColorUtil.overCol(
                            ColorUtil.getColor(0, 0.25F * alpha * expandT),
                            ColorUtil.replAlpha(ColorUtil.client(), alpha * (0.5F + 0.5F * hover) * expandT), selected), 2.5F);

                    font.drawCentered(Lang.tr(value), chip.centerX(), chip.y() + 1.4F, VALUE_TEXT,
                            ColorUtil.getColor(255, alpha * (0.35F + 0.65F * selected) * (0.7F + 0.3F * hover) * expandT));
                }
            }
            return;
        }

        if (setting instanceof ColorSetting color) {
            float hover = widgets.rowHover(key, b);

            widgets.drawRowBackground(b, hover, alpha);

            Font font = Fonts.sf_regular;
            font.drawFadingText(Lang.tr(row.label), b.x() + 6, b.y() + 3.2F, b.width() - 30,
                    ColorUtil.getColor(255, alpha * (0.5F + 0.5F * hover)), TEXT);

            int argb = color.getValue();
            int r = (int) widgets.smooth(key + ":r", (argb >> 16) & 0xFF);
            int g = (int) widgets.smooth(key + ":g", (argb >> 8) & 0xFF);
            int bl = (int) widgets.smooth(key + ":b", argb & 0xFF);

            float px = b.x() + b.width() - 6 - 7;
            float py = b.y() + 3;

            RenderUtil.Render2D.rect(px, py, 7, 7, ColorUtil.getColor(r, g, bl, alpha), 7);
            RenderUtil.Render2D.outline(px - 0.75F, py - 0.75F, 8.5F, 8.5F, 0.25F,
                    ColorUtil.getColor(r, g, bl, alpha * (0.4F + 0.6F * hover)), 7);
            RenderUtil.Render2D.glow(px, py, 7, 7, ColorUtil.getColor(r, g, bl, 0.1F * alpha), 7, 6, 1);

            if (!color.pickerOpen) return;

            float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);
            float hue = widgets.smooth(key + ":h", hsb[0]);
            float sat = widgets.smooth(key + ":s", hsb[1]);
            float bri = widgets.smooth(key + ":v", hsb[2]);
            float[] smoothHsb = {hue, sat, bri};

            float barX = b.x() + 6;
            float barW = b.width() - 12;
            int segments = 16;
            float segment = barW / segments;

            for (int bar = 0; bar < 3; bar++) {
                float barY = b.y() + ROW_H + 2F + bar * 10F;

                for (int i = 0; i < segments; i++) {
                    float t0 = (float) i / segments;
                    float t1 = (float) (i + 1) / segments;

                    int c0 = switch (bar) {
                        case 0 -> Color.HSBtoRGB(t0, 1F, 1F);
                        case 1 -> Color.HSBtoRGB(hue, t0, bri);
                        default -> Color.HSBtoRGB(hue, sat, t0);
                    };
                    int c1 = switch (bar) {
                        case 0 -> Color.HSBtoRGB(t1, 1F, 1F);
                        case 1 -> Color.HSBtoRGB(hue, t1, bri);
                        default -> Color.HSBtoRGB(hue, sat, t1);
                    };

                    int a0 = ColorUtil.replAlpha(c0, alpha);
                    int a1 = ColorUtil.replAlpha(c1, alpha);

                    RenderUtil.Render2D.gradientRect(barX + i * segment, barY,
                            segment + (i == segments - 1 ? 0 : 0.5F), 3.5F,
                            new int[]{a0, a1, a1, a0},
                            i == 0 ? 2 : 0, i == segments - 1 ? 2 : 0,
                            i == segments - 1 ? 2 : 0, i == 0 ? 2 : 0);
                }

                float knobX = barX + barW * smoothHsb[bar];
                int knobColor = bar == 0
                        ? Color.HSBtoRGB(hue, 1F, 1F)
                        : Color.HSBtoRGB(hue, sat, bri);

                RenderUtil.Render2D.rect(knobX - 2.5F, barY - 0.75F, 5, 5, ColorUtil.getColor(255, alpha), 5);
                RenderUtil.Render2D.rect(knobX - 1.75F, barY, 3.5F, 3.5F, ColorUtil.replAlpha(knobColor, alpha), 5);
            }
            return;
        }

        if (setting instanceof ButtonSetting buttonSetting) {
            float hover = widgets.rowHover(key, b);

            buttonSetting.pressAnim.update();
            float press = buttonSetting.pressAnim.get();
            float accent = Math.max(hover, press);

            RenderUtil.Render2D.glow(b.x(), b.y(), b.width(), b.height(),
                    ColorUtil.getColor(0, 0.06F * alpha), 5, 7, 1);
            RenderUtil.Render2D.rect(b.x(), b.y(), b.width(), b.height(), ColorUtil.overCol(
                    ColorUtil.getColor(40, 0.15F * alpha),
                    ColorUtil.replAlpha(ColorUtil.client(), 0.25F * alpha), accent), 5);
            RenderUtil.Render2D.outline(b.x(), b.y(), b.width(), b.height(), 0.5F,
                    ColorUtil.replAlpha(ColorUtil.client(), alpha * accent), 4);

            Fonts.sf_regular.drawCentered(Lang.tr(row.label), b.centerX(), b.y() + 3.2F, TEXT,
                    ColorUtil.getColor(255, alpha * (0.5F + 0.5F * hover)));
        }
    }

    // ───────────────────────────── ввод ─────────────────────────────

    public boolean handleClick(float mouseX, float mouseY, int button) {
        if (module == null || !bounds.contains(mouseX, mouseY)) return false;

        if (headerBounds.contains(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                draggingSelf = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
            return true;
        }

        if (showEnableRow && enableBounds.contains(mouseX, mouseY)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                module.setEnabled(!module.isEnabled(), false);
            }
            return true;
        }

        for (PanelRow row : rows) {
            if (!row.bounds.contains(mouseX, mouseY)) continue;

            Setting<?> setting = row.setting;

            if (setting instanceof BooleanSetting booleanSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) booleanSetting.set(!booleanSetting.getValue());
            } else if (setting instanceof SliderSetting slider) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    draggingSlider = slider;
                    draggingSliderBounds = row.bounds;
                    applySlider(slider, row.bounds, mouseX);
                }
            } else if (setting instanceof ModeSetting mode) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (mouseY <= row.bounds.y() + ROW_H) {
                        // Click on header — toggle expand/collapse
                        String key = mode.getName();
                        if (expandedModes.contains(key)) {
                            expandedModes.remove(key);
                        } else {
                            expandedModes.add(key);
                        }
                    } else {
                        // Click on chip area
                        for (int i = 0; i < row.chips.size(); i++) {
                            if (row.chips.get(i).contains(mouseX, mouseY)) {
                                mode.set(mode.values.get(i));
                                break;
                            }
                        }
                    }
                }
            } else if (setting instanceof ColorSetting color) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    if (mouseY <= row.bounds.y() + ROW_H) {
                        color.pickerOpen = !color.pickerOpen;
                    } else if (color.pickerOpen) {
                        for (int bar = 0; bar < 3; bar++) {
                            float barY = row.bounds.y() + ROW_H + 2F + bar * 10F;
                            if (MathUtil.isHovered(mouseX, mouseY, row.bounds.x() + 6F, barY - 2.5F,
                                    row.bounds.width() - 12F, 9F)) {
                                draggingColor = color;
                                draggingColorBar = bar;
                                draggingColorBounds = row.bounds;
                                applyColorDrag(mouseX);
                            }
                        }
                    }
                }
            } else if (setting instanceof ButtonSetting buttonSetting) {
                if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) buttonSetting.press();
            }

            return true;
        }

        return true;
    }

    public boolean handleScroll(float mouseX, float mouseY, double verticalAmount) {
        if (module == null || !bounds.contains(mouseX, mouseY)) return false;
        scrollTarget = MathUtil.clamp((float) (scrollTarget - verticalAmount * 18F), 0F, maxScroll);
        return true;
    }

    /** Возвращает true, если движение курсора было поглощено перетаскиванием внутри панели. */
    public boolean rawMouseMoved(float mouseX, float mouseY) {
        if (draggingSelf) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
            return true;
        }
        if (draggingSlider != null) {
            applySlider(draggingSlider, draggingSliderBounds, mouseX);
            return true;
        }
        if (draggingColor != null) {
            applyColorDrag(mouseX);
            return true;
        }
        return false;
    }

    private void applySlider(SliderSetting setting, Rect sliderBounds, float mouseX) {
        float trackX = sliderBounds.x() + 6F;
        float trackW = Math.max(1F, sliderBounds.width() - 12F);
        float progress = MathHelper.clamp((mouseX - trackX) / trackW, 0F, 1F);
        setting.set(MathHelper.clamp(
                MathUtil.round(setting.min + (setting.max - setting.min) * progress, setting.increment),
                setting.min, setting.max));
    }

    private void applyColorDrag(float mouseX) {
        if (draggingColor == null) return;

        int argb = draggingColor.getValue();
        float[] hsb = Color.RGBtoHSB((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, null);

        float t = MathUtil.clamp((mouseX - (draggingColorBounds.x() + 6F))
                / Math.max(1F, draggingColorBounds.width() - 12F), 0F, 1F);

        float[] next = {hsb[0], hsb[1], hsb[2]};
        next[draggingColorBar] = t;

        int rgb = Color.HSBtoRGB(next[0], next[1], next[2]);
        draggingColor.set((argb & 0xFF000000) | (rgb & 0x00FFFFFF));
    }

    private static final class PanelRow {
        private final Setting<?> setting;
        private final String label;
        private final List<Rect> chips = new ArrayList<>();
        private Rect bounds = Rect.EMPTY;
        private float vis = 1F;

        private PanelRow(Setting<?> setting, String label) {
            this.setting = setting;
            this.label = label;
        }
    }
}
