package dev.poc.musichud;

import java.util.ArrayList;
import java.util.List;

/**
 * État de la musique, tel que renvoyé par le lanceur.
 *
 * <p>Immuable et sans dépendance à Minecraft : le fil réseau en produit des instances, le fil de
 * rendu les lit. Sans immuabilité, un champ pourrait changer au milieu d'une frame et donner un
 * affichage incohérent — un titre déjà remplacé avec l'ancien artiste, par exemple.
 */
public final class MusicState {

    public static final MusicState OFFLINE = new MusicState(false, "", "", "", false, 0, 0);

    public final boolean connected;
    public final String title;
    public final String artist;
    public final String album;
    public final boolean playing;
    public final long progressMs;
    public final long durationMs;

    public MusicState(boolean connected, String title, String artist, String album,
                      boolean playing, long progressMs, long durationMs) {
        this.connected = connected;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
        this.playing = playing;
        this.progressMs = progressMs;
        this.durationMs = durationMs;
    }

    public boolean hasTrack() {
        return !title.isEmpty() || !artist.isEmpty();
    }

    /** Avancement de 0 à 1, borné : une durée absente ne doit pas produire une barre infinie. */
    public float progress() {
        if (durationMs <= 0) return 0f;
        return Math.max(0f, Math.min(1f, (float) progressMs / durationMs));
    }

    public String formattedPosition() {
        return formatMillis(progressMs) + " / " + formatMillis(durationMs);
    }

    public static String formatMillis(long millis) {
        long seconds = millis / 1000L;
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    @Override
    public String toString() {
        if (!connected) return "hors ligne";
        if (!hasTrack()) return "rien en lecture";
        return (playing ? "▶ " : "|| ") + artist + " — " + title;
    }

    /** Une playlist de l'utilisateur. */
    public static final class Playlist {
        public final String name;
        public final String uri;
        public final int trackCount;

        public Playlist(String name, String uri, int trackCount) {
            this.name = name;
            this.uri = uri;
            this.trackCount = trackCount;
        }

        @Override
        public String toString() {
            return name + " (" + trackCount + ")";
        }
    }

    public static List<Playlist> emptyPlaylists() {
        return new ArrayList<>();
    }
}
