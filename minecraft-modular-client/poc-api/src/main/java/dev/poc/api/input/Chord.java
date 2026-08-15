package dev.poc.api.input;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Combinaison de touches normalisée.
 *
 * <p><b>Décision de conception : on binde sur le SCANCODE, pas sur le keycode.</b> Le scancode est
 * la position physique de la touche ; le keycode dépend de la disposition du clavier. Sur AZERTY,
 * {@code GLFW_KEY_W} n'est pas la touche située à l'emplacement « avancer » — Minecraft vanilla a
 * traîné ce bug pendant des années. En bindant sur le scancode et en n'utilisant
 * {@code glfwGetKeyName(key, scancode)} <em>que pour l'affichage</em>, un profil de keybinds reste
 * cohérent quand l'utilisateur change de disposition en cours de partie.
 *
 * @param device       clavier ou souris (les deux partagent le même résolveur)
 * @param code         scancode (clavier) ou index de bouton (souris)
 * @param modifierMask OU binaire de {@link Modifier#bit()}
 */
public record Chord(Device device, int code, int modifierMask) implements Comparable<Chord> {

    public static final Chord NONE = new Chord(Device.KEYBOARD, -1, 0);

    public enum Device { KEYBOARD, MOUSE }

    public enum Modifier {
        CTRL(1), SHIFT(2), ALT(4), SUPER(8);

        private final int bit;
        Modifier(int bit) { this.bit = bit; }
        public int bit() { return bit; }

        public static int mask(Modifier... mods) {
            int m = 0;
            for (Modifier mod : mods) m |= mod.bit;
            return m;
        }
    }

    public static Chord key(int scancode, Modifier... mods) {
        return new Chord(Device.KEYBOARD, scancode, Modifier.mask(mods));
    }

    public static Chord mouse(int button, Modifier... mods) {
        return new Chord(Device.MOUSE, button, Modifier.mask(mods));
    }

    public boolean isBound() { return code >= 0; }

    /**
     * Nombre de modificateurs requis. C'est le critère de <b>spécificité</b> : entre un bind
     * {@code CTRL+K} et un bind {@code K}, l'appui sur Ctrl+K ne doit déclencher que le premier.
     */
    public int specificity() { return Integer.bitCount(modifierMask); }

    /** Vrai si les modificateurs de ce chord sont un sous-ensemble de ceux actuellement enfoncés. */
    public boolean satisfiedBy(int activeModifiers) {
        return (modifierMask & activeModifiers) == modifierMask;
    }

    /** Vrai si les deux chords peuvent être déclenchés par le même appui physique. */
    public boolean collidesWith(Chord other) {
        return device == other.device && code == other.code && modifierMask == other.modifierMask;
    }

    public Set<Modifier> modifiers() {
        var set = java.util.EnumSet.noneOf(Modifier.class);
        for (Modifier m : Modifier.values()) {
            if ((modifierMask & m.bit) != 0) set.add(m);
        }
        return set;
    }

    /** Tri du plus spécifique au moins spécifique — utilisé par le résolveur. */
    @Override
    public int compareTo(Chord o) {
        int c = Integer.compare(o.specificity(), specificity());
        if (c != 0) return c;
        c = Integer.compare(code, o.code);
        return c != 0 ? c : device.compareTo(o.device);
    }

    /**
     * Représentation stable pour la sérialisation du profil, ex. {@code "CTRL+SHIFT+key.31"}.
     * On ne persiste JAMAIS le libellé lisible : il change avec la disposition clavier.
     */
    public String serialize() {
        if (!isBound()) return "unbound";
        List<String> parts = new ArrayList<>();
        for (Modifier m : Modifier.values()) {
            if ((modifierMask & m.bit) != 0) parts.add(m.name());
        }
        parts.add((device == Device.MOUSE ? "mouse." : "key.") + code);
        return String.join("+", parts);
    }

    public static Chord deserialize(String s) {
        if (s == null || s.isBlank() || "unbound".equals(s)) return NONE;
        String[] parts = s.split("\\+");
        int mask = 0;
        Device device = Device.KEYBOARD;
        int code = -1;
        for (String p : parts) {
            if (p.startsWith("key.")) {
                device = Device.KEYBOARD;
                code = Integer.parseInt(p.substring(4));
            } else if (p.startsWith("mouse.")) {
                device = Device.MOUSE;
                code = Integer.parseInt(p.substring(6));
            } else {
                mask |= Modifier.valueOf(p.toUpperCase(java.util.Locale.ROOT)).bit();
            }
        }
        return new Chord(device, code, mask);
    }
}
