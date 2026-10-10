package ru.decide.module.impl.render;

import java.awt.Color;
import ru.decide.manager.event_impl.EventRender3D;
import ru.decide.manager.event_impl.EventTick;
import ru.decide.manager.event_impl.WorldLoadEvent;
import ru.decide.manager.events.orbit.EventHandler;
import ru.decide.module.api.Category;
import ru.decide.module.api.Module;
import ru.decide.module.api.ModuleInfo;
import ru.decide.module.api.settings.impl.BooleanSetting;
import ru.decide.module.api.settings.impl.DelimiterSetting;
import ru.decide.module.api.settings.impl.ModeSetting;
import ru.decide.module.api.settings.impl.SliderSetting;
import ru.decide.module.impl.render.lyrics.LyricParticles;
import ru.decide.theme.ThemeColor;
import ru.decide.utils.colors.ColorUtil;
import ru.decide.utils.media.MediaLog;
import ru.decide.utils.media.MediaPlayer;
import ru.decide.utils.animation.Animation;
import ru.decide.utils.animation.Easings;
import ru.decide.utils.render.lyrics.LyricText3D;

/**
 * Строки играющей песни висят в мире перед игроком. Порт «Lyrics Text» из Kimiko.
 *
 * Текст берётся с lrclib.net по названию текущего трека из системных медиа-сессий
 * Windows, тайминги слов разогревают глифы, а реальный вокал (наш
 * {@code DecideVocal.dll}) подмешивается в яркость свечения.
 *
 * Анимации, цвет и часть шрифтов добавлены из «Kinetic Lyrics».
 */
@ModuleInfo(
        name = "Lyrics Text",
        desc = "Строки играющей песни висят в мире перед вами",
        category = Category.VISUALS
)
public class LyricsText extends Module {

    private static final String EXIT_FADE = "Затухание";
    private static final String EXIT_FALL = "Распад";
    private static final String EXIT_SMOOTH = "Плавный";
    private static final String EXIT_TYPEWRITER = "Печатная машинка";
    private static final String EXIT_SCALE = "Масштабирование";
    private static final String EXIT_SLIDE = "Скольжение";

    private static final String ANIM_PROGRESSIVE = "Постепенно";
    private static final String ANIM_SMOOTH      = "Плавный";
    private static final String ANIM_TYPEWRITER  = "Печатная машинка";
    private static final String ANIM_SCALE       = "Масштабирование";
    private static final String ANIM_SLIDE       = "Скольжение";
    private static final String ANIM_FADE        = "Мягкое появление";

    private static final String COLOR_THEME     = "Тема";
    private static final String COLOR_GRADIENT  = "Градиент";
    private static final String COLOR_WHITE     = "Белый";

    /** Шрифты, которые были в Kimiko. Manasco — по умолчанию, как и там. */
    private static final String FONT_MANASCO = "Manasco";
    private static final String FONT_SF_PRO  = "SF Pro";
    private static final String FONT_BOLD    = "Bold";
    private static final String FONT_MONTSERRAT = "Montserrat";

    /** Имя процесса, для которого уже запущен детектор вокала. */
    private String vocalProcess;
    private boolean vocalRunning;

    /** Плавное появление/гашение текста вместо резкого включения. */
    private final Animation fade = new Animation();

    private final LyricParticles lyrics = new LyricParticles();

    public DelimiterSetting textSeparator = new DelimiterSetting(this, "Текст");

    public ModeSetting mode = new ModeSetting(this, "Показывать",
            LyricParticles.MODE_LINES, LyricParticles.MODE_WORDS);

    public ModeSetting layout = new ModeSetting(this, "Раскладка",
            LyricParticles.LAYOUT_ARC, LyricParticles.LAYOUT_SCATTER, LyricParticles.LAYOUT_CIRCLE)
            .setVisible(() -> mode.is(LyricParticles.MODE_WORDS));

    public ModeSetting font = new ModeSetting(this, "Шрифт", FONT_MANASCO, FONT_MANASCO, FONT_SF_PRO,
            FONT_BOLD, FONT_MONTSERRAT);

    public DelimiterSetting animationsSeparator = new DelimiterSetting(this, "Анимации");

    public ModeSetting intro = new ModeSetting(this, "Появление", ANIM_PROGRESSIVE,
            ANIM_PROGRESSIVE, ANIM_SMOOTH, ANIM_TYPEWRITER, ANIM_SCALE, ANIM_SLIDE, ANIM_FADE);

    /** Уход идёт сразу под появлением: исчезновение — те же приёмы, но в обратную сторону. */
    public ModeSetting exit = new ModeSetting(this, "Уход", EXIT_FADE,
            EXIT_FADE, EXIT_FALL, EXIT_SMOOTH, EXIT_TYPEWRITER, EXIT_SCALE, EXIT_SLIDE);

    public ModeSetting colorMode = new ModeSetting(this, "Цвет", COLOR_THEME,
            COLOR_THEME, COLOR_GRADIENT, COLOR_WHITE);

    public SliderSetting size = new SliderSetting(this, "Размер строки", 0.3f, 0.05f, 2.0f, 0.01f);

    public SliderSetting opacity = new SliderSetting(this, "Прозрачность", 1.0f, 0.1f, 1.0f, 0.05f);

    public BooleanSetting throughWalls = new BooleanSetting(this, "Сквозь стены", false);

    public DelimiterSetting styleSeparator = new DelimiterSetting(this, "Кастомизация");

