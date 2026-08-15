package dev.poc.api.input;

/**
 * Sémantique de déclenchement. Vanilla ne connaît que PRESS (via {@code wasPressed()}, un
 * compteur décrémenté) et HOLD (via {@code isPressed()}), ce qui oblige chaque mod à réimplémenter
 * son propre détecteur de double-tap ou de toggle — avec autant de bugs de désynchronisation.
 */
public enum ActivationMode {
    /** Front montant : un événement à l'appui. */
    PRESS,
    /** Front descendant : un événement au relâchement. */
    RELEASE,
    /** Niveau : événement continu tant que la touche est tenue (émis à chaque tick d'input). */
    HOLD,
    /** Bascule un état booléen à chaque appui. */
    TOGGLE,
    /** Deux appuis dans la fenêtre de double-tap (par défaut 250 ms). */
    DOUBLE_TAP,
    /** Maintien au-delà du seuil d'appui long (par défaut 400 ms), émis une seule fois. */
    LONG_PRESS
}
