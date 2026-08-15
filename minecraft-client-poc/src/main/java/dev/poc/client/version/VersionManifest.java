package dev.poc.client.version;

import java.util.List;

/**
 * Everything needed to stand up one Minecraft version, resolved ahead of time.
 *
 * <p>{@code mappings} matters as much as the jar: modules are written against a stable façade, and
 * the per-version adapter is what translates façade calls into that version's obfuscated names. The
 * manifest records which mapping flavour the adapter was built against so a mismatch is caught at
 * resolve time rather than as a {@code NoSuchMethodError} three minutes into a session.
 */
public record VersionManifest(String id,
                              String javaMajor,
                              MappingFlavour mappings,
                              Artifact clientJar,
                              List<Artifact> libraries,
                              List<Artifact> natives,
                              String assetIndexId,
                              Artifact assetIndex) {

    public enum MappingFlavour {
        /** Mojang's official obfuscation maps (1.14.4+). Simplest, but licence-encumbered. */
        MOJMAP,
        /** Fabric intermediary + Yarn names. Stable across snapshots. */
        YARN,
        /** MCP/SRG, the Forge lineage. The only realistic option for 1.8.9-era versions. */
        SRG
    }

    /** A downloadable file, identified by content hash so the store can deduplicate it. */
    public record Artifact(String path, String sha1, long size, String url) {
    }

    /** Total bytes, used to drive a real progress bar rather than a spinner. */
    public long totalBytes() {
        long total = clientJar.size() + assetIndex.size();
        for (Artifact library : libraries) {
            total += library.size();
        }
        for (Artifact native_ : natives) {
            total += native_.size();
        }
        return total;
    }
}
