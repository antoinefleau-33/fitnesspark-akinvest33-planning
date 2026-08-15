package dev.poc.core;

import dev.poc.api.input.ActivationContext;
import dev.poc.api.input.ActivationMode;
import dev.poc.api.input.Chord;
import dev.poc.api.input.Keybind;
import dev.poc.core.input.InputPipeline;
import dev.poc.core.input.KeybindRegistry;
import dev.poc.core.module.DependencyResolver;
import dev.poc.core.module.ModuleDiscovery;
import dev.poc.core.util.SemVer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Démonstrateur sans dépendance : exécute le résolveur de keybinds et le résolveur de dépendances
 * hors de tout contexte graphique, et vérifie les comportements qui posent problème dans les
 * clients moddés existants.
 *
 * <pre>
 *   javac -d out $(find . -name '*.java' -path '*poc-api*' -o -name '*.java' -path '*poc-core*')
 *   java -cp out dev.poc.core.Demo
 * </pre>
 */
public final class Demo {

    private static final int SCAN_G = 34;      // scancode de la touche « G » en QWERTY US
    private static final int SCAN_LCTRL = 29;
    private static final int KEY_LCTRL = 341;  // GLFW_KEY_LEFT_CONTROL

    private static ActivationContext.Scope scope = ActivationContext.Scope.GAMEPLAY;
    private static final List<String> journal = new ArrayList<>();

