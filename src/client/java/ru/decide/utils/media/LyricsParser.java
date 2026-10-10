package ru.decide.utils.media;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор LRC / enhanced-LRC в {@link Lyrics}.
 *
 * Поддерживает три источника таймингов, в порядке убывания точности:
 * <ol>
 *   <li>enhanced-LRC — метки {@code <mm:ss.xx>} на слова;</li>
 *   <li>обычный LRC — метки строк {@code [mm:ss.xx]}, слова внутри строки
 *       распределяются по числу слогов;</li>
 *   <li>plain-текст без меток — строки строятся, но {@code synced} = false.</li>
 * </ol>
 */
public final class LyricsParser {

    private static final Pattern LINE_TIME = Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern WORD_TIME = Pattern.compile("<(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern OFFSET    = Pattern.compile("\\[offset:\\s*([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG        = Pattern.compile("\\[[a-zA-Z#]+:.*]");

    private static final long TAIL_MILLIS = 5000L;
    private static final long MILLIS_PER_SYLLABLE = 300L;
    private static final long MIN_SUNG_MILLIS = 1500L;
    private static final long FULL_SPAN_MILLIS = 7000L;

    private static final String CYRILLIC_VOWELS = "аеёиоуыэюяіїє";
    private static final String LATIN_VOWELS = "aeiouy";

    private LyricsParser() {}

    public static Lyrics parse(String content) {
        if (content == null || content.isBlank()) {
            return Lyrics.EMPTY;
        }

        long offset = 0L;
        Matcher offsetMatcher = OFFSET.matcher(content);
        if (offsetMatcher.find()) {
            offset = Long.parseLong(offsetMatcher.group(1));
        }

        // {время, индекс текста} и сами тексты — параллельные списки
        List<long[]> stamps = new ArrayList<>();
        List<String> bodies = new ArrayList<>();
        List<String> plain = new ArrayList<>();

        for (String raw : content.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }

            Matcher matcher = LINE_TIME.matcher(line);
            int consumed = 0;
            int first = stamps.size();
            // Одна строка может нести несколько меток: [00:10.00][01:20.00]припев
            while (matcher.find(consumed) && matcher.start() == consumed) {
                stamps.add(new long[]{ timestamp(matcher, 1) - offset, bodies.size() });
                consumed = matcher.end();
            }

            if (stamps.size() == first) {
                if (TAG.matcher(line).matches()) {
                    continue;
                }
                plain.add(line);
                continue;
            }
            bodies.add(line.substring(consumed).trim());
        }

        if (stamps.isEmpty()) {
            if (plain.isEmpty()) {
                return Lyrics.EMPTY;
            }
            List<LyricLine> lines = new ArrayList<>(plain.size());
            for (String plainLine : plain) {
                lines.add(new LyricLine(0L, 0L, plainLine, Collections.emptyList()));
            }
            return new Lyrics(List.copyOf(lines), false);
        }

        stamps.sort(Comparator.comparingLong(a -> a[0]));

        List<LyricLine> lines = new ArrayList<>(stamps.size());
        for (int index = 0; index < stamps.size(); index++) {
            long[] stamp = stamps.get(index);
            long start = Math.max(0L, stamp[0]);
            long end = index + 1 < stamps.size()
                    ? Math.max(start, stamps.get(index + 1)[0])
                    : start + TAIL_MILLIS;
            String body = bodies.get((int) stamp[1]);
            lines.add(WORD_TIME.matcher(body).find()
                    ? enhanced(body, start, end, offset)
                    : plainLine(body, start, end));
        }
        return new Lyrics(List.copyOf(lines), true);
    }

    /** Строка с одной меткой: слова распределяются по длине, близкой к реальной. */
    private static LyricLine plainLine(String body, long start, long end) {
        long span = Math.max(1L, end - start);
        long natural = Math.max((long) syllables(body, 0, body.length()) * MILLIS_PER_SYLLABLE, MIN_SUNG_MILLIS);
        long sung = span <= FULL_SPAN_MILLIS ? span : Math.min(span, natural);
        return new LyricLine(start, end, body, distribute(body, start, start + sung));
    }

    /** Строка enhanced-LRC: тайминги слов даны явно, остаётся их вырезать из текста. */
    private static LyricLine enhanced(String body, long start, long end, long offset) {
        StringBuilder text = new StringBuilder();
        List<LyricWord> words = new ArrayList<>();
        // {время начала, begin, end} в координатах накапливаемого text
        List<long[]> pending = new ArrayList<>();

        Matcher matcher = WORD_TIME.matcher(body);
        int cursor = 0;
        long current = -1L;
        while (matcher.find()) {
            String chunk = body.substring(cursor, matcher.start());
            if (current >= 0L) {
                pending.add(new long[]{ current, text.length(), text.length() + chunk.length() });
            }
            text.append(chunk);
            current = Math.max(0L, timestamp(matcher, 1) - offset);
            cursor = matcher.end();
        }

        String tail = body.substring(cursor);
        if (current >= 0L) {
            pending.add(new long[]{ current, text.length(), text.length() + tail.length() });
        }
        text.append(tail);

        for (int index = 0; index < pending.size(); index++) {
            long[] span = pending.get(index);
            long wordStart = span[0];
            long wordEnd = index + 1 < pending.size()
                    ? Math.max(wordStart, pending.get(index + 1)[0])
                    : end;

            // Подрезаем пробелы по краям, чтобы в words не попадали разделители
            int begin = (int) span[1];
            int finish = (int) span[2];
            while (begin < finish && Character.isWhitespace(text.charAt(begin))) {
                begin++;
            }
            while (finish > begin && Character.isWhitespace(text.charAt(finish - 1))) {
                finish--;
            }
            if (finish <= begin) {
                continue;
            }
            words.add(new LyricWord(wordStart, wordEnd, text.substring(begin, finish), begin, finish));
        }
        return new LyricLine(start, end, text.toString(), List.copyOf(words));
    }

    /**
     * Раскидывает слова по времени пропорционально числу слогов: длинное слово
     * поётся дольше короткого, иначе подсветка дёргается на каждом переходе.
     */
    private static List<LyricWord> distribute(String text, long start, long end) {
        List<int[]> spans = new ArrayList<>();   // {begin, end, вес}
        int total = 0;
        int cursor = 0;
        while (cursor < text.length()) {
            while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            int begin = cursor;
            while (cursor < text.length() && !Character.isWhitespace(text.charAt(cursor))) {
                cursor++;
            }
            if (cursor <= begin) {
                continue;
            }
            int weight = syllables(text, begin, cursor);
            spans.add(new int[]{ begin, cursor, weight });
            total += weight;
        }
        if (spans.isEmpty() || total == 0) {
            return Collections.emptyList();
        }

        long duration = Math.max(0L, end - start);
        List<LyricWord> words = new ArrayList<>(spans.size());
        long accumulated = 0L;
        for (int[] span : spans) {
            long wordStart = start + duration * accumulated / total;
            accumulated += span[2];
            long wordEnd = start + duration * accumulated / total;
            words.add(new LyricWord(wordStart, wordEnd, text.substring(span[0], span[1]), span[0], span[1]));
        }
        return List.copyOf(words);
    }

    /** Грубая оценка слогов: гласные, при latin-гласных подряд — одна гласная на слог. */
    private static int syllables(String text, int begin, int end) {
        int count = 0;
        boolean spoken = false;
        boolean latinRun = false;
        for (int index = begin; index < end; index++) {
            char symbol = Character.toLowerCase(text.charAt(index));
            if (Character.isLetterOrDigit(symbol)) {
                spoken = true;
            }
            if (CYRILLIC_VOWELS.indexOf(symbol) >= 0) {
                count++;
                latinRun = false;
                continue;
            }
            if (LATIN_VOWELS.indexOf(symbol) >= 0) {
                if (!latinRun) {
                    count++;
                }
                latinRun = true;
                continue;
            }
            latinRun = false;
        }
        return count > 0 ? count : (spoken ? 1 : 0);
    }

    /** Читает {@code [m:ss}, {@code [m:ss.x} или {@code [m:ss.xxx} из групп 1..3. */
    private static long timestamp(Matcher matcher, int group) {
        long minutes = Long.parseLong(matcher.group(group));
        long seconds = Long.parseLong(matcher.group(group + 1));
        String fraction = matcher.group(group + 2);
        long millis = 0L;
        if (fraction != null && !fraction.isEmpty()) {
            millis = switch (fraction.length()) {
                case 1 -> Long.parseLong(fraction) * 100L;
                case 2 -> Long.parseLong(fraction) * 10L;
                default -> Long.parseLong(fraction);
            };
        }
        return minutes * 60000L + seconds * 1000L + millis;
    }
}
