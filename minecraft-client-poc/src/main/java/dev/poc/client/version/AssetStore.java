package dev.poc.client.version;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Content-addressed store shared by every installed version.
 *
 * <p>Versions overlap heavily — LWJGL, Netty, Guava, the whole {@code minecraft/sounds} tree — so
 * storing by SHA-1 rather than per version turns "eight versions installed" from ~8 GB into
 * something closer to 2 GB, and makes the "is this already downloaded?" check a stat call.
 *
 * <p>{@link #materialise} hard-links into the per-version layout the game expects and falls back to
 * copying when the filesystem refuses (different mount, Windows without privilege). Linking is what
 * keeps a version switch from paying a multi-hundred-megabyte copy.
 */
public final class AssetStore {

    private final Path objectsRoot;

    public AssetStore(Path objectsRoot) throws IOException {
        this.objectsRoot = objectsRoot;
        Files.createDirectories(objectsRoot);
    }

    public Path objectPath(String sha1) {
        return objectsRoot.resolve(sha1.substring(0, 2)).resolve(sha1);
    }

    public boolean has(VersionManifest.Artifact artifact) {
        Path path = objectPath(artifact.sha1());
        try {
            return Files.isRegularFile(path) && Files.size(path) == artifact.size();
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Streams {@code source} into the store, hashing as it goes, and refuses content whose digest
     * does not match. Writes to a temp file first so an interrupted download can never be mistaken
     * for a complete object on the next launch — the single most common cause of "reinstall fixed
     * it" bug reports in launchers.
     */
    public Path put(VersionManifest.Artifact artifact, InputStream source) throws IOException {
        Path target = objectPath(artifact.sha1());
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), artifact.sha1(), ".part");

        MessageDigest digest = sha1Digest();
        try {
            try (InputStream in = source; var out = Files.newOutputStream(temp)) {
                byte[] buffer = new byte[1 << 16];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                    out.write(buffer, 0, read);
                }
            }
            String actual = HexFormat.of().formatHex(digest.digest());
            if (!actual.equalsIgnoreCase(artifact.sha1())) {
                throw new IOException("Hash mismatch for " + artifact.path()
                        + ": expected " + artifact.sha1() + ", got " + actual);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return target;
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Links (or copies) a stored object into a version's working layout. */
    public Path materialise(VersionManifest.Artifact artifact, Path versionRoot) throws IOException {
        Path source = objectPath(artifact.sha1());
        Path target = versionRoot.resolve(artifact.path());
        Files.createDirectories(target.getParent());
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            return target;
        }
        try {
            Files.createLink(target, source);
        } catch (IOException | UnsupportedOperationException e) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }

    private static MessageDigest sha1Digest() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required by the JLS platform spec", e);
        }
    }
}