    public SliderSetting glow = new SliderSetting(this, "Свечение", 1.0f, 0.0f, 3.0f, 0.05f);

    public DelimiterSetting placementSeparator = new DelimiterSetting(this, "Размещение");

    public SliderSetting radius = new SliderSetting(this, "Радиус", 3.5f, 1.0f, 16.0f, 0.1f);

    public SliderSetting height = new SliderSetting(this, "Высота", 1.8f, -2.0f, 8.0f, 0.1f);

    public SliderSetting limit = new SliderSetting(this, "Кусков", 3.0f, 1.0f, 8.0f, 1.0f);

    public SliderSetting sync = new SliderSetting(this, "Смещение", 0.0f, -1500.0f, 1500.0f, 10.0f);

    @Override
    protected void onEnable() {
        lyrics.reset();
        fade.set(0.0D);
        fade.run(1.0D, 0.8D, Easings.SINE_OUT, true);
    }

    @Override
    protected void onDisable() {
        lyrics.reset();
        LyricText3D.invalidate();
        MediaPlayer.stopVocalDetect();
        vocalProcess = null;
        vocalRunning = false;
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        lyrics.reset();
    }

    @EventHandler
    public void onTick(EventTick event) {
        MediaPlayer.tick();
        syncVocalDetect();
        fade.update();
    }

    @EventHandler
    public void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.world == null || mc.gameRenderer == null) {
            return;
        }
        if (fade.get() <= 0.0f) {
            return;
        }
        lyrics.render(event, mc, options());
    }

    /**
     * Держим детектор вокала запущенным для процесса, который сейчас играет музыку.
     * Сессия отдаёт не exe, а id приложения, поэтому имя процесса достаётся отдельно.
     */
    private void syncVocalDetect() {
        if (!MediaPlayer.isPlaying()) {
            return;
        }
        String process = MediaPlayer.vocalProcess();
        if (process == null || process.isEmpty()) {
            return;
        }
        if (process.equals(vocalProcess) && vocalRunning) {
            return;
        }
        vocalRunning = MediaPlayer.startVocalDetect(process);
        vocalProcess = process;
        MediaLog.note("vocal", "detect for '" + process + "' -> " + vocalRunning);
    }

    private LyricParticles.Options options() {
        float alpha = fade.get();
        int[] tint = textColor();
        return new LyricParticles.Options(
                mode.is(LyricParticles.MODE_WORDS),
                layout.getValue(),
                fontId(),
                size.getValue(),
                opacity.getValue() * alpha,
                throughWalls.getValue(),
                glow.getValue() * alpha,
                radius.getValue(),
                height.getValue(),
                Math.round(limit.getValue()),
                Math.round(sync.getValue()),
                exitId(),
                animationId(),
                tint[0],
                tint[1] != 0);
    }

    /** Название режима -> путь к атласу в assets/decide/fonts. */
    private String fontId() {
        if (font.is(FONT_SF_PRO)) {
            return "sf_pro_medium";
        }
        if (font.is(FONT_BOLD)) {
            return "sf_bold";
        }
        if (font.is(FONT_MONTSERRAT)) {
            return "montserrat";
        }
        return "manasco";
    }

    private int animationId() {
        String value = intro.getValue();

        if (ANIM_SMOOTH.equals(value)) return LyricParticles.ANIM_SMOOTH;
        if (ANIM_TYPEWRITER.equals(value)) return LyricParticles.ANIM_TYPEWRITER;
        if (ANIM_SCALE.equals(value)) return LyricParticles.ANIM_SCALE;
        if (ANIM_SLIDE.equals(value)) return LyricParticles.ANIM_SLIDE;
        if (ANIM_FADE.equals(value)) return LyricParticles.ANIM_FADE;
        return LyricParticles.ANIM_PROGRESSIVE;
    }

    private int exitId() {
        String value = exit.getValue();

        if (EXIT_FALL.equals(value)) return LyricParticles.EXIT_SHATTER;
        if (EXIT_SMOOTH.equals(value)) return LyricParticles.EXIT_SMOOTH;
        if (EXIT_TYPEWRITER.equals(value)) return LyricParticles.EXIT_TYPEWRITER;
        if (EXIT_SCALE.equals(value)) return LyricParticles.EXIT_SCALE;
        if (EXIT_SLIDE.equals(value)) return LyricParticles.EXIT_SLIDE;
        return LyricParticles.EXIT_FADE;
    }

    /**
     * Цвет текста: тема клиента, плавно перетекающая в сдвинутый по оттенку цвет
     * (градиент), либо чистый белый. Возвращает [rgb, признак «цветной»].
     */
    private int[] textColor() {
        if (colorMode.is(COLOR_WHITE)) {
            return new int[]{0xFFFFFF, 1};
        }

        int base = ThemeColor.getVisualColor();

        if (colorMode.is(COLOR_GRADIENT)) {
            int shifted = shiftHue(base, 45.0f);
            float ratio = (float) (Math.sin(System.currentTimeMillis() / 450.0) * 0.5 + 0.5);
            return new int[]{ColorUtil.interpolateColor(base, shifted, ratio, true), 1};
        }

        return new int[]{base, 1};
    }

    /** Сдвиг оттенка через HSB: тот же приём, что в «Kinetic Lyrics». */
    private static int shiftHue(int rgb, float degrees) {
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        int shifted = Color.HSBtoRGB((hsb[0] + degrees / 360.0f) % 1.0f, hsb[1], hsb[2]);
        return shifted & 0xFFFFFF;
    }
}
