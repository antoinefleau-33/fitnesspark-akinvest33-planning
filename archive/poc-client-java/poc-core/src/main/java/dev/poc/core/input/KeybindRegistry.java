package dev.poc.core.input;

import dev.poc.api.input.ActivationContext;
import dev.poc.api.input.Chord;
import dev.poc.api.input.KeyEvent;
import dev.poc.api.input.Keybind;
import dev.poc.api.input.KeybindService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Registre central des raccourcis.
 *
 * <p>Structure clé : une <b>multimap</b> {@code (device, code) -> List<Entry>}. Vanilla utilise
 * {@code Map<InputUtil.Key, KeyBinding>} — un seul binding par touche — d'où la disparition
 * silencieuse des binds de mods qui partagent une touche. Ici toutes les entrées candidates sont
 * conservées et c'est le résolveur qui décide, à chaque appui, laquelle (ou lesquelles) servir.
 *
 * <p>Second point : l'index primaire est {@code id -> Entry} avec un id namespacé obligatoire.
 * Une collision d'id lève une exception au chargement du module. Impossible d'avoir deux
 * {@code "key.anything"} qui s'écrasent.
 *
 * <p>Non thread-safe par conception : tout passe par le thread de rendu/input. Les enregistrements
 * faits depuis un autre thread doivent être postés via {@code InputPipeline#runOnInputThread}.
 */
public final class KeybindRegistry implements KeybindService {

    /** Entrée interne : la définition + le handler + l'état d'exécution. */
    static final class Entry {
        final Keybind bind;
        final Consumer<KeyEvent> handler;
        final long sequence;      // ordre d'enregistrement : départage stable et déterministe
        Chord effectiveChord;     // défaut, ou override du profil utilisateur

        // État d'exécution
        boolean down;
        boolean toggled;
        long pressedAtNanos;
        long lastPressNanos;
        boolean longPressFired;

        Entry(Keybind bind, Consumer<KeyEvent> handler, long sequence, Chord effectiveChord) {
            this.bind = bind;
            this.handler = handler;
            this.sequence = sequence;
            this.effectiveChord = effectiveChord;
        }

        void resetState() {
            down = false;
            pressedAtNanos = 0L;
            longPressFired = false;
            // 'toggled' est volontairement conservé : un toggle survit à une perte de focus.
        }
    }

    private final Map<String, Entry> byId = new LinkedHashMap<>();
    private final Map<Long, List<Entry>> byPhysicalKey = new HashMap<>();
    private final Map<String, Chord> userProfile = new HashMap<>();
    private final AtomicLong sequencer = new AtomicLong();

    private Runnable onChanged = () -> {};

    /** Clé d'indexation physique : device + code, SANS les modificateurs. */
    private static long physicalKey(Chord c) {
        return ((long) c.device().ordinal() << 32) | (c.code() & 0xFFFFFFFFL);
    }

    public void setChangeListener(Runnable listener) {
        this.onChanged = listener == null ? () -> {} : listener;
    }

    @Override
    public Handle register(Keybind bind, Consumer<KeyEvent> handler) {
        Entry existing = byId.get(bind.id());
        if (existing != null) {
            throw new DuplicateKeybindException(
                    "keybind '" + bind.id() + "' déjà enregistré par le namespace '"
                            + existing.bind.namespace() + "'. Les ids doivent être uniques ; "
                            + "utilisez '<votreModuleId>:<action>'.");
        }
        Chord effective = userProfile.getOrDefault(bind.id(), bind.defaultChord());
        Entry entry = new Entry(bind, handler, sequencer.incrementAndGet(), effective);
        byId.put(bind.id(), entry);
        index(entry);
        onChanged.run();

        return new Handle() {
            @Override public Keybind bind() { return bind; }
            @Override public void close() { unregister(bind.id()); }
        };
    }

    private void index(Entry e) {
        if (!e.effectiveChord.isBound()) return;
        byPhysicalKey.computeIfAbsent(physicalKey(e.effectiveChord), k -> new ArrayList<>()).add(e);
        sortBucket(byPhysicalKey.get(physicalKey(e.effectiveChord)));
    }

    private void deindex(Entry e) {
        List<Entry> bucket = byPhysicalKey.get(physicalKey(e.effectiveChord));
        if (bucket != null) {
            bucket.remove(e);
            if (bucket.isEmpty()) byPhysicalKey.remove(physicalKey(e.effectiveChord));
        }
    }

    /**
     * Ordre de service, appliqué une fois à l'indexation plutôt qu'à chaque frappe :
     * <ol>
     *   <li><b>spécificité</b> décroissante — {@code CTRL+K} passe avant {@code K} ;</li>
     *   <li><b>priorité</b> décroissante — arbitrage explicite entre modules ;</li>
     *   <li><b>séquence</b> croissante — départage stable, indépendant du hash.</li>
     * </ol>
     */
    private static void sortBucket(List<Entry> bucket) {
        bucket.sort(Comparator
                .comparingInt((Entry e) -> -e.effectiveChord.specificity())
                .thenComparingInt(e -> -e.bind.priority())
                .thenComparingLong(e -> e.sequence));
    }

    public void unregister(String id) {
        Entry e = byId.remove(id);
        if (e != null) {
            deindex(e);
            onChanged.run();
        }
    }

    /** Retire tous les binds d'un namespace — appelé au déchargement d'un module. */
    public void unregisterNamespace(String namespace) {
        List.copyOf(byId.keySet()).stream()
                .filter(id -> id.startsWith(namespace + ":"))
                .forEach(this::unregister);
    }

    @Override
    public void rebind(String keybindId, Chord chord) {
        Entry e = byId.get(keybindId);
        if (e == null) throw new IllegalArgumentException("keybind inconnu: " + keybindId);
        deindex(e);
        e.effectiveChord = chord;
        e.resetState();
        userProfile.put(keybindId, chord);
        index(e);
        onChanged.run();
    }

    @Override
    public Chord chordOf(String keybindId) {
        Entry e = byId.get(keybindId);
        return e == null ? Chord.NONE : e.effectiveChord;
    }

    @Override
    public boolean isActive(String keybindId) {
        Entry e = byId.get(keybindId);
        if (e == null) return false;
        return e.bind.mode() == dev.poc.api.input.ActivationMode.TOGGLE ? e.toggled : e.down;
    }

    @Override
    public List<Keybind> registered() {
        return byId.values().stream().map(e -> e.bind).toList();
    }

    /**
     * Détection de conflits. Un conflit n'est réel que si les contextes se recouvrent : deux binds
     * sur {@code G}, l'un {@code IN_GAME} et l'autre {@code IN_SCREEN}, cohabitent sans problème.
     * Cette nuance manque à la plupart des écrans de contrôles, qui affichent des faux positifs.
     */
    @Override
    public List<Conflict> conflicts() {
        List<Conflict> out = new ArrayList<>();
        for (List<Entry> bucket : byPhysicalKey.values()) {
            Map<Integer, List<Entry>> byMask = new LinkedHashMap<>();
            for (Entry e : bucket) {
                byMask.computeIfAbsent(e.effectiveChord.modifierMask(), k -> new ArrayList<>()).add(e);
            }
            for (var group : byMask.values()) {
                if (group.size() < 2) continue;
                for (int i = 0; i < group.size(); i++) {
                    for (int j = i + 1; j < group.size(); j++) {
                        Entry a = group.get(i);
                        Entry b = group.get(j);
                        ActivationContext ca = a.bind.context();
                        ActivationContext cb = b.bind.context();
                        if (!ca.overlaps(cb)) continue;
                        boolean hard = a.bind.consuming() || b.bind.consuming();
                        out.add(new Conflict(
                                a.effectiveChord,
                                List.of(a.bind.id(), b.bind.id()),
                                hard ? Conflict.Severity.HARD : Conflict.Severity.SOFT));
                    }
                }
            }
        }
        return out;
    }

    // --- Accès interne pour le résolveur ---------------------------------------------------

    List<Entry> candidates(Chord.Device device, int code) {
        return byPhysicalKey.getOrDefault(
                ((long) device.ordinal() << 32) | (code & 0xFFFFFFFFL), List.of());
    }

    java.util.Collection<Entry> all() { return byId.values(); }

    public Map<String, String> exportProfile() {
        Map<String, String> out = new LinkedHashMap<>();
        byId.forEach((id, e) -> out.put(id, e.effectiveChord.serialize()));
        return out;
    }

    /**
     * Import d'un profil. Les ids inconnus sont conservés en attente : un module chargé plus tard
     * (ou au retour d'un changement de version) retrouve son binding personnalisé.
     */
    public void importProfile(Map<String, String> profile) {
        profile.forEach((id, ser) -> {
            Chord chord = Chord.deserialize(ser);
            userProfile.put(id, chord);
            Entry e = byId.get(id);
            if (e != null) {
                deindex(e);
                e.effectiveChord = chord;
                e.resetState();
                index(e);
            }
        });
        onChanged.run();
    }
}
