package dev.poc.api.game;

import dev.poc.api.module.GameBridge;

/**
 * Point d'entrée d'une version de Minecraft. Une implémentation par famille de version, chargée
 * <b>dans le classloader isolé de la version</b> et découverte via {@code ServiceLoader}.
 *
 * <p>C'est la seule interface que le shell appelle à travers la frontière d'isolation. Elle est
 * délibérément étroite : plus la surface est petite, moins il y a d'occasions de laisser fuir une
 * référence du monde isolé vers le shell (et donc de rendre le classloader non collectable).
 *
 * <h2>Contrat de cycle de vie</h2>
 * <pre>
 *   boot(env)  →  [ tick() / render() en boucle sur le thread principal ]  →  shutdown()
 * </pre>
 * {@code boot} et {@code shutdown} sont appelés sur le thread principal, celui qui possède le
 * contexte OpenGL. Après retour de {@code shutdown}, l'adaptateur doit garantir : aucun thread
 * vivant, aucun objet GL alloué, aucun hook d'arrêt enregistré.
 */
public interface GameAdapter {

    /** Ex. {@code "1.20.1"}. */
    String versionId();

    /** Ex. {@code "1.20"} — plusieurs versions peuvent partager un adaptateur. */
    String family();

    /**
     * Démarre le jeu <b>dans la fenêtre déjà créée par le shell</b>. L'adaptateur ne crée jamais
     * sa propre fenêtre ni son propre contexte GL : c'est ce qui permet de changer de version sans
     * que l'écran ne clignote et sans recharger les natifs (voir docs/03).
     */
    GameBridge boot(GameEnvironment env) throws Exception;

    /** Un tick logique. */
    void tick();

    /** Une frame. {@code partialTicks} pour l'interpolation. */
    void render(float partialTicks);

    /**
     * Arrêt. Doit être idempotent et ne jamais lancer : le switcher ne peut pas se permettre
     * d'échouer à mi-chemin, il resterait sans session valide.
     */
    void shutdown();
}
