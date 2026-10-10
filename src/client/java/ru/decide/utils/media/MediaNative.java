package ru.decide.utils.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Мост к нативным библиотекам и сети.
 *
 * Две DLL, обе грузятся из ресурсов и распаковываются во временный файл:
 * <ul>
 *   <li>{@code OptMedia.dll} — системные медиа-сессии Windows (трек, позиция, play/pause);</li>
 *   <li>{@code DecideVocal.dll} — наш детектор вокала (loopback + полосовой фильтр).</li>
 * </ul>
 *
 * Плюс поиск текста песни на lrclib.net.
 */
public final class MediaNative {

    private static final String MEDIA_RESOURCE = "/assets/decide/natives/OptMedia.dll";
    private static final String VOCAL_RESOURCE = "/assets/decide/natives/DecideVocal.dll";

    /** Как часто опрашиваем медиа-сессии. Меньше — точнее позиция и быстрее реакция на смену трека. */
    private static final long POLL_INTERVAL_MS = 100L;

    private static Boolean available;
    private static OptMediaLibrary media;
    private static DecideVocalLibrary vocal;

    private static volatile String currentTitle = "";
    private static volatile String currentArtist = "";
    private static volatile String currentAppId = "";
    private static volatile boolean isPlaying;
    private static volatile long currentDurationMs;
    private static volatile long currentPositionMs;
    private static volatile long revision = 1L;

    private static volatile int vocalProcessId = 0;
    private static volatile boolean vocalRunning;

    private static volatile ScheduledExecutorService poller;

    private MediaNative() {}

    public static synchronized boolean available() {
        if (available == null) {
            available = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                    && loadMedia();
        }
        return available != null && available;
    }

