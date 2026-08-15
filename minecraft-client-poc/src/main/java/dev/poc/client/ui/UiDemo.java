package dev.poc.client.ui;

import dev.poc.client.keybind.KeyChord;
import dev.poc.client.keybind.KeyContext;
import dev.poc.client.keybind.KeybindManager;
import dev.poc.client.keybind.Keys;
import dev.poc.client.version.RenderThreadExecutor;
import dev.poc.client.version.SimulatedVersionRuntime;
import dev.poc.client.version.VersionManifest;
import dev.poc.client.version.VersionSwitchCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Opens the main menu and wires it to a simulated version switcher, so the swap phases and the
 * progress pill can be seen end to end without a Minecraft install.
 *
 * <p>{@code ./gradlew runUi}
 */
public final class UiDemo {

    private record SwapState(String version, String detail, float progress) {
    }

    public static void main(String[] args) {
        KeybindManager keybinds = new KeybindManager();
        RenderThreadExecutor renderThread = new RenderThreadExecutor();

        AtomicReference<SwapState> state =
                new AtomicReference<>(new SwapState("no version", "idle", -1f));

        try (ClientWindow window = new ClientWindow("POC Client", 1280, 720, keybinds, renderThread)) {
            NanoVgRenderer gfx = window.renderer();
            loadSystemFont(gfx);

            VersionSwitchCoordinator coordinator = new VersionSwitchCoordinator(
                    UiDemo::fakeManifest,
                    manifest -> new SimulatedVersionRuntime(manifest.id(), 1200),
                    renderThread,
                    new VersionSwitchCoordinator.SwapHooks() {
                        @Override
                        public void beforeDetach(String outgoing) {
                        }

                        @Override
                        public void afterAttach(String incoming) {
                        }
                    });
            coordinator.setWindowHandle(window.handle());
            coordinator.setProgressListener((phase, version, fraction) ->
                    state.set(new SwapState(version,
                            phase == VersionSwitchCoordinator.Phase.IDLE
                                    ? "ready" : phase.name().toLowerCase(java.util.Locale.ROOT),
                            phase == VersionSwitchCoordinator.Phase.IDLE ? -1f : (float) fraction)));

            Theme theme = Theme.MIDNIGHT;
            MainMenuScreen menu = new MainMenuScreen(theme, new MainMenuScreen.Status() {
                @Override
                public String versionLabel() {
                    return state.get().version();
                }

                @Override
                public String detail() {
                    return state.get().detail();
                }

                @Override
                public float progress() {
                    return state.get().progress();
                }
            });

            float delay = 0.06f;
            menu.add(new AnimatedButton("Singleplayer", "local worlds", delay, b -> {
            }));
            menu.add(new AnimatedButton("Multiplayer", "server list", delay * 2, b -> {
            }));
            menu.add(new AnimatedButton("Switch to 1.8.9", "prepare and attach", delay * 3,
                    b -> coordinator.switchTo("1.8.9")));
            menu.add(new AnimatedButton("Switch to 1.20.1", "prepare and attach", delay * 4,
                    b -> coordinator.switchTo("1.20.1")));
            menu.add(new AnimatedButton("Modules", "enable, disable, configure", delay * 5, b -> {
            }));

            window.setCursorHandler(menu);

            // A client-owned bind, above module binds, that survives every version swap.
            keybinds.register("client", "warm-next", "Pre-warm next version",
                            KeyChord.of(Keys.codeOf("W"), KeyChord.MOD_CONTROL),
                            KeyContext.ANY, KeybindManager.Activation.PRESS,
                            handle -> coordinator.warm("1.20.1"))
                    .priority(1000);

            window.run(menu, theme);
            coordinator.shutdown();
        }
    }

    /**
     * NanoVG has no built-in font. Falls back through the usual install paths rather than shipping
     * one — a real client bundles its own so text is identical on every machine.
     */
    private static void loadSystemFont(NanoVgRenderer gfx) {
        List<String> candidates = List.of(
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "/usr/share/fonts/TTF/DejaVuSans.ttf",
                "/System/Library/Fonts/Helvetica.ttc",
                "C:\\Windows\\Fonts\\segoeui.ttf");
        for (String candidate : candidates) {
            if (Files.isRegularFile(Path.of(candidate)) && gfx.loadFont("sans", candidate)) {
                return;
            }
        }
        System.err.println("No font found — text will not render. Bundle a .ttf and load it here.");
    }

    private static VersionManifest fakeManifest(String versionId) {
        VersionManifest.Artifact empty =
                new VersionManifest.Artifact("client.jar", "0".repeat(40), 0L, "");
        return new VersionManifest(versionId, "21", VersionManifest.MappingFlavour.MOJMAP,
                empty, List.of(), List.of(), "index", empty);
    }
}
