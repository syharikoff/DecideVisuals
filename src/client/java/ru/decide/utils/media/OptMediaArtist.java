package ru.decide.utils.media;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Структура, которую OptMedia.dll заполняет сведениями о текущем треке. */
public class OptMediaArtist extends Structure {

    public byte[] title = new byte[512];
    public byte[] artist = new byte[512];
    public byte[] sourceApp = new byte[256];
    public Pointer albumArtPng;
    public int albumArtSize;
    public byte isPlaying;
    public long durationMs;
    public long positionMs;

    public OptMediaArtist() {
        super(1);
    }

    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("title", "artist", "sourceApp", "albumArtPng",
                "albumArtSize", "isPlaying", "durationMs", "positionMs");
    }

    public String getTitle() {
        return read(this.title);
    }

    public String getArtist() {
        return read(this.artist);
    }

    public String getSource() {
        return read(this.sourceApp);
    }

    public boolean isPlaying() {
        return this.isPlaying != 0;
    }

    public byte[] getAlbumArt() {
        if (this.albumArtPng != null && this.albumArtSize > 0) {
            try {
                return this.albumArtPng.getByteArray(0L, this.albumArtSize);
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static String read(byte[] bytes) {
        int len = 0;
        while (len < bytes.length && bytes[len] != 0) {
            len++;
        }
        return new String(bytes, 0, len, StandardCharsets.UTF_8).trim();
    }
}
