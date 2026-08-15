package dev.poc.core.util;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Version sémantique et contraintes de plage, façon Fabric Loader.
 *
 * <p>Contraintes supportées : {@code *}, {@code 1.2.3}, {@code >=1.2}, {@code >1.2.3},
 * {@code <=2.0}, {@code ^1.2.3} (compatible majeur), {@code ~1.2.3} (compatible mineur), et la
 * conjonction par espaces : {@code ">=1.2.0 <2.0.0"}.
 */
public record SemVer(int major, int minor, int patch, String preRelease) implements Comparable<SemVer> {

    public static SemVer parse(String s) {
        String v = s.trim();
        String pre = "";
        int dash = v.indexOf('-');
        if (dash >= 0) { pre = v.substring(dash + 1); v = v.substring(0, dash); }
        int plus = v.indexOf('+');
        if (plus >= 0) v = v.substring(0, plus);   // métadonnées de build ignorées
        String[] parts = v.split("\\.");
        try {
            return new SemVer(
                    parts.length > 0 ? Integer.parseInt(parts[0]) : 0,
                    parts.length > 1 ? Integer.parseInt(parts[1]) : 0,
                    parts.length > 2 ? Integer.parseInt(parts[2]) : 0,
                    pre);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("version invalide: " + s, e);
        }
    }

    @Override
    public int compareTo(SemVer o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) return c;
        c = Integer.compare(minor, o.minor);
        if (c != 0) return c;
        c = Integer.compare(patch, o.patch);
        if (c != 0) return c;
        // Une pré-release précède toujours la version stable correspondante.
        if (preRelease.isEmpty() && o.preRelease.isEmpty()) return 0;
        if (preRelease.isEmpty()) return 1;
        if (o.preRelease.isEmpty()) return -1;
        return preRelease.compareTo(o.preRelease);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (preRelease.isEmpty() ? "" : "-" + preRelease);
    }

    public boolean satisfies(String constraint) {
        if (constraint == null || constraint.isBlank() || "*".equals(constraint.trim())) return true;
        return Arrays.stream(constraint.trim().split("\\s+")).allMatch(this::satisfiesSingle);
    }

    private boolean satisfiesSingle(String c) {
        if ("*".equals(c)) return true;
        if (c.startsWith(">=")) return compareTo(parse(c.substring(2))) >= 0;
        if (c.startsWith("<=")) return compareTo(parse(c.substring(2))) <= 0;
        if (c.startsWith(">"))  return compareTo(parse(c.substring(1))) > 0;
        if (c.startsWith("<"))  return compareTo(parse(c.substring(1))) < 0;
        if (c.startsWith("^")) {
            SemVer base = parse(c.substring(1));
            return compareTo(base) >= 0 && major == base.major;
        }
        if (c.startsWith("~")) {
            SemVer base = parse(c.substring(1));
            return compareTo(base) >= 0 && major == base.major && minor == base.minor;
        }
        if (c.startsWith("=")) return compareTo(parse(c.substring(1))) == 0;
        return compareTo(parse(c)) == 0;
    }

    public static final Comparator<SemVer> DESCENDING = Comparator.reverseOrder();
}