    public static void main(String[] args) {
        section("1. Deux modules bindent la même touche — le cas qui casse en vanilla");

        KeybindRegistry registry = new KeybindRegistry();
        InputPipeline pipeline = new InputPipeline(registry, () -> scope);
        pipeline.setPassthrough((device, code, keycode, action, mods) ->
                journal.add("  → transmis au jeu: code=" + code + " " + action));

        registry.register(
                Keybind.builder("hud-example:toggle")
                        .displayName("Afficher le HUD")
                        .defaultChord(Chord.key(SCAN_G))
                        .mode(ActivationMode.TOGGLE)
                        .build(),
                e -> journal.add("  [hud-example] toggle → " + e.toggleState()));

        registry.register(
                Keybind.builder("minimap:center")
                        .displayName("Recentrer la minimap")
                        .defaultChord(Chord.key(SCAN_G))   // MÊME touche, sans modificateur
                        .priority(-10)                      // priorité plus basse
                        .passthrough()                      // ne consomme pas
                        .build(),
                e -> journal.add("  [minimap] recentrage"));

        registry.register(
                Keybind.builder("screenshot:capture")
                        .displayName("Capture d'écran")
                        .defaultChord(Chord.key(SCAN_G, Chord.Modifier.CTRL))   // Ctrl+G
                        .build(),
                e -> journal.add("  [screenshot] capture !"));

        System.out.println("Conflits détectés à l'enregistrement :");
        registry.conflicts().forEach(c ->
                System.out.println("  " + c.severity() + " sur " + c.chord().serialize()
                        + " entre " + c.keybindIds()));

        System.out.println("\nAppui sur G (sans modificateur) :");
        press(pipeline, SCAN_G, SCAN_G);
        release(pipeline, SCAN_G, SCAN_G);
        drain();
        System.out.println("  attendu : hud-example consomme, minimap NON servi "
                + "(hud est consommant et plus prioritaire)");

        System.out.println("\nAppui sur Ctrl+G :");
        pipeline.onKey(SCAN_LCTRL, KEY_LCTRL, InputPipeline.Action.PRESS);
        press(pipeline, SCAN_G, SCAN_G);
        release(pipeline, SCAN_G, SCAN_G);
        pipeline.onKey(SCAN_LCTRL, KEY_LCTRL, InputPipeline.Action.RELEASE);
        drain();
        System.out.println("  attendu : SEUL screenshot se déclenche — la spécificité "
                + "(1 modificateur) l'emporte, et le bind G nu est ignoré");

        section("2. Touche collée : un écran s'ouvre pendant un maintien");

        KeybindRegistry r2 = new KeybindRegistry();
        InputPipeline p2 = new InputPipeline(r2, () -> scope);
        r2.register(
                Keybind.builder("core:sprint")
                        .defaultChord(Chord.key(SCAN_G))
                        .mode(ActivationMode.HOLD)
                        .context(ActivationContext.IN_GAME)
                        .build(),
                e -> {});

        scope = ActivationContext.Scope.GAMEPLAY;
        p2.onKey(SCAN_G, SCAN_G, InputPipeline.Action.PRESS);
        System.out.println("  touche maintenue en jeu, isActive = " + r2.isActive("core:sprint"));

        scope = ActivationContext.Scope.TEXT_INPUT;   // le joueur ouvre le chat
        p2.tick();
        System.out.println("  après ouverture du chat,  isActive = " + r2.isActive("core:sprint"));
        System.out.println("  sans le flush sur changement de scope, ce serait encore 'true' "
                + "et le personnage courrait indéfiniment");
        scope = ActivationContext.Scope.GAMEPLAY;

        section("3. Contextes disjoints : pas un conflit");

        KeybindRegistry r3 = new KeybindRegistry();
        r3.register(Keybind.builder("a:jump").defaultChord(Chord.key(57))
                .context(ActivationContext.IN_GAME).build(), e -> {});
        r3.register(Keybind.builder("b:confirm").defaultChord(Chord.key(57))
                .context(ActivationContext.IN_SCREEN).build(), e -> {});
        System.out.println("  même touche, contextes disjoints → conflits = "
                + r3.conflicts().size() + " (attendu 0)");

        section("4. Id dupliqué : échec bruyant au chargement");

        try {
            KeybindRegistry r4 = new KeybindRegistry();
            r4.register(Keybind.builder("mod:action").defaultChord(Chord.key(20)).build(), e -> {});
            r4.register(Keybind.builder("mod:action").defaultChord(Chord.key(21)).build(), e -> {});
        } catch (RuntimeException e) {
            System.out.println("  " + e.getMessage());
            System.out.println("  en vanilla, le second écraserait silencieusement le premier");
        }

        section("5. Persistance indépendante de la disposition clavier");

        Chord ctrlG = Chord.key(SCAN_G, Chord.Modifier.CTRL);
        String serialized = ctrlG.serialize();
        System.out.println("  sérialisé  : " + serialized);
        System.out.println("  relu       : " + Chord.deserialize(serialized).serialize());
        System.out.println("  le scancode " + SCAN_G + " désigne la même touche physique en "
                + "AZERTY et en QWERTY ; seul le libellé affiché change");

        section("6. Résolution de dépendances de modules");

        var candidates = List.of(
                candidate("core-render", "1.4.0"),
                candidate("themes", "0.9.0", "core-render", ">=1.2.0 <2.0.0"),
                candidate("hud-example", "1.0.0", "themes", "^0.9.0"));
        var resolved = DependencyResolver.resolve(candidates, "1.20.1");
        System.out.println("  ordre de chargement : "
                + resolved.ordered().stream().map(c -> c.metadata().id()).toList());
        resolved.warnings().forEach(w -> System.out.println("  avertissement : " + w));

        try {
            DependencyResolver.resolve(List.of(
                    candidate("mod-a", "1.0.0", "mod-b", "*"),
                    candidate("mod-b", "1.0.0", "mod-a", "*")), "1.20.1");
        } catch (DependencyResolver.ResolutionException e) {
            System.out.println("  " + e.getMessage());
        }

        System.out.println("\n  contraintes semver : 1.4.0 satisfait '>=1.2.0 <2.0.0' → "
                + SemVer.parse("1.4.0").satisfies(">=1.2.0 <2.0.0"));
        System.out.println("  contraintes semver : 2.0.0 satisfait '^1.2.0'         → "
                + SemVer.parse("2.0.0").satisfies("^1.2.0"));
    }

    private static void press(InputPipeline p, int scancode, int keycode) {
        p.onKey(scancode, keycode, InputPipeline.Action.PRESS);
    }

    private static void release(InputPipeline p, int scancode, int keycode) {
        p.onKey(scancode, keycode, InputPipeline.Action.RELEASE);
    }

    private static void drain() {
        journal.forEach(System.out::println);
        journal.clear();
    }

    private static void section(String title) {
        System.out.println("\n" + "=".repeat(72));
        System.out.println(title);
        System.out.println("=".repeat(72));
    }

    private static ModuleDiscovery.Candidate candidate(String id, String version,
                                                       String... dependPairs) {
        StringBuilder depends = new StringBuilder();
        for (int i = 0; i + 1 < dependPairs.length; i += 2) {
            if (!depends.isEmpty()) depends.append(",");
            depends.append("\"").append(dependPairs[i]).append("\":\"")
                    .append(dependPairs[i + 1]).append("\"");
        }
        String json = """
                {
                  "id": "%s",
                  "version": "%s",
                  "entrypoint": "dev.poc.demo.%s",
                  "gameVersions": ["*"],
                  "depends": {%s}
                }
                """.formatted(id, version, id.replace('-', '_'), depends);
        return new ModuleDiscovery.Candidate(Path.of(id + ".jar"),
                ModuleDiscovery.fromJson(json));
    }
}
