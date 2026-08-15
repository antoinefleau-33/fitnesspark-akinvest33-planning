package dev.poc.core.input;

import dev.poc.api.input.ActivationContext;
import dev.poc.api.input.ActivationMode;
import dev.poc.api.input.Chord;
import dev.poc.api.input.KeyEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pipeline d'entrée. Il se branche <b>en amont</b> du jeu : la couche UI installe les callbacks
 * GLFW et les redirige ici, puis ce pipeline décide de laisser passer ou non l'événement vers la
 * session Minecraft. C'est l'inverse de l'approche « mod » classique (qui sonde
 * {@code Keyboard.isKeyDown} pendant le tick, d'où les événements perdus quand la fenêtre est
 * inactive ou que deux mods lisent le même état).
 *
 * <p>Ne dépend pas de LWJGL : les codes sont des entiers opaques, ce qui rend la classe testable
 * en pur JUnit et réutilisable si le back-end fenêtre change.
 *
 * <h2>Bugs traités explicitement</h2>
 * <ul>
 *   <li><b>Touches fantômes (stuck keys)</b> — un écran s'ouvre pendant qu'une touche est tenue,
 *       le relâchement part vers l'écran et le bind reste « enfoncé » à vie. Corrigé par
 *       {@link #flushHeld} sur changement de scope et sur perte de focus.</li>
 *   <li><b>Désynchronisation des modificateurs</b> — Alt+Tab laisse Alt collé. Le masque est
 *       recalculé à partir de l'état physique suivi ici, pas du champ {@code mods} de GLFW.</li>
 *   <li><b>Auto-répétition de l'OS</b> — {@code GLFW_REPEAT} ne doit pas déclencher un bind PRESS
 *       sauf demande explicite ({@code allowRepeat}).</li>
 * </ul>
 */
public final class InputPipeline {

    /** Actions brutes, alignées sémantiquement sur GLFW mais sans dépendance à LWJGL. */
    public enum Action { RELEASE, PRESS, REPEAT }

    public interface ScopeProvider {
        ActivationContext.Scope currentScope();
    }

    /** Reçoit ce que le pipeline n'a pas consommé, pour transmission au jeu. */
    public interface Passthrough {
        void onKey(Chord.Device device, int code, int keycode, Action action, int mods);
    }

    private static final long DOUBLE_TAP_WINDOW_NANOS = 250_000_000L;
    private static final long LONG_PRESS_THRESHOLD_NANOS = 400_000_000L;

    private final KeybindRegistry registry;
    private final ScopeProvider scopeProvider;
    private Passthrough passthrough = (d, c, k, a, m) -> {};

    /** État physique réel, source de vérité pour le masque de modificateurs. */
    private final Set<Long> physicallyDown = new HashSet<>();
    private int activeModifiers;
    private ActivationContext.Scope lastScope;

    public InputPipeline(KeybindRegistry registry, ScopeProvider scopeProvider) {
        this.registry = registry;
        this.scopeProvider = scopeProvider;
        this.lastScope = scopeProvider.currentScope();
    }

    public void setPassthrough(Passthrough passthrough) {
        this.passthrough = passthrough == null ? (d, c, k, a, m) -> {} : passthrough;
    }

    // -- Entrées brutes ---------------------------------------------------------------------

    /**
     * @param scancode code physique, indépendant de la disposition (ce sur quoi on binde)
     * @param keycode  code logique GLFW, utilisé uniquement pour reconnaître les modificateurs
     *                 et pour l'affichage
     */
    public void onKey(int scancode, int keycode, Action action) {
        updateModifierState(keycode, action);
        dispatch(Chord.Device.KEYBOARD, scancode, keycode, action);
    }

    public void onMouseButton(int button, Action action) {
        dispatch(Chord.Device.MOUSE, button, button, action);
    }

    /** Perte de focus fenêtre : tout relâcher, sinon des touches restent collées. */
    public void onFocusLost() {
        flushHeld(true);
        physicallyDown.clear();
        activeModifiers = 0;
    }

    /**
     * À appeler une fois par frame. Émet les événements de niveau (HOLD) et les seuils temporels
     * (LONG_PRESS), et détecte les changements de scope.
     */
    public void tick() {
        ActivationContext.Scope scope = scopeProvider.currentScope();
        if (scope != lastScope) {
            // Un bind actif dans l'ancien scope mais pas dans le nouveau doit être relâché
            // proprement, sinon il reste « tenu » indéfiniment.
            flushHeld(false);
            lastScope = scope;
        }

        long now = System.nanoTime();
        for (KeybindRegistry.Entry e : registry.all()) {
            if (!e.down) continue;
            if (!e.bind.context().activeIn(scope)) continue;

            if (e.bind.mode() == ActivationMode.HOLD) {
                fire(e, ActivationMode.HOLD, now, new KeyEvent.Consumption());
            } else if (e.bind.mode() == ActivationMode.LONG_PRESS
                    && !e.longPressFired
                    && now - e.pressedAtNanos >= LONG_PRESS_THRESHOLD_NANOS) {
                e.longPressFired = true;
                fire(e, ActivationMode.LONG_PRESS, now, new KeyEvent.Consumption());
            }
        }
    }

    // -- Résolution -------------------------------------------------------------------------

    private void dispatch(Chord.Device device, int code, int keycode, Action action) {
        long physical = ((long) device.ordinal() << 32) | (code & 0xFFFFFFFFL);
        if (action == Action.PRESS) {
            physicallyDown.add(physical);
        } else if (action == Action.RELEASE) {
            physicallyDown.remove(physical);
        }

        ActivationContext.Scope scope = scopeProvider.currentScope();
        long now = System.nanoTime();
        KeyEvent.Consumption consumption = new KeyEvent.Consumption();

        // Le bucket est déjà trié : spécificité, puis priorité, puis ordre d'enregistrement.
        List<KeybindRegistry.Entry> candidates = registry.candidates(device, code);
        int servedSpecificity = -1;

        for (KeybindRegistry.Entry e : candidates) {
            if (!e.bind.context().activeIn(scope)) continue;
            if (!e.effectiveChord.satisfiedBy(activeModifiers)) continue;

            // Règle de spécificité : dès qu'un bind d'un niveau de spécificité a été servi, on
            // ignore les niveaux inférieurs. Ctrl+K ne doit pas déclencher aussi le bind K.
            int spec = e.effectiveChord.specificity();
            if (servedSpecificity >= 0 && spec < servedSpecificity) break;

            boolean fired = handle(e, action, now, consumption);
            if (fired) {
                servedSpecificity = spec;
                if (e.bind.consuming()) consumption.consume();
            }
            if (consumption.isConsumed()) break;
        }

        if (!consumption.isConsumed()) {
            passthrough.onKey(device, code, keycode, action, activeModifiers);
        }
    }

    /** @return true si l'entrée a effectivement produit un déclenchement. */
    private boolean handle(KeybindRegistry.Entry e, Action action, long now,
                           KeyEvent.Consumption consumption) {
        ActivationMode mode = e.bind.mode();

        if (action == Action.REPEAT) {
            // L'auto-répétition de l'OS n'est pas un nouvel appui.
            if (mode == ActivationMode.PRESS && e.bind.allowRepeat()) {
                fire(e, ActivationMode.PRESS, now, consumption);
                return true;
            }
            return false;
        }

        if (action == Action.PRESS) {
            if (e.down) return false;   // garde-fou contre les doubles PRESS sans RELEASE
            e.down = true;
            e.pressedAtNanos = now;
            e.longPressFired = false;

            switch (mode) {
                case PRESS -> { fire(e, ActivationMode.PRESS, now, consumption); return true; }
                case TOGGLE -> {
                    e.toggled = !e.toggled;
                    fire(e, ActivationMode.TOGGLE, now, consumption);
                    return true;
                }
                case DOUBLE_TAP -> {
                    boolean isSecond = now - e.lastPressNanos <= DOUBLE_TAP_WINDOW_NANOS;
                    e.lastPressNanos = isSecond ? 0L : now;   // évite le triple-tap en cascade
                    if (isSecond) {
                        fire(e, ActivationMode.DOUBLE_TAP, now, consumption);
                        return true;
                    }
                    // Premier tap : on ne consomme pas, l'appui doit rester utilisable ailleurs.
                    return false;
                }
                case HOLD -> {
                    fire(e, ActivationMode.HOLD, now, consumption);
                    return true;
                }
                case LONG_PRESS, RELEASE -> {
                    // Rien à l'appui ; on réserve tout de même la touche pour ne pas laisser un
                    // bind moins spécifique se déclencher à la place.
                    return true;
                }
            }
        }

        if (action == Action.RELEASE) {
            // Un bind qui n'était pas enfoncé n'a rien à relâcher : laisser passer aux suivants.
            if (!e.down) return false;
            e.down = false;
            if (mode == ActivationMode.RELEASE) {
                fire(e, ActivationMode.RELEASE, now, consumption);
            }
            // Dans tous les cas l'entrée « possédait » cet appui : elle reste servie, ce qui
            // empêche un bind moins spécifique de récupérer le relâchement.
            return true;
        }
        return false;
    }

    private void fire(KeybindRegistry.Entry e, ActivationMode mode, long now,
                      KeyEvent.Consumption consumption) {
        long held = e.pressedAtNanos == 0 ? 0 : now - e.pressedAtNanos;
        KeyEvent event = new KeyEvent(e.bind, mode, e.effectiveChord, e.toggled, held, consumption);
        try {
            e.handler.accept(event);
        } catch (RuntimeException ex) {
            // Un module qui plante ne doit jamais casser le pipeline d'entrée du client entier.
            System.getLogger("input").log(System.Logger.Level.ERROR,
                    "handler du keybind " + e.bind.id() + " a levé une exception", ex);
        }
    }

    /**
     * Relâche de force les binds tenus. Sans cela, ouvrir le chat en tenant « sprint » laisse le
     * personnage courir indéfiniment — le bug de touche collée le plus courant des clients moddés.
     *
     * @param all si true, tout relâcher ; sinon, uniquement ce qui n'est plus actif dans le scope
     */
    private void flushHeld(boolean all) {
        ActivationContext.Scope scope = scopeProvider.currentScope();
        long now = System.nanoTime();
        List<KeybindRegistry.Entry> toRelease = new ArrayList<>();
        for (KeybindRegistry.Entry e : registry.all()) {
            if (!e.down) continue;
            if (all || !e.bind.context().activeIn(scope)) toRelease.add(e);
        }
        for (KeybindRegistry.Entry e : toRelease) {
            e.down = false;
            if (e.bind.mode() == ActivationMode.RELEASE) {
                fire(e, ActivationMode.RELEASE, now, new KeyEvent.Consumption());
            }
            e.resetState();
        }
    }

    /** Suivi du masque de modificateurs à partir de l'état physique observé, pas de GLFW. */
    private void updateModifierState(int keycode, Action action) {
        Chord.Modifier mod = modifierOf(keycode);
        if (mod == null) return;
        if (action == Action.PRESS) {
            activeModifiers |= mod.bit();
        } else if (action == Action.RELEASE) {
            activeModifiers &= ~mod.bit();
        }
    }

    // Codes GLFW des modificateurs (gauche/droite), en dur pour garder le core sans dépendance.
    private static Chord.Modifier modifierOf(int keycode) {
        return switch (keycode) {
            case 340, 344 -> Chord.Modifier.SHIFT;   // GLFW_KEY_LEFT_SHIFT / RIGHT_SHIFT
            case 341, 345 -> Chord.Modifier.CTRL;    // GLFW_KEY_LEFT_CONTROL / RIGHT_CONTROL
            case 342, 346 -> Chord.Modifier.ALT;     // GLFW_KEY_LEFT_ALT / RIGHT_ALT
            case 343, 347 -> Chord.Modifier.SUPER;   // GLFW_KEY_LEFT_SUPER / RIGHT_SUPER
            default -> null;
        };
    }

    public int activeModifiers() { return activeModifiers; }
}
