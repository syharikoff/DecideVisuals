package ru.decide.utils.media;

import java.util.Collections;
import java.util.List;

/** Набор строк текста песни, отсортированный по времени старта. */
public final class Lyrics {

    public static final Lyrics EMPTY = new Lyrics(Collections.emptyList(), false);

    private final List<LyricLine> lines;
    private final boolean synced;

    public Lyrics(List<LyricLine> lines, boolean synced) {
        this.lines = lines;
        this.synced = synced;
    }

    public List<LyricLine> lines() {
        return lines;
    }

    /** true — тайминги пришли из enhanced-LRC, false — синтезированы из plain-текста. */
    public boolean synced() {
        return synced;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** Индекс строки, звучащей в момент {@code millis}, либо -1. */
    public int indexAt(long millis) {
        if (!synced) {
            return -1;
        }
        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = low + (high - low) / 2;
            LyricLine line = lines.get(middle);
            if (millis < line.startMillis()) {
                high = middle - 1;
                continue;
            }
            if (millis >= line.endMillis()) {
                low = middle + 1;
                continue;
            }
            return middle;
        }
        return -1;
    }

    public LyricLine lineAt(long millis) {
        int index = indexAt(millis);
        return index < 0 ? null : lines.get(index);
    }

    /** Первая строка, начинающаяся строго после {@code millis} — нужна для предпросмотра. */
    public LyricLine lineAfter(long millis) {
        for (LyricLine line : lines) {
            if (line.startMillis() > millis) {
                return line;
            }
        }
        return null;
    }

    public LyricWord wordAt(long millis) {
        LyricLine line = lineAt(millis);
        return line == null ? null : line.wordAt(millis);
    }

    /** Длительность последней строки — грубый хвост длительности песни. */
    public long millis() {
        long end = 0L;
        for (LyricLine line : lines) {
            if (line.endMillis() > end) {
                end = line.endMillis();
            }
        }
        return end;
    }
}
