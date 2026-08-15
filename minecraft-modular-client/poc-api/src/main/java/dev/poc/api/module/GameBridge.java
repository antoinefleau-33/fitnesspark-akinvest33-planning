package dev.poc.api.module;

/**
 * Abstraction du jeu, <b>indépendante de la version</b>. C'est la pièce maîtresse qui permet à un
 * module unique de fonctionner de 1.8.9 à 1.20.1 : un module ne référence jamais une classe de
 * Minecraft, il ne parle qu'à cette interface. L'adaptateur spécifique à la version (voir
 * {@code poc-adapters/}) est le seul code à réécrire quand une nouvelle version sort.
 *
 * <p>Cette interface est chargée par le classloader racine, donc son identité de classe est
 * stable de part et d'autre de la frontière d'isolation. Corollaire : elle ne doit exposer que
 * des types du JDK et des types de {@code poc-api}.
 */
public interface GameBridge {

    /** Identifiant de version au sens du manifeste Mojang, ex. {@code "1.20.1"}. */
    String versionId();

    /** Famille d'adaptateur, ex. {@code "1.20"} — utile pour les modules qui doivent dégrader. */
    String versionFamily();

    LocalPlayer player();

    World world();

    Hud hud();

    /** Vrai si un écran (inventaire, menu, chat) capture actuellement la saisie. */
    boolean isScreenOpen();

    /** Vrai si un champ de texte a le focus — utilisé par le pipeline d'input. */
    boolean isTextInputFocused();

    interface LocalPlayer {
        double x();
        double y();
        double z();
        float yaw();
        float pitch();
        float health();
        boolean onGround();
        String name();
    }

    interface World {
        String dimensionId();
        long dayTime();
        int playerCount();
        /** Nom du serveur, ou {@code null} en solo. */
        String serverAddress();
    }

    /** Dessin dans l'espace écran du jeu, délégué au renderer du shell. */
    interface Hud {
        int width();
        int height();
        void text(float x, float y, String text, int argb);
        void rect(float x, float y, float w, float h, int argb);
        void roundedRect(float x, float y, float w, float h, float radius, int argb);
    }
}
