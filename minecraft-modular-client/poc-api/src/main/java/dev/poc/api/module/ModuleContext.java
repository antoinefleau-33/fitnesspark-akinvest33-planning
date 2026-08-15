package dev.poc.api.module;

import dev.poc.api.event.EventBus;
import dev.poc.api.input.KeybindService;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Capacité passée au module. C'est un <em>scope</em> : tout ce qui est enregistré via ce contexte
 * est automatiquement révoqué au déchargement du module. C'est le mécanisme central qui rend le
 * hot-unload fiable — aucun registre global ne conserve de référence vers le module.
 */
public interface ModuleContext extends AutoCloseable {

    ModuleMetadata metadata();

    /** Bus d'événements filtré : les abonnements sont désenregistrés au close(). */
    EventBus events();

    /** Enregistrement de raccourcis. Les binds sont retirés au close(). */
    KeybindService keybinds();

    /** Répertoire de configuration dédié, créé à la demande : {@code config/<module-id>/}. */
    Path configDir();

    /**
     * Pont vers le jeu pour la session courante. {@code empty()} quand aucune version n'est
     * démarrée (menu principal du client, ou bascule de version en cours).
     *
     * <p>Ne JAMAIS mettre en cache le résultat dans un champ : la référence devient invalide à
     * chaque changement de version. Toujours re-demander au contexte.
     */
    Optional<GameBridge> game();

    /** Journalisation préfixée par l'id du module. */
    void log(System.Logger.Level level, String message, Object... args);

    /** Enregistre une action de nettoyage exécutée au déchargement (ordre LIFO). */
    void onClose(AutoCloseable cleanup);

    @Override
    void close();
}
