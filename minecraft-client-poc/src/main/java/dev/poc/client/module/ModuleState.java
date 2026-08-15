package dev.poc.client.module;

public enum ModuleState {
    /** Manifest read, class loader built, instance constructed, {@code onLoad} done. */
    LOADED,
    /** Active: event handlers and keybinds are live. */
    ENABLED,
    /** Loaded but inactive. Can be re-enabled without touching the class loader. */
    DISABLED,
    /** Loading or enabling threw. The module is inert and reported in the UI. */
    ERRORED,
    /** Class loader closed. The container is a tombstone kept for diagnostics. */
    UNLOADED
}
