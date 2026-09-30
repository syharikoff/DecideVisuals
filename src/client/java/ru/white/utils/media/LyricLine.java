package ru.white.utils.media;

import java.util.List;

/** Строка текста песни с таймингом и разбивкой на слова. */
public final class LyricLine {

    private final long startMillis;
    private final long endMillis;
    private final String text;
    private final List<LyricWord> words;

    public LyricLine(long startMillis, long endMillis, String text, List<LyricWord> words) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.text = text;
        this.words = words;
    }

    public long startMillis() {
        return startMillis;
    }

    public long endMillis() {
        return endMillis;
    }

    public String text() {
        return text;
    }

    public List<LyricWord> words() {
        return words;
    }

    public long durationMillis() {
        return Math.max(0L, endMillis - startMillis);
    }

    public boolean contains(long millis) {
        return millis >= startMillis && millis < endMillis;
    }

    public float progress(long millis) {
        long duration = durationMillis();
        if (duration <= 0L) {
            return millis >= startMillis ? 1.0f : 0.0f;
        }
        return Math.min(1.0f, Math.max(0.0f, (millis - startMillis) / (float) duration));
    }

    /** Слово, которое звучит в этот момент, либо null. */
    public LyricWord wordAt(long millis) {
        for (LyricWord word : words) {
            if (word.contains(millis)) {
                return word;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "LyricLine(" + startMillis + ".." + endMillis + " '" + text + "')";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LyricLine line)) return false;
        return startMillis == line.startMillis
                && endMillis == line.endMillis
                && text.equals(line.text)
                && words.equals(line.words);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(startMillis);
        result = 31 * result + Long.hashCode(endMillis);
        result = 31 * result + text.hashCode();
        result = 31 * result + words.hashCode();
        return result;
    }
}
