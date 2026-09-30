package ru.white.utils.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Диагностика Lyrics Text. По умолчанию пишет в лог клиента, при
 * {@code -Dwhite.lyrics.log=<путь>} — ещё и в отдельный файл.
 *
 * Нужен, чтобы отличить «плеер отдаёт не ту позицию» от «lrclib вернул текст другой
 * песни»: первое лечится офсетом, второе — подбором запроса.
 */
public final class MediaLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("client/LyricsText");

    private static final Path FILE = resolveFile();
    private static final boolean ENABLED = Boolean.getBoolean("white.lyrics.log")
            || System.getProperty("white.lyrics.trace") != null;

    private MediaLog() {}

    private static Path resolveFile() {
        String path = System.getProperty("white.lyrics.log");
        if (path == null || path.isBlank()) {
            return null;
        }
        return Path.of(path);
    }

    public static boolean enabled() {
        return ENABLED;
    }

    public static void note(String tag, String message) {
        if (!ENABLED) {
            return;
        }
        String line = "[" + tag + "] " + message;
        LOGGER.info(line);
        if (FILE == null) {
            return;
        }
        try {
            Files.createDirectories(FILE.getParent());
            try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(
                    FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
                writer.println(line);
            }
        } catch (IOException ignored) {
        }
    }
}
