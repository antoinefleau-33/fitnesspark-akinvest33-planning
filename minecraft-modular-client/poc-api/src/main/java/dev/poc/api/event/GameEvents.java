package dev.poc.api.event;

import dev.poc.api.module.GameBridge;

/** Événements de haut niveau, indépendants de la version de Minecraft. */
public final class GameEvents {

    private GameEvents() {}

    /** Une session de jeu est prête (fenêtre + contexte GL + monde chargeable). */
    public static final class SessionStarted extends Event.Abstract {
        public final GameBridge game;
        public SessionStarted(GameBridge game) { this.game = game; }
    }

    /** La session va s'arrêter. Dernier moment pour libérer des ressources GL de la session. */
    public static final class SessionStopping extends Event.Abstract {
        public final GameBridge game;
        public SessionStopping(GameBridge game) { this.game = game; }
    }

    /** Tick logique du client (20 Hz côté vanilla). */
    public static final class ClientTick extends Event.Abstract {
        public final long tick;
        public ClientTick(long tick) { this.tick = tick; }
    }

    /**
     * Émis après le rendu du terrain et des entités, avant le HUD — l'équivalent version-agnostique
     * de {@code RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS} (Forge) ou de
     * {@code WorldRenderEvents.LAST} (Fabric).
     *
     * <p>Ce point d'accroche est choisi délibérément : plus tôt (avant les blocs translucides), les
     * boîtes seraient repeintes par l'eau et le verre ; plus tard (après le HUD), la matrice de
     * projection du monde a déjà été remplacée par la projection orthographique de l'interface.
     */
    public static final class RenderWorld extends Event.Abstract {
        public final dev.poc.api.render.WorldRenderer renderer;
        public final GameBridge.Camera camera;
        public final float partialTicks;

        public RenderWorld(dev.poc.api.render.WorldRenderer renderer,
                           GameBridge.Camera camera, float partialTicks) {
            this.renderer = renderer;
            this.camera = camera;
            this.partialTicks = partialTicks;
        }
    }

    /** Frame de rendu ; {@code partialTicks} pour l'interpolation. */
    public static final class RenderHud extends Event.Abstract {
        public final GameBridge.Hud hud;
        public final float partialTicks;
        public RenderHud(GameBridge.Hud hud, float partialTicks) {
            this.hud = hud;
            this.partialTicks = partialTicks;
        }
    }
}
