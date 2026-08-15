package dev.poc.api.input;

import java.util.Objects;

/**
 * Définition d'un raccourci.
 *
 * <p><b>Le champ {@code id} est la correction du bug historique dit « key.anything ».</b> Dans
 * Minecraft vanilla, un {@code KeyBinding} est identifié par sa clé de traduction
 * ({@code "key.forward"}, et par imitation {@code "key.anything"} chez beaucoup de mods). Deux
 * conséquences : (1) deux mods qui choisissent la même clé s'écrasent mutuellement dans
 * {@code KeyBinding.KEYS_BY_ID}, et le dernier chargé gagne ; (2) la map statique
 * {@code KEY_TO_BINDINGS} associe une touche physique à <em>un seul</em> binding, donc deux binds
 * sur la même touche font que l'un des deux ne reçoit jamais {@code wasPressed()}.
 *
 * <p>Ici : l'identité est {@code "<moduleId>:<action>"}, imposée et vérifiée à l'enregistrement
 * (collision = exception explicite au chargement, pas un échec silencieux à l'exécution), le
 * libellé affiché est un champ séparé et purement cosmétique, et le registre est une multimap
 * touche → liste de binds.
 *
 * @param id            identité unique {@code namespace:action}, jamais affichée
 * @param displayName   libellé UI (peut être dupliqué sans conséquence)
 * @param category      groupe d'affichage dans l'écran des contrôles
 * @param defaultChord  binding par défaut, écrasé par le profil utilisateur
 * @param mode          sémantique de déclenchement
 * @param context       où le bind est actif
 * @param priority      départage entre binds de spécificité égale (plus grand = servi en premier)
 * @param consuming     si true, stoppe la propagation vers les binds suivants et vers le jeu
 * @param allowRepeat   pour {@link ActivationMode#PRESS}, accepte l'auto-répétition de l'OS
 */
public record Keybind(
        String id,
        String displayName,
        String category,
        Chord defaultChord,
        ActivationMode mode,
        ActivationContext context,
        int priority,
        boolean consuming,
        boolean allowRepeat) {

    public Keybind {
        Objects.requireNonNull(id, "id");
        if (!id.matches("[a-z0-9][a-z0-9_-]*:[a-z0-9][a-z0-9_./-]*")) {
            throw new IllegalArgumentException(
                    "id de keybind invalide '" + id + "' — format attendu: <namespace>:<action>");
        }
        Objects.requireNonNull(defaultChord, "defaultChord");
        mode = mode == null ? ActivationMode.PRESS : mode;
        context = context == null ? ActivationContext.IN_GAME : context;
    }

    public String namespace() { return id.substring(0, id.indexOf(':')); }

    public static Builder builder(String id) { return new Builder(id); }

    public static final class Builder {
        private final String id;
        private String displayName;
        private String category = "general";
        private Chord defaultChord = Chord.NONE;
        private ActivationMode mode = ActivationMode.PRESS;
        private ActivationContext context = ActivationContext.IN_GAME;
        private int priority = 0;
        private boolean consuming = true;
        private boolean allowRepeat = false;

        private Builder(String id) {
            this.id = id;
            this.displayName = id.substring(id.indexOf(':') + 1);
        }

        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder category(String v) { this.category = v; return this; }
        public Builder defaultChord(Chord v) { this.defaultChord = v; return this; }
        public Builder mode(ActivationMode v) { this.mode = v; return this; }
        public Builder context(ActivationContext v) { this.context = v; return this; }
        public Builder priority(int v) { this.priority = v; return this; }
        public Builder passthrough() { this.consuming = false; return this; }
        public Builder allowRepeat() { this.allowRepeat = true; return this; }

        public Keybind build() {
            return new Keybind(id, displayName, category, defaultChord, mode, context,
                    priority, consuming, allowRepeat);
        }
    }
}
