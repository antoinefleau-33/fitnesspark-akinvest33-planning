package dev.poc.client;

import dev.poc.client.event.ClientEvents;
import dev.poc.client.event.EventBus;
import dev.poc.client.event.Subscribe;
import dev.poc.client.keybind.KeyChord;
import dev.poc.client.keybind.KeyContext;
import dev.poc.client.keybind.KeybindManager;
import dev.poc.client.keybind.Keys;
import dev.poc.client.module.Module;
import dev.poc.client.module.ModuleContext;
import dev.poc.client.module.ModuleDescriptor;
import dev.poc.client.module.ModuleManager;
import dev.poc.client.version.RenderThreadExecutor;
import dev.poc.client.version.SimulatedVersionRuntime;
import dev.poc.client.version.VersionSwitchCoordinator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Headless walkthrough of the four subsystems, runnable without a GPU or a Minecraft install:
 * module lifecycle, event scoping, keybind conflict handling, and a version swap driven through the
 * coordinator with simulated runtimes.
 *
 * <p>{@code ./gradlew runDemo}
 */
public final class PocDemo {

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("poc-client");
        EventBus eventBus = new EventBus();
        KeybindManager keybinds = new KeybindManager();
        ModuleManager modules = new ModuleManager(eventBus, keybinds,
                root.resolve("mods"), root.resolve("data"));

        section("1. modules");
        modules.registerBuiltin(descriptor("core-hud", "Core HUD", List.of()), CoreHudModule::new);
        modules.registerBuiltin(descriptor("waypoints", "Waypoints", List.of("core-hud")),
                WaypointsModule::new);
        modules.registerBuiltin(descriptor("broken", "Broken", List.of("does-not-exist")),
                CoreHudModule::new);
        modules.discover();
        modules.loadAll();

        modules.rejectedModules().forEach((id, reason) ->
                System.out.println("  rejected " + id + ": " + reason));
        modules.modules().forEach(container -> modules.enable(container.id()));
        modules.modules().forEach(container -> System.out.println("  " + container));

        section("2. keybinds - same physical key, no collision");
        keybinds.setContext(KeyContext.IN_GAME);
        keybinds.handles().forEach(handle -> System.out.println("  " + handle));
        System.out.println("  press G        -> consumed=" + press(keybinds, Keys.codeOf("G"), 0));
        System.out.println("  press CTRL+G   -> consumed="
                + press(keybinds, Keys.codeOf("G"), KeyChord.MOD_CONTROL));

        section("3. deliberate conflict, then resolution");
        keybinds.rebind("waypoints:toggle-list", KeyChord.of(Keys.codeOf("G")));
        keybinds.conflicts().forEach(conflict -> System.out.println("  conflict " + conflict));
        System.out.println("  press G        -> consumed=" + press(keybinds, Keys.codeOf("G"), 0)
                + "  (highest priority consumed it; the other never fired)");
        List<?> displaced = keybinds.rebindExclusive("waypoints:toggle-list",
                KeyChord.of(Keys.codeOf("G")));
        System.out.println("  rebindExclusive kept waypoints on G and unbound " + displaced);
        System.out.println("  conflicts now: " + keybinds.conflicts());

        section("4. disabling a module removes its handlers and binds");
        eventBus.post(new ClientEvents.Tick(1));
        System.out.println("  disabled: " + modules.disable("core-hud")
                + "  (waypoints depends on it, so it went first)");
        System.out.println("  handlers left: " + eventBus.handlerCount()
                + ", binds left: " + keybinds.handles().size());
        eventBus.post(new ClientEvents.Tick(2));

        section("5. version swap");
        modules.enable("waypoints");
        RenderThreadExecutor renderThread = new RenderThreadExecutor();
        renderThread.bindToCurrentThread();

        VersionSwitchCoordinator simulated = simulatedCoordinator(renderThread, modules, eventBus);
        simulated.setProgressListener((phase, version, fraction) ->
                System.out.printf("  [%-9s] %-8s %3.0f%%%n", phase, version, fraction * 100));

