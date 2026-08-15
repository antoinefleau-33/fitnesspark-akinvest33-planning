package dev.poc.api.game;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Ce que le shell fournit à une version au démarrage. Types JDK uniquement : cet objet traverse la
 * frontière des classloaders.
 */
public interface GameEnvironment {

    /** Handle GLFW de la fenêtre possédée par le shell. Le contexte GL est déjà courant. */
    long windowHandle();

    int framebufferWidth();

    int framebufferHeight();

    /** Racine du profil : {@code saves/}, {@code resourcepacks/}, {@code options.txt}. */
    Path gameDir();

    /** Store d'assets partagé entre versions, adressé par hash. */
    Path assetsRoot();

    /** Nom de l'index d'assets de cette version, ex. {@code "5"} pour 1.8.9, {@code "8"} pour 1.20.1. */
    String assetIndexName();

    /** Session de compte, ou vide en mode hors-ligne. */
    Optional<AccountSession> account();

    /**
     * Serveur à rejoindre automatiquement après le boot. Renseigné par le switcher quand
     * l'utilisateur change de version en étant connecté : le client se reconnecte tout seul.
     */
    Optional<String> autoConnectAddress();

    /** Arène de ressources GL de la session : tout ce qui y est déclaré est libéré à l'arrêt. */
    GlArena glArena();

    /**
     * Enregistre une ressource fermée lors de l'arrêt de la session, avant l'abandon du
     * classloader. À utiliser pour les executors, les timers et les pools de connexions.
     */
    void registerSessionResource(AutoCloseable resource);

    record AccountSession(String username, String uuid, String accessToken, String type) {}

    /**
     * Suivi explicite des objets OpenGL. Minecraft laisse traîner des textures et des FBO à
     * l'arrêt — sans importance quand le processus s'arrête juste après, fatal quand on enchaîne
     * dix changements de version dans la même JVM (la VRAM ne se libère jamais).
     */
    interface GlArena {
        int genTexture();
        int genBuffer();
        int genVertexArray();
        int genFramebuffer();
        int createProgram();
        /** Libère tout ce qui a été alloué via cette arène. Appelé par le switcher. */
        void freeAll();
        int liveObjectCount();
    }
}
