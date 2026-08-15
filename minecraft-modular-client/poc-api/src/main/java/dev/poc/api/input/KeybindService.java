package dev.poc.api.input;

import java.util.List;
import java.util.function.Consumer;

/** Façade d'enregistrement exposée aux modules. */
public interface KeybindService {

    /**
     * @throws DuplicateKeybindException si l'id est déjà pris (échec bruyant au chargement plutôt
     *         qu'un bind silencieusement mort à l'exécution).
     */
    Handle register(Keybind bind, Consumer<KeyEvent> handler);

    /** Conflits actuels : même chord, contextes qui se recouvrent. Alimente l'écran des contrôles. */
    List<Conflict> conflicts();

    /** Rebinding à chaud depuis l'UI ; persiste dans le profil utilisateur. */
    void rebind(String keybindId, Chord chord);

    /** Chord effectif (profil utilisateur, ou défaut si non personnalisé). */
    Chord chordOf(String keybindId);

    /** État courant pour les binds TOGGLE / HOLD, sans passer par un handler. */
    boolean isActive(String keybindId);

    List<Keybind> registered();

    interface Handle extends AutoCloseable {
        Keybind bind();
        @Override void close();
    }

    /**
     * @param severity {@code HARD} = même chord ET contextes recouvrants ET les deux consommants
     *                 (un seul se déclenchera) ; {@code SOFT} = coexistence possible, les deux se
     *                 déclencheront (informatif).
     */
    record Conflict(Chord chord, List<String> keybindIds, Severity severity) {
        public enum Severity { HARD, SOFT }
    }

    class DuplicateKeybindException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public DuplicateKeybindException(String message) { super(message); }
    }
}
