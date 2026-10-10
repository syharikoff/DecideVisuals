package ru.decide.utils.media;

import com.sun.jna.Library;

/**
 * Экспорты OptMedia.dll — доступ к системным медиа-сессиям Windows
 * ({@code GlobalSystemMediaTransportControlsSessionManager}).
 *
 * DLL кладётся в {@code assets/client/natives/}, распаковывается во временный файл
 * и грузится через JNA, поэтому клиенту не нужен установленный плеер или PATH.
 *
 * На x64 соглашение вызова не различается, хватает {@link Library} без jna-platform.
 */
public interface OptMediaLibrary extends Library {

    boolean GetCurrentMediaInfo(OptMediaArtist artist);

    void FreeMediaInfo(OptMediaArtist artist);

    boolean Play();

    boolean Pause();

    boolean TogglePlayPause();

    boolean SkipNext();

    boolean SkipPrevious();

    void SeekToMs(long ms);

    float GetVolume();

    boolean SetVolume(float vol);

    void Deinitialize();
}
