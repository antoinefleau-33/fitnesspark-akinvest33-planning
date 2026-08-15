package dev.poc.api.module;

/**
 * Cycle de vie d'un module. Volontairement minimal : tout ce dont un module a besoin
 * transite par {@link ModuleContext}, jamais par des singletons statiques — c'est ce qui
 * permet de décharger proprement un module (et un classloader) sans fuite mémoire.
 *
 * <p>Contrat d'implémentation : constructeur public sans argument. L'instanciation est faite
 * par le {@code ModuleLoader} via {@code ServiceLoader} ou par nom de classe déclaré dans
 * {@code module.json}.
 */
public interface ClientModule {

    /**
     * Appelé une fois, après résolution des dépendances, dans l'ordre topologique.
     * Le module doit y déclarer ses keybinds, ses écrans, ses services. Il ne doit PAS
     * supposer que les autres modules sont déjà démarrés.
     */
    void onLoad(ModuleContext ctx) throws Exception;

    /** Activation (module coché dans l'UI, ou activation au démarrage). Peut être appelé N fois. */
    default void onEnable(ModuleContext ctx) throws Exception {}

    /** Désactivation. Doit relâcher tout ce qui a été acquis dans {@link #onEnable}. */
    default void onDisable(ModuleContext ctx) throws Exception {}

    /**
     * Déchargement définitif. Après retour, aucune référence forte vers du code du module
     * ne doit subsister : threads arrêtés, listeners retirés, timers annulés.
     * Le {@code ModuleContext} révoque automatiquement tout ce qui a été enregistré via lui.
     */
    default void onUnload(ModuleContext ctx) throws Exception {}
}
