package ru.decide.utils.media;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.CRC32;

/**
 * Асинхронный поиск текста песни: сначала кэш на диске, иначе запрос к
 * lrclib.net через {@link MediaNative#fetchLyrics}.
 *
 * Сеть и файлы трогаются только в воркере — рендер-поток ждёт {@link CompletableFuture}.
 * Неудачные попытки запираются в {@link #FAILURES} с растущей паузой, чтобы не
 * долбить сеть на каждом треке без текста.
 */
public final class LyricsService {

    private static final Path DIRECTORY =
            FabricLoader.getInstance().getGameDir().resolve("decide").resolve("lyrics");

    private static final Map<String, Lyrics> MEMORY = new ConcurrentHashMap<>();
    private static final Map<String, long[]> FAILURES = new ConcurrentHashMap<>();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "white-lyrics");
        thread.setDaemon(true);
        return thread;
    });

    /** Версия формата кэша — смена сбрасывает старые .lrc. */
    private static final String VERSION = "v1";

    /** 30 с → 2 мин → 10 мин → час между попытками для трека без текста. */
    private static final long[] RETRY_DELAYS_MS = { 30000L, 120000L, 600000L, 3600000L };

    private static volatile boolean transcripts = true;
    private static volatile boolean swept;

    private LyricsService() {}

    public static void setTranscripts(boolean value) {
        transcripts = value;
    }

    public static CompletableFuture<Lyrics> request(MediaTrack track) {
        if (track == null || track.isEmpty() || !MediaNative.available()) {
            return CompletableFuture.completedFuture(Lyrics.EMPTY);
        }

        String key = key(track);

        Lyrics cached = MEMORY.get(key);
        if (cached != null && !cached.isEmpty()) {
            return CompletableFuture.completedFuture(cached);
        }

        long[] failure = FAILURES.get(key);
        if (failure != null && System.currentTimeMillis() < failure[1]) {
            return CompletableFuture.completedFuture(cached == null ? Lyrics.EMPTY : cached);
        }

        return CompletableFuture.supplyAsync(() -> resolve(key, track), WORKER);
    }

    /** Сбросить кэш трека (например, F5 в GUI — перезапросить текст). */
    public static void forget(MediaTrack track) {
        if (track == null) {
            return;
        }
        String key = key(track);
        MEMORY.remove(key);
        FAILURES.remove(key);
        try {
            Files.deleteIfExists(DIRECTORY.resolve(key + ".lrc"));
        } catch (IOException ignored) {
        }
    }

    private static Lyrics resolve(String key, MediaTrack track) {
        sweep();

        Path file = DIRECTORY.resolve(key + ".lrc");
        Lyrics cached = LyricsParser.parse(read(file));
        if (cached.synced()) {
            MEMORY.put(key, cached);
            FAILURES.remove(key);
            return cached;
        }

        long started = System.currentTimeMillis();
        String content = MediaNative.fetchLyrics(
                track.title(), track.artist(), track.albumTitle(), track.durationSeconds());
        Lyrics fetched = LyricsParser.parse(content);
        MediaLog.note("fetch", "'" + track.display() + "' -> " + (content == null ? 0 : content.length())
                + " chars in " + (System.currentTimeMillis() - started) + "ms, synced=" + fetched.synced());

        if (fetched.synced()) {
            write(file, content);
        } else {
            // Не синхронизированный текст не кэшируем — вдруг в следующий раз найдётся лучше
            discard(file);
        }

        Lyrics resolved = fetched.isEmpty() ? cached : fetched;
        MEMORY.put(key, resolved);

        if (resolved.synced()) {
            FAILURES.remove(key);
        } else {
            long[] previous = FAILURES.get(key);
            int attempt = previous == null ? 0 : (int) Math.min(previous[0] + 1, RETRY_DELAYS_MS.length - 1);
            FAILURES.put(key, new long[]{ attempt, System.currentTimeMillis() + RETRY_DELAYS_MS[attempt] });
        }
        return resolved;
    }

    /** Разовая чистка кэша, если сменилась версия формата. */
    private static void sweep() {
        if (swept) {
            return;
        }
        synchronized (LyricsService.class) {
            if (swept) {
                return;
            }
            swept = true;
        }

        try {
            Path marker = DIRECTORY.resolve("version");
            if (Files.exists(marker)) {
                if (VERSION.equals(Files.readString(marker, StandardCharsets.UTF_8).trim())) {
                    return;
                }
            }
            if (Files.isDirectory(DIRECTORY)) {
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(DIRECTORY, "*.lrc")) {
                    Iterator<Path> it = entries.iterator();
                    while (it.hasNext()) {
                        Files.deleteIfExists(it.next());
                    }
                }
            }
            Files.createDirectories(DIRECTORY);
            Files.writeString(marker, VERSION, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static void discard(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }

    private static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
        } catch (IOException failure) {
            return null;
        }
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(DIRECTORY);
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** CRC32 от метаданных трека — короткий безопасный для имени файла ключ. */
    private static String key(MediaTrack track) {
        CRC32 checksum = new CRC32();
        String source = String.join("|",
                track.title(), track.artist(), track.albumTitle(),
                String.valueOf(track.durationSeconds()),
                VERSION, transcripts ? "asr1" : "asr0");
        checksum.update(source.getBytes(StandardCharsets.UTF_8));
        return Long.toHexString(checksum.getValue());
    }
}