        pump(renderThread, simulated.switchTo("1.8.9"));
        pump(renderThread, simulated.warm("1.20.1"));
        System.out.println("  1.20.1 warmed in the background; the swap below skips preparation");
        pump(renderThread, simulated.switchTo("1.20.1"));
        simulated.shutdown();

        section("done");
        System.out.println("  temp root: " + root);
    }

    private static VersionSwitchCoordinator simulatedCoordinator(RenderThreadExecutor renderThread,
                                                                 ModuleManager modules,
                                                                 EventBus eventBus) {
        return new VersionSwitchCoordinator(
                versionId -> new dev.poc.client.version.VersionManifest(
                        versionId, "21",
                        dev.poc.client.version.VersionManifest.MappingFlavour.MOJMAP,
                        new dev.poc.client.version.VersionManifest.Artifact(
                                "client.jar", "0".repeat(40), 0, ""),
                        List.of(), List.of(), "index",
                        new dev.poc.client.version.VersionManifest.Artifact(
                                "index.json", "0".repeat(40), 0, "")),
                manifest -> new SimulatedVersionRuntime(manifest.id(), 250),
                renderThread,
                new VersionSwitchCoordinator.SwapHooks() {
                    @Override
                    public void beforeDetach(String outgoing) {
                        modules.shutdown();
                    }

                    @Override
                    public void afterAttach(String incoming) {
                        modules.modules().forEach(container -> modules.enable(container.id()));
                        eventBus.post(new ClientEvents.VersionChanged(null, incoming));
                    }
                });
    }

    /** Stands in for the render loop: drains render-thread work until the swap completes. */
    private static void pump(RenderThreadExecutor renderThread, CompletableFuture<?> future)
            throws Exception {
        while (!future.isDone()) {
            renderThread.drain(Long.MAX_VALUE);
            Thread.sleep(5);
        }
        renderThread.drain(Long.MAX_VALUE);
        future.get();
    }

    private static boolean press(KeybindManager keybinds, int key, int mods) {
        boolean consumed = keybinds.onKey(key, 1, mods);
        keybinds.onKey(key, 0, mods);
        return consumed;
    }

    private static ModuleDescriptor descriptor(String id, String name, List<String> depends) {
        return new ModuleDescriptor(id, name, "1.0.0",
                "builtin." + id, ModuleDescriptor.CURRENT_API_VERSION, depends, List.of());
    }

    private static void section(String title) {
        System.out.println();
        System.out.println("== " + title + " " + "=".repeat(Math.max(0, 60 - title.length())));
    }

    // ------------------------------------------------------------------ demo modules

    /** Both demo modules bind the same physical key, on purpose. */
    public static final class CoreHudModule implements Module {
        @Override
        public void onEnable(ModuleContext context) {
            context.subscribe(this);
            context.bindKey("toggle-hud", "Toggle HUD", KeyChord.of(Keys.codeOf("G")),
                    KeyContext.IN_GAME, KeybindManager.Activation.TOGGLE,
                    handle -> context.log("HUD -> " + (handle.isToggled() ? "on" : "off")))
                    .priority(10);
        }

        @Subscribe
        public void onTick(ClientEvents.Tick tick) {
            System.out.println("  [core-hud] tick " + tick.tickCount());
        }
    }

    /** Depends on core-hud, so it enables after it and disables before it. */
    public static final class WaypointsModule implements Module {
        @Override
        public void onEnable(ModuleContext context) {
            context.subscribe(this);
            context.bindKey("toggle-list", "Toggle waypoints",
                    KeyChord.of(Keys.codeOf("G"), KeyChord.MOD_CONTROL),
                    KeyContext.IN_GAME, KeybindManager.Activation.PRESS,
                    handle -> context.log("waypoint list toggled"));
        }

        @Subscribe(priority = Subscribe.MONITOR)
        public void onTick(ClientEvents.Tick tick) {
            System.out.println("  [waypoints] tick " + tick.tickCount());
        }

        @Subscribe
        public void onVersionChanged(ClientEvents.VersionChanged event) {
            System.out.println("  [waypoints] rebinding adapter for " + event.newVersionId());
        }
    }
}
