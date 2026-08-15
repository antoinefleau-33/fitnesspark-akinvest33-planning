package dev.poc.client.keybind;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A key plus an exact modifier set — {@code G}, {@code CTRL+G}, {@code CTRL+SHIFT+MOUSE3}.
 *
 * <p>Matching is <b>exact</b> on the modifier mask, not subset-based. That single decision removes
 * the most common keybind complaint: with subset matching, pressing {@code CTRL+G} also fires the
 * action bound to plain {@code G}, so a user who adds a modified variant of an existing bind gets
 * both actions. Exact matching means {@code G} and {@code CTRL+G} are simply different chords.
 *
 * <p>Lock keys are deliberately not part of the mask — see {@link #normaliseMods(int)}. A bind that
 * stopped working because Caps Lock was on would be indistinguishable, to the user, from a broken
 * mod.
 */
public record KeyChord(int keyCode, int modifiers) {

    public static final int MOD_SHIFT = 0x1;
    public static final int MOD_CONTROL = 0x2;
    public static final int MOD_ALT = 0x4;
    public static final int MOD_SUPER = 0x8;

    /** GLFW also reports Caps Lock (0x10) and Num Lock (0x20); they must never affect matching. */
    private static final int MOD_MASK = MOD_SHIFT | MOD_CONTROL | MOD_ALT | MOD_SUPER;

    public static final KeyChord UNBOUND = new KeyChord(Keys.UNKNOWN, 0);

    public KeyChord {
        modifiers = modifiers & MOD_MASK;
    }

    public static KeyChord of(int keyCode) {
        return new KeyChord(keyCode, 0);
    }

    public static KeyChord of(int keyCode, int modifiers) {
        return new KeyChord(keyCode, modifiers);
    }

    public boolean isBound() {
        return keyCode != Keys.UNKNOWN;
    }

    public static int normaliseMods(int rawGlfwMods) {
        return rawGlfwMods & MOD_MASK;
    }

    /** True when this chord is a strictly less-specific version of {@code other} on the same key. */
    public boolean isPrefixOf(KeyChord other) {
        return keyCode == other.keyCode
                && modifiers != other.modifiers
                && (modifiers & other.modifiers) == modifiers;
    }

    /** {@code "CTRL+SHIFT+G"}. Round-trips through {@link #parse(String)}. */
    public String format() {
        if (!isBound()) {
            return "NONE";
        }
        List<String> parts = new ArrayList<>(4);
        if ((modifiers & MOD_CONTROL) != 0) {
            parts.add("CTRL");
        }
        if ((modifiers & MOD_SHIFT) != 0) {
            parts.add("SHIFT");
        }
        if ((modifiers & MOD_ALT) != 0) {
            parts.add("ALT");
        }
        if ((modifiers & MOD_SUPER) != 0) {
            parts.add("SUPER");
        }
        parts.add(Keys.nameOf(keyCode));
        return String.join("+", parts);
    }

    public static KeyChord parse(String text) {
        if (text == null || text.isBlank() || text.equalsIgnoreCase("NONE")) {
            return UNBOUND;
        }
        int modifiers = 0;
        int keyCode = Keys.UNKNOWN;
        for (String rawPart : text.split("\\+")) {
            String part = rawPart.trim().toUpperCase(Locale.ROOT);
            switch (part) {
                case "CTRL", "CONTROL" -> modifiers |= MOD_CONTROL;
                case "SHIFT" -> modifiers |= MOD_SHIFT;
                case "ALT" -> modifiers |= MOD_ALT;
                case "SUPER", "META", "CMD" -> modifiers |= MOD_SUPER;
                default -> keyCode = Keys.codeOf(part);
            }
        }
        return keyCode == Keys.UNKNOWN ? UNBOUND : new KeyChord(keyCode, modifiers);
    }

    @Override
    public String toString() {
        return format();
    }
}
