package ru.white.module.impl.render;

import ru.white.manager.event_impl.EventRender3D;
import ru.white.manager.event_impl.EventTick;
import ru.white.manager.event_impl.WorldLoadEvent;
import ru.white.manager.events.orbit.EventHandler;
import ru.white.module.api.Category;
import ru.white.module.api.Module;
import ru.white.module.api.ModuleInfo;
import ru.white.module.api.settings.impl.BooleanSetting;
import ru.white.module.api.settings.impl.DelimiterSetting;
import ru.white.module.api.settings.impl.ModeSetting;
import ru.white.module.api.settings.impl.SliderSetting;
import ru.white.module.impl.render.lyrics.LyricParticles;
import ru.white.utils.media.MediaLog;
import ru.white.utils.media.MediaPlayer;
import ru.white.utils.animation.Animation;
import ru.white.utils.animation.Easings;
import ru.white.utils.render.lyrics.LyricText3D;

/**
 * Строки играющей песни висят в мире перед игроком. Порт «Lyrics Text» из Kimiko.
 *
 * Текст берётся с lrclib.net по названию текущего трека из системных медиа-сессий
 * Windows, тайминги слов разогревают глифы, а реальный вокал (наш
 * {@code NightixVocal.dll}) подмешивается в яркость свечения.
 */
@ModuleInfo(
        name = "Lyrics Text",
        desc = "Строки играющей песни висят в мире перед вами",
        category = Category.VISUALS
)
public class LyricsText extends Module {

    private static final String EXIT_FADE = "Затухание";
    private static final String EXIT_FALL = "Распад";

    /** Шрифты, которые были в Kimiko. Manasco — по умолчанию, как и там. */
    private static final String FONT_MANASCO = "Manasco";
    private static final String FONT_SF_PRO  = "SF";

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

    public ModeSetting font = new ModeSetting(this, "Шрифт", FONT_MANASCO, FONT_SF_PRO);

    public SliderSetting size = new SliderSetting(this, "Размер строки", 0.3f, 0.05f, 2.0f, 0.01f);

    public SliderSetting opacity = new SliderSetting(this, "Прозрачность", 1.0f, 0.1f, 1.0f, 0.05f);

    public BooleanSetting throughWalls = new BooleanSetting(this, "Сквозь стены", false);

    public DelimiterSetting styleSeparator = new DelimiterSetting(this, "Кастомизация");

    public SliderSetting glow = new SliderSetting(this, "Свечение", 1.0f, 0.0f, 3.0f, 0.05f);

    public ModeSetting exit = new ModeSetting(this, "Уход", EXIT_FADE, EXIT_FALL);

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
                exit.is(EXIT_FALL));
    }

    /** Название режима -> путь к атласу в assets/client/fonts. */
    private String fontId() {
        return font.is(FONT_SF_PRO) ? "sf_pro_medium" : "manasco";
    }
}
