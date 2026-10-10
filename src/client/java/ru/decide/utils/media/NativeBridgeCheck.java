// Проверка нативного моста Decide: грузит OptMedia.dll и DecideVocal.dll из
// resources, опрашивает медиа-сессию и печатает всё, что пришло.
//
// Нужна, потому что клиент запускается в игре, а тут видно «сырое» поведение DLL:
// если позиция или isPlaying приходят нулями — причина рассинхрона найдена тут,
// а не в логике LyricsText.
package ru.decide.utils.media;

public final class NativeBridgeCheck {

    public static void main(String[] args) throws Exception {
        System.out.println("os.name = " + System.getProperty("os.name"));

        System.out.println("OptMedia.available()  = " + MediaNative.available());
        System.out.println("MediaNative.init()    = " + MediaNative.init());
        System.out.println("vocal loaded          = " + MediaNative.vocalAvailable());

        System.out.println("\n--- 5 секунд опроса медиа-сессии ---");
        for (int i = 0; i < 25; i++) {
            Thread.sleep(200);
            System.out.printf(
                    "title='%s' artist='%s' appId='%s' playing=%s duration=%dms position=%dms rev=%d%n",
                    MediaNative.title(), MediaNative.artist(), MediaNative.appId(),
                    MediaNative.isPlaying(), MediaNative.durationMillis(),
                    MediaNative.positionMillis(), MediaNative.revision());
        }

        System.out.println("\n--- процесс для детектора вокала ---");
        System.out.println("vocalProcess() = " + MediaPlayer.vocalProcess());

        System.out.println("\n--- детектор вокала ---");
        String process = MediaPlayer.vocalProcess();
        boolean started = MediaNative.startVocalDetect(process);
        System.out.println("startVocalDetect('" + process + "') = " + started);
        for (int i = 0; i < 15; i++) {
            Thread.sleep(200);
            System.out.printf("level=%.4f peak=%.4f frames=%d flags=0x%x%n",
                    MediaNative.vocalLevel(), MediaNative.vocalPeak(),
                    MediaNative.vocalFrames(), MediaNative.vocalFlags());
        }
        MediaNative.stopVocalDetect();
        System.out.println("после stop: level = " + MediaNative.vocalLevel() + " (ожидается -1)");

        System.out.println("\n--- поиск текста (lrclib) ---");
        // title|artist|durationSeconds — длительность обязательна, без неё сервис
        // отдаёт служебные записи вроде [00:00.00] *Rickrolling*
        String[] probe = {
                "Never Gonna Give You Up|Rick Astley|213",
                "Smells Like Teen Spirit|Nirvana|301",
                "Smells Like Teen Spirit|Nirvana|0",
                "Yellow|Coldplay|269",
                "Кино — Группа крови|Кино|331",
                "Не существует такой песни на свете|Никто|200",
        };
        for (String probeLine : probe) {
            String[] parts = probeLine.split("\\|", 3);
            String title = parts[0];
            String artist = parts[1];
            int duration = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
            long startedAt = System.currentTimeMillis();
            String lrc = MediaNative.fetchLyrics(title, artist, "", duration);
            long took = System.currentTimeMillis() - startedAt;
            System.out.println("'" + title + "' (" + duration + "s) -> "
                    + (lrc == null ? "null" : lrc.length() + " chars") + " за " + took + "ms");
            if (lrc != null) {
                Lyrics parsed = LyricsParser.parse(lrc);
                System.out.println("   synced=" + parsed.synced() + " lines=" + parsed.lines().size()
                        + (parsed.lines().isEmpty() ? "" : " first='" + parsed.lines().get(0).text() + "'"));
            }
        }

        MediaNative.shutdown();
    }
}
