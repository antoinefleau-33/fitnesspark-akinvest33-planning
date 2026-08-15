package dev.poc.musichud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client du serveur local exposé par le lanceur.
 *
 * <p>Aucune dépendance à Minecraft : cette classe se compile et se teste avec un simple
 * {@code javac}, ce qui permet de valider le pont sans lancer le jeu — c'est justement le point
 * d'intégration le plus fragile.
 *
 * <h2>Pourquoi passer par le lanceur</h2>
 * Le mod ne peut pas parler à Spotify directement : il n'a ni les jetons d'authentification, ni
 * la possibilité d'ouvrir un navigateur pour l'autorisation. Le lanceur, lui, a déjà tout ça.
 *
 * <h2>Le fil réseau</h2>
 * Toutes les requêtes partent d'un fil dédié. Un appel HTTP depuis le fil de rendu gèlerait
 * l'image à chaque interrogation — et à trois secondes d'intervalle, le jeu serait injouable.
 * Le fil de rendu ne fait que lire une référence atomique, opération qui ne bloque jamais.
 */
public final class SpotifyBridge {

    private static final int TIMEOUT_MS = 2500;

    private final Path tokenFile;
    private final AtomicReference<MusicState> state = new AtomicReference<>(MusicState.OFFLINE);
    private final AtomicReference<List<MusicState.Playlist>> playlists =
            new AtomicReference<>(MusicState.emptyPlaylists());

    private ScheduledExecutorService scheduler;
    private volatile String token = "";
    private volatile int port = 0;
    private volatile String lastError = "";

    public SpotifyBridge(Path tokenFile) {
        this.tokenFile = tokenFile;
    }

    /** État le plus récent. Appelable depuis le fil de rendu : lecture atomique, sans verrou. */
    public MusicState state() {
        return state.get();
    }

    public List<MusicState.Playlist> playlists() {
        return playlists.get();
    }

    public String lastError() {
        return lastError;
    }

    public void start(long periodSeconds) {
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "musichud-bridge");
            // Fil démon : sans ça, il empêcherait la JVM de s'arrêter quand on quitte le jeu.
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::poll, 0, periodSeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    // -- Interrogation ------------------------------------------------------------------------

    private void poll() {
        try {
            if (!readTokenFile()) {
                state.set(MusicState.OFFLINE);
                return;
            }
            Map<String, Object> body = request("GET", "/status", null);
            state.set(new MusicState(
                    true,
                    MiniJson.str(body, "title"),
                    MiniJson.str(body, "artist"),
                    MiniJson.str(body, "album"),
                    MiniJson.bool(body, "playing"),
                    MiniJson.num(body, "progress_ms"),
                    MiniJson.num(body, "duration_ms")));
            lastError = "";
        } catch (Exception e) {
            // Le lanceur peut être fermé, ou Spotify absent : on repasse hors ligne sans bruit.
            // Une exception qui remonterait ici tuerait le planificateur et le pont resterait
            // muet pour le reste de la session.
            state.set(MusicState.OFFLINE);
            lastError = String.valueOf(e.getMessage());
        }
    }

    /**
     * Relit le fichier écrit par le lanceur à chaque cycle.
     *
     * <p>Le port et le jeton changent à chaque démarrage du lanceur : les mettre en cache une
     * fois pour toutes ferait échouer le mod dès que l'utilisateur relance le lanceur sans
     * relancer le jeu.
     */
    private boolean readTokenFile() {
        try {
            if (!Files.isReadable(tokenFile)) return false;
            Map<String, Object> data = MiniJson.parseObject(
                    new String(Files.readAllBytes(tokenFile), StandardCharsets.UTF_8));
            port = (int) MiniJson.num(data, "port");
            token = MiniJson.str(data, "token");
            return port > 0 && !token.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, Object> request(String method, String path, String jsonBody)
            throws IOException {
        // URI puis toURL() : le constructeur URL(String) est deprecie depuis Java 20.
        URL url = java.net.URI.create("http://127.0.0.1:" + port + path).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("X-Token", token);
        conn.setRequestProperty("Accept", "application/json");

        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream out = conn.getOutputStream()) {
                out.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int status = conn.getResponseCode();
        // En erreur, le corps est sur le flux d'erreur : le lire donne le message du lanceur
        // (« compte Premium requis », par exemple) au lieu d'un code nu.
        InputStream stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
        String raw = stream == null ? "" : readAll(stream);

        if (status >= 400) {
            String message = raw.isEmpty() ? ("HTTP " + status)
                    : MiniJson.str(MiniJson.parseObject(raw), "error");
            throw new IOException(message.isEmpty() ? ("HTTP " + status) : message);
        }
        return raw.isEmpty() ? MiniJson.parseObject("{}") : MiniJson.parseObject(raw);
    }

    private static String readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[4096];
        int read;
        while ((read = in.read(chunk)) > 0) buffer.write(chunk, 0, read);
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    // -- Commandes ----------------------------------------------------------------------------

    /** Les commandes partent sur le fil réseau : jamais d'appel HTTP depuis le fil de rendu. */
    private void send(String path, String jsonBody, Runnable after) {
        Thread thread = new Thread(() -> {
            try {
                request("POST", path, jsonBody);
                poll();
                if (after != null) after.run();
            } catch (Exception e) {
                lastError = String.valueOf(e.getMessage());
            }
        }, "musichud-command");
        thread.setDaemon(true);
        thread.start();
    }

    public void playPause() { send("/playpause", null, null); }

    public void next() { send("/next", null, null); }

    public void previous() { send("/previous", null, null); }

    public void playPlaylist(String uri) {
        send("/play", "{\"uri\":\"" + escape(uri) + "\"}", null);
    }

    public void refreshPlaylists() {
        Thread thread = new Thread(() -> {
            try {
                if (!readTokenFile()) return;
                Map<String, Object> body = request("GET", "/playlists", null);
                List<MusicState.Playlist> found = MusicState.emptyPlaylists();
                for (Map<String, Object> item : MiniJson.objects(body, "playlists")) {
                    found.add(new MusicState.Playlist(
                            MiniJson.str(item, "name"),
                            MiniJson.str(item, "uri"),
                            (int) MiniJson.num(item, "tracks")));
                }
                playlists.set(found);
            } catch (Exception e) {
                lastError = String.valueOf(e.getMessage());
            }
        }, "musichud-playlists");
        thread.setDaemon(true);
        thread.start();
    }

    private static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
