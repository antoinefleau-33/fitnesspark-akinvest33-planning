package dev.poc.client.module;

/**
 * Entry point every module implements. The class named by {@code main} in the manifest must
 * implement this and expose a public no-arg constructor.
 *
 * <p>Lifecycle: {@code onLoad} → {@code onEnable} → ({@code onDisable} → {@code onEnable})* →
 * {@code onDisable} → {@code onUnload}. Only {@code onEnable}/{@code onDisable} are re-entered,
 * which is what lets the user toggle a module from the UI without a restart.
 */
public interface Module {

    /**
     * Called once after construction, before any dependent module is enabled. Register nothing
     * that has a visible effect here — read config, allocate state, and return fast.
     */
    default void onLoad(ModuleContext context) {
    }

    /**
     * Called every time the module becomes active. Register event handlers and keybinds through
     * {@code context} only: anything registered that way is torn down automatically on disable.
     */
    void onEnable(ModuleContext context);

    /**
     * Called every time the module goes inactive. Release GPU handles, threads, and file locks.
     * Handlers and keybinds registered through the context are already gone by the time this runs.
     */
    default void onDisable(ModuleContext context) {
    }

    /** Last call before the class loader is closed. Anything not released here leaks the loader. */
    default void onUnload(ModuleContext context) {
    }
}
