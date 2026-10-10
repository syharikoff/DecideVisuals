package ru.decide.utils.media;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Состояние медиаплеера для рендера: трек, сглаженная позиция, текст песни,
 * уровень вокала.
 *
 * Позиция плеера приходит квантами (200 мс у OptMedia.dll) и иногда отстаёт, поэтому
 * держим собственные часы: пока играет — тикаем сами, при большом расхождении с
 * нативным значением — прыгаем на него.
 */
public final class MediaPlayer {

    /** Расхождение, после которого своё время больше не верим. */
    private static final double CLOCK_SNAP_MILLIS = 300.0;
    /** Расхождение, которое догоняем плавно. */
    private static final double CLOCK_CATCHUP_MILLIS = 450.0;
    /** Сколько ждём стабильности трека перед запросом текста. */
    private static final long SETTLE_MILLIS = 250L;
    /** Не чаще раза в 40 мс — нативный опрос идёт раз в POLL_INTERVAL_MS. */
    private static final long TICK_MIN_INTERVAL_MS = 20L;

    private static final MediaPlayer INSTANCE = new MediaPlayer();

    private static double clockMillis;
    private static long clockNanos;
    private static boolean clockValid;
    private static boolean initialized;
    private static long revision;
    private static boolean transcriptFallback = true;
    private static long lyricsOffsetMillis;

    private static MediaTrack track = MediaTrack.EMPTY;
    private static Lyrics lyrics = Lyrics.EMPTY;
    private static CompletableFuture<Lyrics> pending;
    private static MediaTrack pendingTrack = MediaTrack.EMPTY;
    private static MediaTrack settleTrack;
    private static long settleStamp;
    private static long lastTickAtMs;
    private static int reportedLine = Integer.MIN_VALUE;

    private MediaPlayer() {}

    public static boolean init() {
        if (!initialized) {
            initialized = MediaNative.init();
        }
        return initialized;
    }

    public static void shutdown() {
        initialized = false;
        revision = Long.MIN_VALUE;
        track = MediaTrack.EMPTY;
        lyrics = Lyrics.EMPTY;
        pending = null;
        pendingTrack = MediaTrack.EMPTY;
        settleTrack = null;
        clockValid = false;
        MediaNative.shutdown();
    }

    /** Вызывается каждый тик: подхватывает смену трека и дожидается ответа по тексту. */
    public static void tick() {
        if (!initialized) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastTickAtMs < TICK_MIN_INTERVAL_MS) {
            return;
        }
        lastTickAtMs = now;

        long current = MediaNative.revision();
        if (current != revision) {
            revision = current;
            INSTANCE.refreshTrack();
        }

        INSTANCE.settle();

        CompletableFuture<Lyrics> future = pending;
        if (future != null && future.isDone()) {
            pending = null;
            if (pendingTrack.equals(track)) {
                lyrics = future.getNow(Lyrics.EMPTY);
                INSTANCE.report();
            } else {
                // Пока грузили, включили другой трек — выбрасываем результат
                MediaLog.note("client", "dropped lyrics of '" + pendingTrack.display()
                        + "', now playing '" + track.display() + "'");
                lyrics = Lyrics.EMPTY;
                settleTrack = track;
                settleStamp = System.currentTimeMillis();
            }
            pendingTrack = MediaTrack.EMPTY;
        }

