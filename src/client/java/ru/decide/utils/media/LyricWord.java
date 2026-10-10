package ru.decide.utils.media;

/**
 * Слово с таймингом. {@code begin}/{@code end} — смещения в строке, чтобы
 * рендерер мог подсветить ровно те символы, которые сейчас поются.
 */
public final class LyricWord {

    private final long startMillis;
    private final long endMillis;
    private final String text;
    private final int begin;
    private final int end;

    public LyricWord(long startMillis, long endMillis, String text, int begin, int end) {
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.text = text;
        this.begin = begin;
        this.end = end;
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

    public int begin() {
        return begin;
    }

    public int end() {
        return end;
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

    @Override
    public String toString() {
        return "LyricWord(" + startMillis + ".." + endMillis + " '" + text + "' " + begin + ".." + end + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof LyricWord w)) return false;
        return startMillis == w.startMillis
                && endMillis == w.endMillis
                && begin == w.begin
                && end == w.end
                && text.equals(w.text);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(startMillis);
        result = 31 * result + Long.hashCode(endMillis);
        result = 31 * result + text.hashCode();
        result = 31 * result + Integer.hashCode(begin);
        result = 31 * result + Integer.hashCode(end);
        return result;
    }
}
