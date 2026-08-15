package dev.poc.client.keybind;

import java.util.function.Consumer;

/**
 * A registered bind. The identity is {@link #id()} — {@code owner:localId} — and nothing else.
 * {@link #displayName()} is presentation only and two modules may happily share one.
 */
public final class KeybindHandle {

    private final String id;
    private final String owner;
    private final String displayName;
    private final KeyChord defaultChord;
    private final KeyContext context;
    private final KeybindManager.Activation activation;
    private final Consumer<KeybindHandle> action;

    private KeyChord chord;
    private int priority;
    private boolean consumesInput = true;
    private boolean enabled = true;

    // runtime state, owned by the manager
    private boolean pressed;
    private boolean toggled;
    private long lastPressNanos = Long.MIN_VALUE;

    KeybindHandle(String owner, String localId, String displayName, KeyChord defaultChord,
                  KeyContext context, KeybindManager.Activation activation,
                  Consumer<KeybindHandle> action) {
        this.owner = owner;
        this.id = owner + ":" + localId;
        this.displayName = displayName;
        this.defaultChord = defaultChord;
        this.chord = defaultChord;
        this.context = context;
        this.activation = activation;
        this.action = action;
    }

    public String id() {
        return id;
    }

    public String owner() {
        return owner;
    }

    public String displayName() {
        return displayName;
    }

    public KeyChord chord() {
        return chord;
    }

    public KeyChord defaultChord() {
        return defaultChord;
    }

    public KeyContext context() {
        return context;
    }

    public KeybindManager.Activation activation() {
        return activation;
    }

    public boolean isPressed() {
        return pressed;
    }

    public boolean isToggled() {
        return toggled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int priority() {
        return priority;
    }

    public boolean consumesInput() {
        return consumesInput;
    }

    /** Higher priority wins a chord collision. Client-reserved binds sit above module binds. */
    public KeybindHandle priority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * When false the bind fires but lets lower-priority binds on the same chord fire too. Overlays
     * and HUD toggles usually want this; anything that eats a gameplay key does not.
     */
    public KeybindHandle passthrough() {
        this.consumesInput = false;
        return this;
    }

    public KeybindHandle enabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            pressed = false;
        }
        return this;
    }

    void setChord(KeyChord chord) {
        this.chord = chord;
        this.pressed = false;
    }

    void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    boolean toggle() {
        toggled = !toggled;
        return toggled;
    }

    long lastPressNanos() {
        return lastPressNanos;
    }

    void setLastPressNanos(long nanos) {
        this.lastPressNanos = nanos;
    }

    void fire() {
        action.accept(this);
    }

    @Override
    public String toString() {
        return id + " = " + chord.format() + " (" + context + ", " + activation + ")";
    }
}
