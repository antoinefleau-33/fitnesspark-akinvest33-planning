package dev.poc.client.keybind;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GLFW key codes and their canonical names, mirrored here so the keybind package stays free of a
 * hard LWJGL dependency (handy for unit tests and for a headless config editor). The numeric values
 * are GLFW's and match {@code org.lwjgl.glfw.GLFW.GLFW_KEY_*} one for one.
 *
 * <p>Mouse buttons live in the same numeric space, offset by {@link #MOUSE_OFFSET}, so a chord can
 * bind "middle mouse" exactly like a letter and the rest of the system needs no special case.
 */
public final class Keys {

    private Keys() {
    }

    public static final int UNKNOWN = -1;
    public static final int MOUSE_OFFSET = 1000;

    public static final int SPACE = 32;
    public static final int APOSTROPHE = 39;
    public static final int COMMA = 44;
    public static final int MINUS = 45;
    public static final int PERIOD = 46;
    public static final int SLASH = 47;
    public static final int SEMICOLON = 59;
    public static final int EQUAL = 61;
    public static final int LEFT_BRACKET = 91;
    public static final int BACKSLASH = 92;
    public static final int RIGHT_BRACKET = 93;
    public static final int GRAVE_ACCENT = 96;
    public static final int ESCAPE = 256;
    public static final int ENTER = 257;
    public static final int TAB = 258;
    public static final int BACKSPACE = 259;
    public static final int INSERT = 260;
    public static final int DELETE = 261;
    public static final int RIGHT = 262;
    public static final int LEFT = 263;
    public static final int DOWN = 264;
    public static final int UP = 265;
    public static final int PAGE_UP = 266;
    public static final int PAGE_DOWN = 267;
    public static final int HOME = 268;
    public static final int END = 269;
    public static final int CAPS_LOCK = 280;
    public static final int LEFT_SHIFT = 340;
    public static final int LEFT_CONTROL = 341;
    public static final int LEFT_ALT = 342;
    public static final int LEFT_SUPER = 343;
    public static final int RIGHT_SHIFT = 344;
    public static final int RIGHT_CONTROL = 345;
    public static final int RIGHT_ALT = 346;
    public static final int RIGHT_SUPER = 347;

    public static final int MOUSE_LEFT = MOUSE_OFFSET;
    public static final int MOUSE_RIGHT = MOUSE_OFFSET + 1;
    public static final int MOUSE_MIDDLE = MOUSE_OFFSET + 2;
    public static final int MOUSE_4 = MOUSE_OFFSET + 3;
    public static final int MOUSE_5 = MOUSE_OFFSET + 4;

    private static final Map<String, Integer> BY_NAME = new LinkedHashMap<>();
    private static final Map<Integer, String> BY_CODE = new LinkedHashMap<>();

    static {
        for (char c = 'A'; c <= 'Z'; c++) {
            put(String.valueOf(c), c); // GLFW letter codes are the ASCII uppercase values
        }
        for (char c = '0'; c <= '9'; c++) {
            put(String.valueOf(c), c);
        }
        for (int i = 1; i <= 25; i++) {
            put("F" + i, 289 + i); // GLFW_KEY_F1 == 290
        }
        for (int i = 0; i <= 9; i++) {
            put("NUMPAD" + i, 320 + i);
        }
        put("SPACE", SPACE);
        put("APOSTROPHE", APOSTROPHE);
        put("COMMA", COMMA);
        put("MINUS", MINUS);
        put("PERIOD", PERIOD);
        put("SLASH", SLASH);
        put("SEMICOLON", SEMICOLON);
        put("EQUAL", EQUAL);
        put("LBRACKET", LEFT_BRACKET);
        put("BACKSLASH", BACKSLASH);
        put("RBRACKET", RIGHT_BRACKET);
        put("GRAVE", GRAVE_ACCENT);
        put("ESCAPE", ESCAPE);
        put("ENTER", ENTER);
        put("TAB", TAB);
        put("BACKSPACE", BACKSPACE);
        put("INSERT", INSERT);
        put("DELETE", DELETE);
        put("RIGHT", RIGHT);
        put("LEFT", LEFT);
        put("DOWN", DOWN);
        put("UP", UP);
        put("PAGEUP", PAGE_UP);
        put("PAGEDOWN", PAGE_DOWN);
        put("HOME", HOME);
        put("END", END);
        put("MOUSE1", MOUSE_LEFT);
        put("MOUSE2", MOUSE_RIGHT);
        put("MOUSE3", MOUSE_MIDDLE);
        put("MOUSE4", MOUSE_4);
        put("MOUSE5", MOUSE_5);
        put("NONE", UNKNOWN);
    }

    private static void put(String name, int code) {
        BY_NAME.put(name, code);
        BY_CODE.putIfAbsent(code, name);
    }

    public static int codeOf(String name) {
        Integer code = BY_NAME.get(name.toUpperCase(java.util.Locale.ROOT));
        return code == null ? UNKNOWN : code;
    }

    public static String nameOf(int code) {
        return BY_CODE.getOrDefault(code, "KEY_" + code);
    }

    public static boolean isModifierKey(int code) {
        return code >= LEFT_SHIFT && code <= RIGHT_SUPER;
    }

    public static boolean isMouse(int code) {
        return code >= MOUSE_OFFSET;
    }
}
