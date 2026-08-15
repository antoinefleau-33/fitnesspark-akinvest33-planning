package dev.poc.api.input;

/**
 * Événement livré au handler d'un keybind.
 *
 * @param bind       le keybind déclenché
 * @param mode       le mode qui a produit ce déclenchement
 * @param chord      le chord physique effectivement pressé
 * @param toggleState pour {@link ActivationMode#TOGGLE}, le nouvel état
 * @param heldNanos  durée de maintien au moment de l'événement
 */
public record KeyEvent(
        Keybind bind,
        ActivationMode mode,
        Chord chord,
        boolean toggleState,
        long heldNanos,
        Consumption consumption) {

    /** Mutable, partagé entre handlers d'un même appui : permet à un handler de stopper la chaîne. */
    public static final class Consumption {
        private boolean consumed;
        public boolean isConsumed() { return consumed; }
        /** Bloque les binds de priorité inférieure ET la propagation vers le jeu. */
        public void consume() { this.consumed = true; }
    }

    public void consume() { consumption.consume(); }
}
