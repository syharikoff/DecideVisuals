package ru.white.utils.media;

import com.sun.jna.Library;

/**
 * Экспорты NightixVocal.dll — детектор вокала в реальном времени.
 *
 * DLL берёт звук с выхода системы (WASAPI loopback), выделяет полосу 180–4200 Гц
 * и отдаёт сглаженную энергию. Исходник — {@code native/lyrics_vocal.cpp}.
 */
public interface NightixVocalLibrary extends Library {

    /** Запустить захват. {@code processName} — exe источника, который надо заглушить
     *  (чтобы тишина в плеере давала тишину вокала), может быть null. */
    int VocalStart(String processName);

    void VocalStop();

    void VocalDeinitialize();

    /** Энергия вокала 0..1, либо -1 если захват не запущен. */
    float VocalLevel();

    /** Мгновенный пик огибающей, 0 если захват не запущен. */
    float VocalPeak();

    /** Сколько всего сэмплов принято, -1 если захват не запущен. */
    int VocalFrames();

    /** Бит 0 — поток захвата жив, 1 — эндпоинт открыт, 2 — сигнала нет. */
    int VocalFlags();

    int VocalApiVersion();

    int FLAG_RUNNING  = 1;
    int FLAG_ENDPOINT = 2;
    int FLAG_SILENT   = 4;
}