        INSTANCE.follow();
    }

    /** Разовый отчёт о том, что загрузилось для трека. */
    private void report() {
        if (!MediaLog.enabled()) {
            return;
        }
        int worded = 0;
        for (LyricLine line : lyrics.lines()) {
            if (!line.words().isEmpty()) {
                worded++;
            }
        }
        List<LyricLine> lines = lyrics.lines();
        long first = lines.isEmpty() ? -1L : lines.get(0).startMillis();
        long last = lines.isEmpty() ? -1L : lines.get(lines.size() - 1).endMillis();
        MediaLog.note("client", "lyrics for '" + track.display() + "': lines=" + lines.size()
                + " synced=" + lyrics.synced() + " worded=" + worded
                + " span=" + first + ".." + last + "ms duration=" + getDurationMillis() + "ms");
    }

    /**
     * Раз в кадр пишет, какая строка сейчас звучит. Это и есть разница между
     * «текст отстаёт» и «текст не тот»: если offset стабильный — правь настройкой
     * «Смещение», если скачет — виновата позиция плеера.
     */
    private void follow() {
        if (!MediaLog.enabled() || !lyrics.synced()) {
            return;
        }
        long time = getLyricsTimeMillis();
        int index = lyrics.indexAt(time);
        if (index == reportedLine) {
            return;
        }
        reportedLine = index;
        if (index < 0) {
            return;
        }
        LyricLine line = lyrics.lines().get(index);
        MediaLog.note("sync", "pos=" + time + "ms offset=" + lyricsOffsetMillis
                + "ms line[" + index + "]=" + line.startMillis() + ".." + line.endMillis()
                + "ms lead=" + (time - line.startMillis()) + "ms '" + line.text() + "'");
    }

    /**
     * Ждём, пока трек перестанет меняться: иначе на заставке успеваем запросить текст
     * не того трека. Длительность приходит отдельным ревизией позже названия, поэтому
     * пока её нет — продолжаем ждать, а не отменяем запрос.
     */
    private void settle() {
        MediaTrack waiting = settleTrack;
        if (waiting == null) {
            return;
        }
        if (!waiting.equals(track) || track.isEmpty()) {
            settleTrack = null;
            return;
        }
        if (System.currentTimeMillis() - settleStamp < SETTLE_MILLIS) {
            return;
        }
        if (track.durationMillis() <= 0L) {
            // Длительность ещё не пришла — ждём следующей ревизии, не теряя трек
            settleStamp = System.currentTimeMillis();
            return;
        }
        settleTrack = null;
        pendingTrack = track;
        MediaLog.note("client", "requesting lyrics for '" + track.display() + "'");
        pending = LyricsService.request(track);
    }

    private double clock() {
        if (!initialized) {
            clockValid = false;
            return 0.0;
        }

        long now = System.nanoTime();
        double reported = MediaNative.positionMillis();

        if (!clockValid) {
            clockMillis = reported;
            clockNanos = now;
            clockValid = true;
            return clockMillis;
        }

        double elapsed = Math.max(0.0, (now - clockNanos) / 1_000_000.0);
        clockNanos = now;

        boolean running = MediaNative.isPlaying();
        if (running) {
            clockMillis += elapsed;
        }

        double drift = reported - clockMillis;
        if (!running || Math.abs(drift) >= CLOCK_SNAP_MILLIS) {
            clockMillis = reported;
        } else if (elapsed > 0.0) {
            // Небольшое расхождение догоняем постепенно, иначе текст дёргается
            clockMillis += drift * Math.min(1.0, elapsed / CLOCK_CATCHUP_MILLIS);
        }

        long duration = MediaNative.durationMillis();
        if (duration > 0L) {
            clockMillis = Math.max(0.0, Math.min((double) duration, clockMillis));
        }
        return clockMillis;
    }

    private void refreshTrack() {
        MediaTrack updated = new MediaTrack(
                MediaNative.title(), MediaNative.artist(), "", MediaNative.artist(),
                0, MediaNative.appId(), MediaNative.durationMillis());

        if (updated.equals(track)) {
            return;
        }

        track = updated;
        lyrics = Lyrics.EMPTY;
        pending = null;
        pendingTrack = MediaTrack.EMPTY;
        settleTrack = track;
        settleStamp = System.currentTimeMillis();
    }

    // ------------------------------------------------------------ публичное

    public static boolean isAvailable() {
        return initialized;
    }

    public static MediaTrack getTrack() {
        return track;
    }

    public static boolean isPlaying() {
        return initialized && MediaNative.isPlaying();
    }

    public static long getPositionMillis() {
        return Math.round(INSTANCE.clock());
    }

    public static long getDurationMillis() {
        return initialized ? MediaNative.durationMillis() : 0L;
    }

    public static Lyrics getLyrics() {
        return lyrics;
    }

    public static String getAppId() {
        return track.appId();
    }

    /** Имя .exe, который сейчас звучит, — для детектора вокала. */
    public static String vocalProcess() {
        return MediaNative.processFor(track.appId());
    }

    public static long getLyricsTimeMillis() {
        return getPositionMillis() + lyricsOffsetMillis;
    }

    public static void setLyricsOffsetMillis(long offset) {
        if (lyricsOffsetMillis != offset) {
            lyricsOffsetMillis = offset;
        }
    }

    public static void setTranscriptFallback(boolean value) {
        if (initialized && transcriptFallback != value) {
            transcriptFallback = value;
            LyricsService.setTranscripts(value);
            reloadLyrics();
        }
    }

    public static boolean startVocalDetect(String process) {
        return initialized && MediaNative.startVocalDetect(process);
    }

    public static void stopVocalDetect() {
        MediaNative.stopVocalDetect();
    }

    public static float vocalLevel() {
        return initialized ? MediaNative.vocalLevel() : -1.0f;
    }

    public static int vocalFrames() {
        return initialized ? MediaNative.vocalFrames() : -1;
    }

    public static void next() {
        if (initialized) MediaNative.next();
    }

    public static void previous() {
        if (initialized) MediaNative.previous();
    }

    public static void toggle() {
        if (initialized) MediaNative.toggle();
    }

    public static void seek(long millis) {
        if (initialized) {
            clockMillis = millis;
            clockNanos = System.nanoTime();
            clockValid = true;
            MediaNative.seek(millis);
        }
    }

    /** Перезапросить текст для текущего трека (сброс кэша). */
    public static void reloadLyrics() {
        LyricsService.forget(track);
        lyrics = Lyrics.EMPTY;
        settleTrack = null;
        pendingTrack = track;
        pending = LyricsService.request(track);
    }
}
