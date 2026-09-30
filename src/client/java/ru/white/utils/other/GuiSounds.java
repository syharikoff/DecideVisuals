package ru.white.utils.other;

/**
 * Звуковая палитра меню — целиком на ассетах из {@code assets/client/sound/gui}.
 * Каждый тип действия имеет свой семпл; питч используется точечно и слабо
 * (ползунок, чипсы, печать), чтобы повторяющиеся клики не звучали механически.
 */
public class GuiSounds {

    private static final String DIR = "gui/";

    private static long lastTick;
    private static long lastType;

    private static boolean pass(long last, long delay) {
        return System.currentTimeMillis() - last >= delay;
    }

    private static void play(String name, float volume) {
        SoundUtil.playSound_wav(DIR + name, volume);
    }

    private static void play(String name, float volume, float pitch) {
        SoundUtil.playSound_wav(DIR + name, volume, pitch);
    }

    // ——— открытие / закрытие меню ———

    public static void open() {
        play("gui_open", 0.45F);
    }

    public static void close() {
        play("gui_close", 0.45F);
    }

    /** Редактор рук поверх меню. */
    public static void editor() {
        play("gui_anime", 0.4F);
    }

    // ——— навигация ———

    /** Категория слева: тон едва заметно растёт сверху вниз по списку. */
    public static void category(int index, int total) {
        float t = total <= 1 ? 0 : (float) index / (total - 1);
        play("gui_category", 0.45F, 0.97F + 0.08F * t);
    }

    /** Смена темы — свой оттенок тона у каждой темы. */
    public static void theme(int index, int total) {
        float t = total <= 1 ? 0 : (float) index / (total - 1);
        play("gui_theme", 0.45F, 0.95F + 0.14F * t);
    }

    // ——— модули ———

    /** ПКМ по модулю — раскрыть/свернуть его настройки. */
    public static void expand(boolean opened) {
        play(opened ? "gui_module_open" : "gui_module_close", 0.45F);
    }

    // ——— сеттинги ———

    public static void toggle(boolean value) {
        play(value ? "gui_boolean_enable" : "gui_boolean_disable", 0.4F);
    }

    /** Чипсы режима. */
    public static void chip(int index, int total) {
        float t = total <= 1 ? 0 : (float) index / (total - 1);
        play("gui_mode_multi", 0.45F, 0.95F + 0.15F * t);
    }

    /** Чипсы мультибула — своя пара «вкл/выкл», отличная от обычного тумблера. */
    public static void chipMulti(boolean value) {
        play(value ? "gui_boolean2_enable" : "gui_boolean2_disable", 0.4F);
    }

    public static void button() {
        play("gui_open_button", 0.4F);
    }

    /** Захват ползунка. */
    public static void sliderGrab() {
        play("gui_click", 0.35F);
    }

    /** Тик на каждый шаг ползунка: тон едет за значением. */
    public static void sliderTick(float percent) {
        if (!pass(lastTick, 22)) return;
        lastTick = System.currentTimeMillis();
        play("gui_slider", 0.35F, 0.9F + 0.35F * Math.max(0, Math.min(1, percent)));
    }

    public static void sliderRelease() {
        play("gui_slider", 0.3F, 1.25F);
    }

    // ——— бинд ———

    public static void bindStart() {
        play("gui_binding", 0.45F);
    }

    public static void bindSet() {
        play("gui_bind", 0.4F);
    }

    public static void bindReset() {
        play("gui_clear", 0.4F);
    }

    // ——— текстовые поля и поиск ———

    public static void editStart() {
        play("gui_click", 0.4F);
    }

    public static void editCommit() {
        play("gui_bind", 0.35F);
    }

    public static void editCancel() {
        play("gui_clear", 0.35F);
    }

    /** Печать: лёгкий разброс тона, чтобы клавиши не звучали одинаково. */
    public static void type() {
        if (!pass(lastType, 25)) return;
        lastType = System.currentTimeMillis();
        play("gui_key_click", 0.35F, 1.0F + (float) (Math.random() * 0.12F - 0.06F));
    }

    public static void erase() {
        if (!pass(lastType, 25)) return;
        lastType = System.currentTimeMillis();
        play("gui_key_click", 0.3F, 0.88F);
    }

    public static void searchClear() {
        play("gui_clear", 0.4F);
    }

    // ——— цвет ———

    public static void picker(boolean opened) {
        play(opened ? "gui_multi_open" : "gui_multi_close", 0.4F);
    }

    /** Протяжка по полосе пикера — тон едет за позицией. */
    public static void colorTick(float percent) {
        if (!pass(lastTick, 28)) return;
        lastTick = System.currentTimeMillis();
        play("gui_slider", 0.3F, 0.9F + 0.35F * Math.max(0, Math.min(1, percent)));
    }

    // ——— прокрутка ———

    public static void scroll() {
        if (!pass(lastTick, 55)) return;
        lastTick = System.currentTimeMillis();
        play("gui_scroll", 0.35F);
    }
}