    private static synchronized boolean loadMedia() {
        if (media != null) {
            return true;
        }
        try {
            Path dll = extract(MEDIA_RESOURCE, "OptMedia_", ".dll");
            Map<String, Object> options = new HashMap<>();
            options.put(Library.OPTION_ALLOW_OBJECTS, Boolean.TRUE);
            media = Native.load(dll.toAbsolutePath().toString(), OptMediaLibrary.class, options);
            return media != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static synchronized boolean loadVocal() {
        if (vocal != null) {
            return true;
        }
        try {
            Path dll = extract(VOCAL_RESOURCE, "DecideVocal_", ".dll");
            vocal = Native.load(dll.toAbsolutePath().toString(), DecideVocalLibrary.class);
            return vocal != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** Достаёт DLL из jar/ресурсов во временный файл — LoadLibrary не умеет из classpath. */
    private static Path extract(String resource, String prefix, String suffix) throws Exception {
        try (InputStream in = MediaNative.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("нет ресурса " + resource);
            }
            Path temp = Files.createTempFile(prefix, suffix);
            temp.toFile().deleteOnExit();
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            return temp;
        }
    }

    public static synchronized boolean init() {
        if (!available()) {
            return false;
        }
        if (poller == null || poller.isShutdown()) {
            poller = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "white-media-poller");
                thread.setDaemon(true);
                return thread;
            });
            poller.scheduleWithFixedDelay(MediaNative::poll, 0L, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
        return true;
    }

    public static synchronized void shutdown() {
        if (poller != null) {
            poller.shutdownNow();
            poller = null;
        }
        if (media != null) {
            try {
                media.Deinitialize();
            } catch (Throwable ignored) {
            }
        }
        stopVocalDetect();
        currentTitle = "";
        currentArtist = "";
        currentAppId = "";
        isPlaying = false;
        currentDurationMs = 0L;
        currentPositionMs = 0L;
    }

    private static void poll() {
        if (media == null) {
            return;
        }
        try {
            OptMediaArtist artist = new OptMediaArtist();
            // JNA по умолчанию пишет поля структуры обратно в нативную память ПОСЛЕ
            // вызова — то есть затирал бы всё, что DLL только что туда положила.
            // Поэтому отключаем автозапись и читаем результат вручную.
            artist.setAutoWrite(false);
            if (media.GetCurrentMediaInfo(artist)) {
                artist.read();
                String title = artist.getTitle();
                String name = artist.getArtist();
                String app = artist.getSource();
                boolean playing = artist.isPlaying();
                long duration = Math.max(0L, artist.durationMs);
                long position = Math.max(0L, artist.positionMs);

                boolean changed = !Objects.equals(title, currentTitle)
                        || !Objects.equals(name, currentArtist)
                        || playing != isPlaying
                        || duration != currentDurationMs;

                if (changed) {
                    currentTitle = title;
                    currentArtist = name;
                    currentAppId = app;
                    isPlaying = playing;
                    currentDurationMs = duration;
                    currentPositionMs = position;
                    revision++;
                    MediaLog.note("media", "track='" + title + "' artist='" + name
                            + "' app='" + app + "' playing=" + playing
                            + " duration=" + duration + "ms pos=" + position + "ms");
                } else {
                    currentPositionMs = position;
                }
                media.FreeMediaInfo(artist);
            } else if (isPlaying) {
                isPlaying = false;
                revision++;
                MediaLog.note("media", "session closed");
            }
        } catch (Throwable ignored) {
        }
    }

    // ---------------------------------------------------------------- трек

    public static long revision() {
        return revision;
    }

    public static String title() {
        return currentTitle;
    }

    public static String artist() {
        return currentArtist;
    }

    public static String appId() {
        return currentAppId;
    }

    public static boolean isPlaying() {
        return isPlaying;
    }

    public static long durationMillis() {
        return currentDurationMs;
    }

    public static long positionMillis() {
        return currentPositionMs;
    }

    public static String status() {
        if (currentTitle.isEmpty()) {
            return "closed";
        }
        return isPlaying ? "playing" : "paused";
    }

    // ------------------------------------------------------------ управление

    public static void play() {
        if (media != null) media.Play();
    }

    public static void pause() {
        if (media != null) media.Pause();
    }

    public static void toggle() {
        if (media != null) media.TogglePlayPause();
    }

    public static void next() {
        if (media != null) media.SkipNext();
    }

    public static void previous() {
        if (media != null) media.SkipPrevious();
    }

    public static void seek(long millis) {
        if (media != null) {
            media.SeekToMs(millis);
            currentPositionMs = millis;
        }
    }

    // -------------------------------------------------------------- вокал

    /**
     * Запускает детектор вокала. Идемпотентно по имени процесса: повторный вызов
     * с тем же процессом ничего не делает.
     */
    public static synchronized boolean startVocalDetect(String process) {
        if (process == null || process.isEmpty() || !loadVocal()) {
            return false;
        }
        if (vocalRunning && process.hashCode() == vocalProcessId) {
            return true;
        }
        stopVocalDetect();
        // Имя процесса нужно DLL, чтобы заглушить его аудио-сессию: иначе вокала
        // в тракте нет, но в колонках он есть, и свечение будет ложным.
        boolean started = vocal.VocalStart(process) != 0;
        if (started) {
            vocalProcessId = process.hashCode();
            vocalRunning = true;
        }
        return started;
    }

    public static synchronized void stopVocalDetect() {
        if (vocal != null) {
            try {
                vocal.VocalStop();
            } catch (Throwable ignored) {
            }
        }
        vocalRunning = false;
        vocalProcessId = 0;
    }

    /** Загружена ли DLL детектора вокала (не значит, что захват идёт). */
    public static boolean vocalAvailable() {
        return loadVocal();
    }

    /** Энергия вокала 0..1, либо -1 если захват не идёт. */
    public static float vocalLevel() {
        if (vocal == null || !vocalRunning) {
            return -1.0f;
        }
        try {
            return vocal.VocalLevel();
        } catch (Throwable ignored) {
            return -1.0f;
        }
    }

    public static int vocalFrames() {
        if (vocal == null || !vocalRunning) {
            return -1;
        }
        try {
            return vocal.VocalFrames();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    /** Мгновенный пик огибающей, 0 если захват не идёт. */
    public static float vocalPeak() {
        if (vocal == null || !vocalRunning) {
            return 0.0f;
        }
        try {
            return vocal.VocalPeak();
        } catch (Throwable ignored) {
            return 0.0f;
        }
    }

    public static int vocalFlags() {
        if (vocal == null) {
            return 0;
        }
        try {
            return vocal.VocalFlags();
        } catch (Throwable ignored) {
            return 0;
        }
    }

    // --------------------------------------------------------- поиск текста

    /**
     * Имя .exe, который звучит, по id приложения из медиа-сессии.
     *
     * Нужно детектору вокала: он заглушает аудио-сессию этого процесса, чтобы тишина
     * в плеере давала тишину вокала. В сессии приходит не exe, а разные id вроде
     * {@code MSEdge} или {@code spotify.exe}, поэтому разбираем по списку плееров.
     */
    public static String processFor(String appId) {
        String[] candidates = processCandidates(appId);
        return candidates.length == 0 ? null : candidates[0];
    }

    private static final String[] BROWSER_PROCESSES = {
            "chrome.exe", "msedge.exe", "firefox.exe", "opera.exe", "browser.exe", "yandex.exe"
    };

    private static String[] processCandidates(String appId) {
        if (appId == null || appId.isEmpty()) {
            return new String[0];
        }
        String lower = appId.toLowerCase(Locale.ROOT);
        if (lower.contains("spotify")) {
            return new String[]{ "Spotify.exe" };
        }
        if (lower.contains("yandex") || lower.contains("music.desktop")) {
            return new String[]{ "Яндекс Музыка.exe", "YandexMusic.exe", "Yandex.Music.exe", "browser.exe" };
        }
        if (lower.contains("aimp")) {
            return new String[]{ "AIMP.exe" };
        }
        if (lower.contains("foobar")) {
            return new String[]{ "foobar2000.exe" };
        }
        if (lower.contains("deezer")) {
            return new String[]{ "Deezer.exe" };
        }
        if (lower.contains("tidal")) {
            return new String[]{ "TIDAL.exe" };
        }
        if (lower.contains("apple")) {
            return new String[]{ "AppleMusic.exe" };
        }
        if (lower.contains("vlc")) {
            return new String[]{ "vlc.exe" };
        }
        if (lower.contains("musicbee")) {
            return new String[]{ "MusicBee.exe" };
        }
        if (lower.contains("winamp")) {
            return new String[]{ "winamp.exe" };
        }
        if (lower.endsWith(".exe")) {
            return new String[]{ appId };
        }
        // Браузер: сессия приходит от вкладки, а звучит всё равно процесс браузера
        for (String browser : BROWSER_PROCESSES) {
            if (lower.contains(browser.replace(".exe", ""))) {
                return new String[]{ browser };
            }
        }
        if (lower.endsWith("browser.exe") || lower.contains("chromium")) {
            return new String[]{ "chrome.exe" };
        }
        return new String[0];
    }

    /**
     * Ищет LRC на lrclib.net.
     *
     * Стратегия повторяет Kimiko (точный get по названию+исполнителю, затем поиск,
     * затем разбор «Artist - Title» из самого названия), но с проверкой ответа:
     * поиск lrclib регулярно отдаёт мусорные записи с одной строкой, и без отсева
     * такой текст вешается в мире с чужими таймингами.
     */
    public static String fetchLyrics(String title, String artist, String album, int durationSeconds) {
        if (title == null || title.isBlank()) {
            return null;
        }

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            String cleanArtist = cleanArtistName(artist);
            String cleanTitle = cleanLyricsTitle(title, cleanArtist);

            // 1. Точный запрос: сервер сам отфильтрует по названию, исполнителю и длительности
            if (!cleanArtist.isBlank()
                    && !cleanArtist.equalsIgnoreCase("Mix")
                    && !cleanArtist.equalsIgnoreCase("YouTube")) {
                String exact = "https://lrclib.net/api/get?track_name=" + encode(cleanTitle)
                        + "&artist_name=" + encode(cleanArtist)
                        + (durationSeconds > 0 ? "&duration=" + durationSeconds : "");
                String result = query(client, exact, false, durationSeconds, cleanTitle, cleanArtist);
                if (result != null && !result.isBlank()) {
                    return result;
                }

                result = search(client, cleanTitle + " " + cleanArtist, durationSeconds, cleanTitle, cleanArtist);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            }

            // 2. Поиск только по названию
            String result = search(client, cleanTitle, durationSeconds, cleanTitle, cleanArtist);
            if (result != null && !result.isBlank()) {
                return result;
            }

            // 3. В названии может быть «Artist - Title»
            if (title.contains("-") || title.contains("–") || title.contains("—")) {
                String[] parts = title.split("[-–—]", 2);
                if (parts.length == 2) {
                    String head = cleanArtistName(parts[0].trim());
                    String tail = cleanLyricsTitle(parts[1].trim(), null);

                    result = search(client, head + " " + tail, durationSeconds, tail, head);
                    if (result != null && !result.isBlank()) {
                        return result;
                    }

                    result = query(client, "https://lrclib.net/api/get?track_name=" + encode(tail)
                            + "&artist_name=" + encode(head), false, durationSeconds, tail, head);
                    if (result != null && !result.isBlank()) {
                        return result;
                    }

                    if (!tail.isBlank() && tail.length() >= 3) {
                        result = search(client, tail, durationSeconds, tail, head);
                        if (result != null && !result.isBlank()) {
                            return result;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String search(HttpClient client, String term, int durationSeconds,
                                 String wantTitle, String wantArtist) {
        return query(client, "https://lrclib.net/api/search?q=" + encode(term), true,
                durationSeconds, wantTitle, wantArtist);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String query(HttpClient client, String url, boolean isArray, int durationSeconds,
                                String wantTitle, String wantArtist) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "DecideClient/1.0")
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                return null;
            }

            if (!isArray) {
                // /api/get — один объект, поле syncedLyrics
                return accepted(JsonParser.parseString(body).getAsJsonObject(), durationSeconds, wantTitle, wantArtist);
            }

            // /api/search — массив, берём лучшее совпадение, а не первое попавшееся
            JsonArray array = JsonParser.parseString(body).getAsJsonArray();
            JsonObject best = null;
            int bestScore = Integer.MIN_VALUE;
            for (JsonElement element : array) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject candidate = element.getAsJsonObject();
                int score = score(candidate, durationSeconds, wantTitle, wantArtist);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            return best == null ? null : accepted(best, durationSeconds, wantTitle, wantArtist);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Оценка совпадения. Возвращает -1, если запись брать нельзя.
     *
     * Жёсткое правило: у настоящего текста песни минимум несколько строк с метками
     * времени. lrclib держит служебные записи вроде {@code [00:00.00] *Rickrolling*}
     * с тем же названием и исполнителем, и без отсева именно они приходят первыми —
     * текст висит в мире с чужими таймингами.
     */
    private static int score(JsonObject entry, int durationSeconds, String wantTitle, String wantArtist) {
        String synced = stringOf(entry, "syncedLyrics");
        String plain = stringOf(entry, "plainLyrics");
        boolean hasSynced = synced != null && !synced.isBlank();
        boolean hasPlain = plain != null && !plain.isBlank();
        if (!hasSynced && !hasPlain) {
            return -1;
        }

        int score = 0;

        if (hasSynced) {
            int lines = countTimedLines(synced);
            if (lines < MIN_LYRIC_LINES) {
                return -1;
            }
            if (lines >= 8) {
                score += 30;
            } else {
                score += 10;
            }
        } else if (countTextLines(plain) < MIN_LYRIC_LINES) {
            return -1;
        }

        if (durationSeconds > 0) {
            double resultDuration = entry.has("duration") ? entry.get("duration").getAsDouble() : 0.0;
            if (resultDuration > 0.0) {
                double delta = Math.abs(resultDuration - durationSeconds);
                if (delta <= 4.0) {
                    score += 120;
                } else if (delta <= 12.0) {
                    score += 55;
                } else if (delta <= 40.0) {
                    score += 10;
                } else {
                    score -= 60;
                }
            }
        }

        String title = normalize(stringOf(entry, "trackName"));
        String expectedTitle = normalize(wantTitle);
        if (!title.isEmpty() && !expectedTitle.isEmpty()) {
            if (title.equals(expectedTitle)) {
                score += 60;
            } else if (title.contains(expectedTitle) || expectedTitle.contains(title)) {
                score += 25;
            } else {
                score -= 30;
            }
        }

        String entryArtist = normalize(stringOf(entry, "artistName"));
        String expectedArtist = normalize(wantArtist);
        if (!entryArtist.isEmpty() && !expectedArtist.isEmpty()) {
            if (entryArtist.equals(expectedArtist)) {
                score += 60;
            } else if (entryArtist.contains(expectedArtist) || expectedArtist.contains(entryArtist)) {
                score += 25;
            }
        }

        return score;
    }

    private static final int MIN_LYRIC_LINES = 3;

    /** Возвращает LRC записи, если она прошла проверку, иначе null. */
    private static String accepted(JsonObject entry, int durationSeconds, String wantTitle, String wantArtist) {
        if (score(entry, durationSeconds, wantTitle, wantArtist) < 0) {
            return null;
        }
        String synced = stringOf(entry, "syncedLyrics");
        if (synced != null && !synced.isBlank()) {
            return synced;
        }
        String plain = stringOf(entry, "plainLyrics");
        if (plain != null && !plain.isBlank()) {
            return plainToLrc(plain, durationSeconds);
        }
        return null;
    }

    private static int countTimedLines(String lrc) {
        int count = 0;
        for (String line : lrc.split("\\R")) {
            if (LINE_TIME.matcher(line.trim()).find()) {
                count++;
            }
        }
        return count;
    }

    private static int countTextLines(String text) {
        int count = 0;
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static final Pattern LINE_TIME = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

    /** Приводит название к сравнимому виду: нижний регистр, только буквы и цифры. */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static String stringOf(JsonObject object, String field) {
        if (object.has(field) && !object.get(field).isJsonNull()) {
            return object.get(field).getAsString();
        }
        return null;
    }

    /**
     * Превращает plain-текст в LRC: интро/аутро оставляем пустыми, остаток
     * делим между строками пропорционально их длине.
     */
    private static String plainToLrc(String plain, int durationSeconds) {
        if (plain == null || plain.isBlank()) {
            return null;
        }

        Pattern section = Pattern.compile("^\\[[A-Za-z\\s0-9_\\-:]+\\]$");
        java.util.List<String> valid = new java.util.ArrayList<>();
        for (String raw : plain.split("\\R")) {
            String trimmed = raw.trim();
            if (!trimmed.isEmpty() && !section.matcher(trimmed).matches()) {
                valid.add(trimmed);
            }
        }
        if (valid.isEmpty()) {
            return null;
        }

        int duration = durationSeconds > 0 ? durationSeconds : Math.max(60, valid.size() * 4);
        double intro = Math.min(12.0, Math.max(4.0, duration * 0.07));
        double outro = Math.min(12.0, Math.max(4.0, duration * 0.07));
        double singTime = Math.max(10.0, duration - intro - outro);

        int totalWeight = 0;
        int[] weights = new int[valid.size()];
        for (int i = 0; i < valid.size(); i++) {
            weights[i] = Math.max(1, valid.get(i).length());
            totalWeight += weights[i];
        }

        StringBuilder builder = new StringBuilder();
        int cumulative = 0;
        for (int i = 0; i < valid.size(); i++) {
            double time = intro + ((double) cumulative / totalWeight) * singTime;
            int minutes = (int) (time / 60.0);
            double seconds = time % 60.0;
            builder.append(String.format(Locale.ROOT, "[%02d:%05.2f]%s\n", minutes, seconds, valid.get(i)));
            cumulative += weights[i];
        }
        return builder.toString();
    }

    /** Выкидывает из названия мусор из тегов: «(Official Video)», «4K», «feat. …» и т.п. */
    private static String cleanLyricsTitle(String title, String cleanArtist) {
        if (title == null) {
            return "";
        }
        String result = title.replaceAll("[⋆★☆•·*]", " ")
                .replaceAll("\\[[^\\]]*]", " ")
                .replaceAll("\\([^\\)]*\\)", " ")
                .replaceAll("(?i)\\b(official\\s+(video|audio|music\\s+video)|lyrics|lyric\\s+video|remix|hd|4k|ft\\.?.*|feat\\.?.*)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleanArtist != null && !cleanArtist.isBlank()) {
            String stripped = result.replaceFirst("(?i)^" + Pattern.quote(cleanArtist) + "\\s*[-–—:]\\s*", "").trim();
            if (!stripped.isEmpty()) {
                result = stripped;
            }
        }
        return result.isEmpty() ? title.trim() : result;
    }

    private static String cleanArtistName(String artist) {
        if (artist == null) {
            return "";
        }
        String result = artist.replaceAll("[⋆★☆•·*]", " ")
                .replaceAll("(?i)\\s*-\\s*topic$", "")
                .replaceAll("(?i)\\s+topic$", "")
                .replaceAll("(?i)\\s*vevo$", "")
                .replaceAll("\\[[^\\]]*]", " ")
                .replaceAll("\\([^\\)]*\\)", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return result.isEmpty() ? artist.trim() : result;
    }
}
