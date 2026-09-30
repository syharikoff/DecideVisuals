package ru.white.theme;

/**
 * A single color theme. Holds 6 customizable colors.
 * Index order matches {@link ThemeColor} getters.
 */
public class Theme {

    public static final int ACCENT      = 0;
    public static final int BACKGROUND  = 1;
    public static final int OUTLINE     = 2;
    public static final int TEXT        = 3;
    public static final int TEXT2       = 4;
    public static final int ELEMENT     = 5;
    public static final int SEPARATOR   = 6;
    public static final int LIGHT_BG    = 7;

    public static final int COLOR_COUNT = 8;

    public static final String[] NAMES = {
            "Цвет визуалов", "Цвет фона", "Обводка", "Светлый текст", "Тёмный текст", "Цвет худа",
            "Цвет разделителей", "Цвет светлого фона"
    };

    // ── слайдеры темы (как SliderSetting: значение/мин/макс/шаг) ──────────────
    public static final int OPACITY = 0;
    public static final int BLUR    = 1;

    public static final String[] SLIDER_NAMES = { "Прозрачность", "Сила размытия" };
    public static final float[]  SLIDER_MIN   = { 0.1F, 0.0F };
    public static final float[]  SLIDER_MAX   = { 1.0F, 6.0F };
    public static final float[]  SLIDER_DEF   = { 1.0F, 0.0F };
    /** шаг слайдера, 0 — плавный */
    public static final float[]  SLIDER_STEP  = { 0.0F, 1.0F };

    // ── булевы переключатели темы ────────────────────────────────────────────
    public static final int SHADOW     = 0;
    public static final int DISTORTION = 1;

    public static final String[]  BOOL_NAMES = { "Эффект тени", "Эффект искажения" };
    public static final boolean[] BOOL_DEF   = { true, false };

    public String name;
    public final int[] colors = new int[COLOR_COUNT];
    /** значения слайдеров темы, индексы — {@link #OPACITY}, {@link #BLUR} */
    public final float[] sliders = new float[2];
    /** булевы переключатели темы, индексы — {@link #SHADOW}, {@link #DISTORTION} */
    public final boolean[] booleans = new boolean[BOOL_NAMES.length];
    /** встроенная тема — её нельзя редактировать, переименовывать или удалять */
    public boolean builtin = false;

    public static int rgb(int r, int g, int b) {
        return 0xFF000000 | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public Theme(String name) {
        this.name = name;
        // по умолчанию всё белое, кроме фона (тёмный, как цвет ректов)
        colors[ACCENT]     = 0xFFFFFFFF;
        colors[BACKGROUND] = 0xFF0E0E12;
        colors[OUTLINE]    = 0xFFFFFFFF;
        colors[TEXT]       = 0xFFFFFFFF;
        colors[TEXT2]      = 0xFFFFFFFF;
        colors[ELEMENT]    = 0xFFFFFFFF;
        colors[SEPARATOR]  = 0xFFFFFFFF;
        colors[LIGHT_BG]   = 0xFF1A1A20;
        // значения слайдеров по умолчанию
        System.arraycopy(SLIDER_DEF, 0, sliders, 0, sliders.length);
        // значения переключателей по умолчанию
        System.arraycopy(BOOL_DEF, 0, booleans, 0, booleans.length);
    }

    public int get(int index) {
        return colors[index];
    }

    public void set(int index, int argb) {
        colors[index] = argb;
    }

    /** Значение слайдера, зажатое в его диапазон [min..max]. */
    public float getSlider(int index) {
        return clampSlider(index, sliders[index]);
    }

    public void setSlider(int index, float value) {
        sliders[index] = clampSlider(index, value);
    }

    private static float clampSlider(int index, float value) {
        float step = SLIDER_STEP[index];
        if (step > 0) value = Math.round(value / step) * step;
        if (value < SLIDER_MIN[index]) return SLIDER_MIN[index];
        if (value > SLIDER_MAX[index]) return SLIDER_MAX[index];
        return value;
    }

    public boolean getBool(int index) {
        return booleans[index];
    }

    public void setBool(int index, boolean value) {
        booleans[index] = value;
    }

    public Theme copy(String newName) {
        Theme t = new Theme(newName);
        System.arraycopy(this.colors, 0, t.colors, 0, COLOR_COUNT);
        System.arraycopy(this.sliders, 0, t.sliders, 0, this.sliders.length);
        System.arraycopy(this.booleans, 0, t.booleans, 0, this.booleans.length);
        return t;
    }
}
