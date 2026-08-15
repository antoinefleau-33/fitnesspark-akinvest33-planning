package dev.poc.client.keybind;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Keybind registry and dispatcher.
 *
 * <h2>The {@code key.anything} failure this replaces</h2>
 * Vanilla keys a {@code KeyBinding} by its <em>description</em> string in a static
 * {@code Map<String, KeyBinding> KEY_BIND_MAP}, and writes {@code options.txt} lines under that
 * same string. Two mods that both use a generic description — the copy-pasted {@code key.anything}
 * being the folklore example — collapse into one map entry: the second registration overwrites the
 * first, {@code options.txt} carries a single line for both, and the losing mod's
 * {@code isKeyDown()} returns false forever with no error anywhere. Older versions make it worse
 * with a second static index keyed by key code, so two binds on the same physical key also drop one.
 *
 * <p>Four decisions make that class of bug unrepresentable here:
 * <ol>
 *   <li><b>Identity is {@code owner:localId}</b>, assigned by the host from the module id. A module
 *       cannot choose an id that collides with another module's, and a duplicate <em>within</em> a
 *       module throws at registration instead of failing silently at runtime.</li>
 *   <li><b>Display names are decoration.</b> Two binds may both be called "Toggle". Nothing keys
 *       off the label.</li>
 *   <li><b>Pressed state lives on the handle</b> and is driven by raw GLFW callbacks, so there is
 *       no shared static index to overwrite. Sharing a chord is a supported configuration, resolved
 *       by context and priority, not an accident that silently disables someone.</li>
 *   <li><b>Persistence is keyed by id</b>, and bindings for ids that are not currently registered
 *       are retained rather than dropped — disabling a module for one session does not lose its
 *       custom binds.</li>
 * </ol>
 *
 * <h2>Stuck-key robustness</h2>
 * Releases are matched by key code alone, never by modifier mask. Matching a release against the
 * chord is the standard way to get a permanently-held bind: press {@code CTRL+G}, release
 * {@code CTRL} first, then {@code G} — the release arrives with mods {@code 0}, fails to match, and
 * the bind stays down forever. {@link #releaseAll()} covers the other half (window focus loss).
 */
public final class KeybindManager {

    public enum Activation {
        /** Fires once on key-down. */
        PRESS,
        /** Fires once on key-up. */
        RELEASE,
        /** Fires every tick while held. */
        HOLD,
        /** Flips {@link KeybindHandle#isToggled()} on key-down, then fires. */
        TOGGLE,
        /** Fires when pressed twice within {@link #doubleTapWindowMillis}. */
        DOUBLE_TAP
    }

    public record Conflict(KeyChord chord, List<KeybindHandle> handles) {
        @Override
        public String toString() {
            return chord.format() + " -> "
                    + handles.stream().map(KeybindHandle::id).toList();
        }
    }

    private final Map<String, KeybindHandle> byId = new LinkedHashMap<>();
    private final Map<Integer, List<KeybindHandle>> byKeyCode = new HashMap<>();
    private final Map<String, String> storedChords = new LinkedHashMap<>();
    private final LongSupplier clock;

    private long doubleTapWindowMillis = 400;
    private KeyContext currentContext = KeyContext.IN_GAME;

    public KeybindManager() {
        this(System::nanoTime);
    }

    public KeybindManager(LongSupplier nanoClock) {
        this.clock = nanoClock;
    }

    // ---------------------------------------------------------------- registration

    public KeybindHandle register(String owner, String localId, String displayName,
                                  KeyChord defaultChord, KeyContext context,
                                  Activation activation, Consumer<KeybindHandle> action) {
        KeybindHandle handle = new KeybindHandle(owner, localId, displayName, defaultChord,
                context, activation, action);
        if (byId.containsKey(handle.id())) {
            throw new IllegalStateException("Duplicate keybind id '" + handle.id()
                    + "'. Ids must be unique within a module.");
        }
        String stored = storedChords.get(handle.id());
        if (stored != null) {
            handle.setChord(KeyChord.parse(stored));
        }
        byId.put(handle.id(), handle);
        reindex();
        return handle;
    }

    /** Drops every bind owned by a module. Called by the module manager on disable. */
    public void unregisterAll(String owner) {
        if (byId.values().removeIf(handle -> handle.owner().equals(owner))) {
            reindex();
        }
    }

    public void unregister(String id) {
        if (byId.remove(id) != null) {
            reindex();
        }
    }

    private void reindex() {
        byKeyCode.clear();
        List<KeybindHandle> ordered = new ArrayList<>(byId.values());
        // Highest priority first; registration order breaks ties, so behaviour is deterministic.
        ordered.sort(Comparator.<KeybindHandle>comparingInt(KeybindHandle::priority).reversed());
        for (KeybindHandle handle : ordered) {
            if (handle.chord().isBound()) {
                byKeyCode.computeIfAbsent(handle.chord().keyCode(), k -> new ArrayList<>()).add(handle);
            }
        }
    }

    // ---------------------------------------------------------------- rebinding

    /**
     * Rebinds and returns the conflicts the new chord creates, so the UI can show a warning without
     * the manager deciding for the user. Rebinding to an already-used chord is legal.
     */
    public List<Conflict> rebind(String id, KeyChord chord) {
        KeybindHandle handle = byId.get(id);
        if (handle == null) {
            throw new IllegalArgumentException("Unknown keybind '" + id + "'");
        }
        handle.setChord(chord);
        storedChords.put(id, chord.format());
        reindex();
        return conflictsFor(handle);
    }

    /** Rebinds and unbinds anything else that would collide. The "make it just work" button. */
    public List<KeybindHandle> rebindExclusive(String id, KeyChord chord) {
        List<KeybindHandle> displaced = new ArrayList<>();
        for (Conflict conflict : rebind(id, chord)) {
            for (KeybindHandle other : conflict.handles()) {
                if (!other.id().equals(id)) {
                    other.setChord(KeyChord.UNBOUND);
                    storedChords.put(other.id(), KeyChord.UNBOUND.format());
                    displaced.add(other);
                }
            }
        }
        reindex();
        return displaced;
    }

    public void resetToDefault(String id) {
        KeybindHandle handle = byId.get(id);
        if (handle != null) {
            handle.setChord(handle.defaultChord());
            storedChords.remove(id);
            reindex();
        }
    }

    // ---------------------------------------------------------------- conflicts

    /** Every chord bound by more than one handle whose contexts can overlap. */
    public List<Conflict> conflicts() {
        List<Conflict> conflicts = new ArrayList<>();
        for (List<KeybindHandle> sameKey : byKeyCode.values()) {
            Map<Integer, List<KeybindHandle>> byMods = new LinkedHashMap<>();
            for (KeybindHandle handle : sameKey) {
                byMods.computeIfAbsent(handle.chord().modifiers(), k -> new ArrayList<>()).add(handle);
            }
            for (List<KeybindHandle> group : byMods.values()) {
                List<KeybindHandle> overlapping = overlappingContexts(group);
                if (overlapping.size() > 1) {
                    conflicts.add(new Conflict(overlapping.get(0).chord(), List.copyOf(overlapping)));
                }
            }
        }
        return List.copyOf(conflicts);
    }

    public List<Conflict> conflictsFor(KeybindHandle handle) {
        return conflicts().stream().filter(c -> c.handles().contains(handle)).toList();
    }

    private static List<KeybindHandle> overlappingContexts(List<KeybindHandle> group) {
        List<KeybindHandle> overlapping = new ArrayList<>();
        for (KeybindHandle candidate : group) {
            for (KeybindHandle other : group) {
                if (candidate != other && candidate.context().overlaps(other.context())) {
                    overlapping.add(candidate);
                    break;
                }
            }
        }
        return overlapping;
    }

    // ---------------------------------------------------------------- dispatch

    public void setContext(KeyContext context) {
        this.currentContext = context;
    }

    public KeyContext context() {
        return currentContext;
    }

    public void setDoubleTapWindowMillis(long millis) {
        this.doubleTapWindowMillis = millis;
    }

    /**
     * Feed straight from the GLFW key callback. Mouse buttons come in through
     * {@link #onMouseButton(int, boolean, int)}.
     *
     * @param glfwAction 0 = release, 1 = press, 2 = repeat
     * @return true when a bind consumed the input and the game should not see it
     */
    public boolean onKey(int keyCode, int glfwAction, int rawMods) {
        if (glfwAction == 2) {
            return false; // auto-repeat is not a new press; HOLD is driven by tick()
        }
        return glfwAction == 1
                ? dispatchPress(keyCode, KeyChord.normaliseMods(rawMods))
                : dispatchRelease(keyCode);
    }

    public boolean onMouseButton(int button, boolean pressed, int rawMods) {
        int code = Keys.MOUSE_OFFSET + button;
        return pressed ? dispatchPress(code, KeyChord.normaliseMods(rawMods)) : dispatchRelease(code);
    }

    private boolean dispatchPress(int keyCode, int mods) {
        List<KeybindHandle> candidates = byKeyCode.get(keyCode);
        if (candidates == null) {
            return false;
        }
        boolean consumed = false;
        long now = clock.getAsLong();
        for (KeybindHandle handle : candidates) {
            if (!handle.isEnabled()
                    || handle.chord().modifiers() != mods
                    || !handle.context().isActiveIn(currentContext)) {
                continue;
            }
            handle.setPressed(true);
            switch (handle.activation()) {
                case PRESS -> handle.fire();
                case TOGGLE -> {
                    handle.toggle();
                    handle.fire();
                }
                case DOUBLE_TAP -> {
                    long elapsedMillis = (now - handle.lastPressNanos()) / 1_000_000L;
                    if (handle.lastPressNanos() != Long.MIN_VALUE && elapsedMillis <= doubleTapWindowMillis) {
                        handle.fire();
                        handle.setLastPressNanos(Long.MIN_VALUE);
                    } else {
                        handle.setLastPressNanos(now);
                    }
                }
                case HOLD, RELEASE -> {
                    // handled by tick() / dispatchRelease()
                }
            }
            if (handle.consumesInput()) {
                return true;
            }
            consumed = true;
        }
        return consumed;
    }

    /** Matches on key code only — never on modifiers. See the class javadoc. */
    private boolean dispatchRelease(int keyCode) {
        List<KeybindHandle> candidates = byKeyCode.get(keyCode);
        if (candidates == null) {
            return false;
        }
        boolean consumed = false;
        for (KeybindHandle handle : candidates) {
            if (!handle.isPressed()) {
                continue;
            }
            handle.setPressed(false);
            if (handle.activation() == Activation.RELEASE) {
                handle.fire();
            }
            consumed |= handle.consumesInput();
        }
        return consumed;
    }

    /** Fires HOLD binds. Call once per client tick. */
    public void tick() {
        for (KeybindHandle handle : byId.values()) {
            if (handle.activation() == Activation.HOLD && handle.isPressed()
                    && handle.isEnabled() && handle.context().isActiveIn(currentContext)) {
                handle.fire();
            }
        }
    }

    /**
     * Clears all pressed state. Call from the GLFW window focus callback: without it, alt-tabbing
     * while a bind is held leaves it held until the key is pressed and released again.
     */
    public void releaseAll() {
        byId.values().forEach(handle -> handle.setPressed(false));
    }

    // ---------------------------------------------------------------- persistence

    public void load(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            props.load(in);
        }
        for (String id : props.stringPropertyNames()) {
            String value = props.getProperty(id);
            storedChords.put(id, value);
            KeybindHandle handle = byId.get(id);
            if (handle != null) {
                handle.setChord(KeyChord.parse(value));
            }
        }
        reindex();
    }

    /**
     * Writes every known binding, including ones belonging to modules that are not currently
     * loaded. Dropping those is how users lose their setup after temporarily disabling a mod.
     */
    public void save(Path file) throws IOException {
        Properties props = new Properties();
        storedChords.forEach(props::setProperty);
        for (KeybindHandle handle : byId.values()) {
            props.setProperty(handle.id(), handle.chord().format());
        }
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "keybindings — id = chord, ids are namespaced by module");
        }
    }

    // ---------------------------------------------------------------- queries

    public Optional<KeybindHandle> handle(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<KeybindHandle> handles() {
        return List.copyOf(byId.values());
    }

    public List<KeybindHandle> handlesOf(String owner) {
        return byId.values().stream().filter(h -> h.owner().equals(owner)).toList();
    }
}
