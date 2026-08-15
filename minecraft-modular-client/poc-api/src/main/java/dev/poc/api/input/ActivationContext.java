package dev.poc.api.input;

import java.util.EnumSet;
import java.util.Set;

/**
 * Contexte d'activation. Équivalent conceptuel du {@code KeyConflictContext} de Forge, mais
 * exploité aussi pour la <b>détection de collision à l'enregistrement</b> : deux binds sur le même
 * chord dans des contextes disjoints ne sont PAS un conflit et peuvent coexister.
 */
public enum ActivationContext {

    /** Toujours actif, y compris pendant la saisie de texte. À réserver aux binds système. */
    UNIVERSAL(Scope.GAMEPLAY, Scope.SCREEN, Scope.TEXT_INPUT),

    /** En jeu, aucun écran ouvert. Le cas par défaut. */
    IN_GAME(Scope.GAMEPLAY),

    /** Un écran (inventaire, menu client) est ouvert, hors saisie de texte. */
    IN_SCREEN(Scope.SCREEN),

    /** En jeu ou dans un écran, mais jamais pendant la saisie de texte. */
    IN_GAME_OR_SCREEN(Scope.GAMEPLAY, Scope.SCREEN);

    public enum Scope { GAMEPLAY, SCREEN, TEXT_INPUT }

    private final Set<Scope> scopes;

    ActivationContext(Scope... scopes) {
        this.scopes = scopes.length == 0
                ? EnumSet.noneOf(Scope.class)
                : EnumSet.copyOf(java.util.Arrays.asList(scopes));
    }

    public boolean activeIn(Scope current) { return scopes.contains(current); }

    /** Deux contextes peuvent-ils être actifs simultanément ? Si non, pas de conflit possible. */
    public boolean overlaps(ActivationContext other) {
        return scopes.stream().anyMatch(other.scopes::contains);
    }
}
