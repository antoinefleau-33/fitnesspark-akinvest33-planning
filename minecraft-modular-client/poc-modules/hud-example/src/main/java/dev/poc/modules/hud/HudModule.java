package dev.poc.modules.hud;

import dev.poc.api.event.GameEvents;
import dev.poc.api.input.ActivationContext;
import dev.poc.api.input.ActivationMode;
import dev.poc.api.input.Chord;
import dev.poc.api.input.Keybind;
import dev.poc.api.module.ClientModule;
import dev.poc.api.module.GameBridge;
import dev.poc.api.module.ModuleContext;

/**
 * Module d'exemple. Le point à retenir : <b>aucun import de classe Minecraft</b>. Ce jar unique
 * fonctionne sur 1.8.9 comme sur 1.20.1, parce qu'il ne parle qu'à {@link GameBridge}. C'est
 * l'adaptateur de version, côté client, qui absorbe les différences de structure du jeu.
 */
public final class HudModule implements ClientModule {

    private static final int SCANCODE_H = 35;

    private boolean visible = true;
    private int frames;
    private long lastFpsSampleNanos = System.nanoTime();
    private int fps;

    @Override
    public void onLoad(ModuleContext ctx) {
        // Le namespace du keybind DOIT être l'id du module : le contexte le vérifie et refuse
        // l'enregistrement sinon. Impossible de squatter le namespace d'un autre module.
        ctx.keybinds().register(
                Keybind.builder("hud-example:toggle")
                        .displayName("Afficher/masquer le HUD")
                        .category("hud")
                        .defaultChord(Chord.key(SCANCODE_H, Chord.Modifier.SHIFT))
                        .mode(ActivationMode.TOGGLE)
                        .context(ActivationContext.IN_GAME)
                        .build(),
                event -> {
                    visible = event.toggleState();
                    ctx.log(System.Logger.Level.INFO, "HUD {0}", visible ? "affiché" : "masqué");
                });

        // Le handler est enregistré via ctx.events() : il sera désabonné automatiquement au
        // déchargement du module, y compris si onUnload() n'est jamais appelé (module planté).
        ctx.events().subscribe(GameEvents.RenderHud.class, this::onRender);

        ctx.events().subscribe(GameEvents.SessionStarted.class, e ->
                ctx.log(System.Logger.Level.INFO, "session {0} démarrée", e.game.versionId()));
    }

    private void onRender(GameEvents.RenderHud event) {
        countFrame();
        if (!visible) return;

        GameBridge.Hud hud = event.hud;
        hud.roundedRect(8, 8, 190, 62, 6, 0xB0101114);

        // Les coordonnées viennent du pont, pas d'un accès direct au joueur : la mécanique reste
        // identique quelle que soit la version chargée.
        hud.text(18, 18, "FPS %d".formatted(fps), 0xFFE6E8EC);
    }

    /** Le module est rechargé à chaque changement de version : l'état de fenêtre FPS repart à zéro. */
    private void countFrame() {
        frames++;
        long now = System.nanoTime();
        if (now - lastFpsSampleNanos >= 1_000_000_000L) {
            fps = frames;
            frames = 0;
            lastFpsSampleNanos = now;
        }
    }

    @Override
    public void onUnload(ModuleContext ctx) {
        // Rien à faire : keybind et abonnements sont révoqués par la fermeture du scope.
        // Ce vide est le signe que le modèle de scope fonctionne — un module ne peut pas
        // oublier un nettoyage qu'il n'a pas à écrire.
        visible = false;
    }
}
