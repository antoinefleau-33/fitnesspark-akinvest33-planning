package dev.poc.client.keybind;

/**
 * Where a bind is allowed to fire. Two binds sharing a chord are only in conflict if their contexts
 * can overlap, which is what lets {@code E} mean "open inventory" in the world and "close screen"
 * in a GUI without either one being a bug.
 */
public enum KeyContext {

    /** Fires anywhere, including while a screen is open. Reserve for things like screenshots. */
    ANY,

    /** No screen open, the mouse is grabbed. The default for gameplay binds. */
    IN_GAME,

    /** A screen is open but no text field has focus. */
    IN_SCREEN,

    /** A text field has focus. Almost nothing should bind here — typing must win. */
    IN_TEXT_INPUT;

    public boolean overlaps(KeyContext other) {
        return this == ANY || other == ANY || this == other;
    }

    public boolean isActiveIn(KeyContext current) {
        return this == ANY || this == current;
    }
}
