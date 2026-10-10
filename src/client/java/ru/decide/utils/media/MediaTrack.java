package ru.decide.utils.media;

/** Снимок текущего трека: всё, что нужно, чтобы найти для него текст. */
public final class MediaTrack {

    public static final MediaTrack EMPTY = new MediaTrack("", "", "", "", 0, "", 0L);

    private final String title;
    private final String artist;
    private final String albumTitle;
    private final String albumArtist;
    private final int trackNumber;
    private final String appId;
    private final long durationMillis;

    public MediaTrack(String title, String artist, String albumTitle, String albumArtist,
                      int trackNumber, String appId, long durationMillis) {
        this.title = title;
        this.artist = artist;
        this.albumTitle = albumTitle;
        this.albumArtist = albumArtist;
        this.trackNumber = trackNumber;
        this.appId = appId;
        this.durationMillis = durationMillis;
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public String albumTitle() {
        return albumTitle;
    }

    public String albumArtist() {
        return albumArtist;
    }

    public int trackNumber() {
        return trackNumber;
    }

    /** Имя процесса/приложения-источника — по нему ищем аудио-сессию для детектора вокала. */
    public String appId() {
        return appId;
    }

    public long durationMillis() {
        return durationMillis;
    }

    public boolean isEmpty() {
        return title.isEmpty() && artist.isEmpty();
    }

    public int durationSeconds() {
        return (int) (durationMillis / 1000L);
    }

    public String display() {
        if (artist.isEmpty()) {
            return title;
        }
        return artist + " - " + title;
    }

    @Override
    public String toString() {
        return "MediaTrack(" + display() + " " + durationMillis + "ms app=" + appId + ")";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MediaTrack t)) return false;
        return trackNumber == t.trackNumber
                && durationMillis == t.durationMillis
                && title.equals(t.title)
                && artist.equals(t.artist)
                && albumTitle.equals(t.albumTitle)
                && albumArtist.equals(t.albumArtist)
                && appId.equals(t.appId);
    }

    @Override
    public int hashCode() {
        int result = title.hashCode();
        result = 31 * result + artist.hashCode();
        result = 31 * result + albumTitle.hashCode();
        result = 31 * result + albumArtist.hashCode();
        result = 31 * result + Integer.hashCode(trackNumber);
        result = 31 * result + appId.hashCode();
        result = 31 * result + Long.hashCode(durationMillis);
        return result;
    }
}
