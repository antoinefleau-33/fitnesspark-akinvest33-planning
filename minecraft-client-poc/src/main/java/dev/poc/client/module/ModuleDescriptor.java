package dev.poc.client.module;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Parsed contents of a module's {@code module.properties} manifest.
 *
 * <pre>
 * id            = example-hud
 * name          = Example HUD
 * version       = 1.0.0
 * main          = com.example.hud.HudModule
 * api-version   = 1
 * depends       = core-utils, shader-api
 * soft-depends  = fancy-fonts
 * </pre>
 *
 * <p>Properties instead of JSON keeps the PoC dependency-free. Swapping in gson/jankson is a
 * one-method change ({@link #read(InputStream)}); the rest of the loader never sees the format.
 */
public record ModuleDescriptor(String id,
                               String name,
                               String version,
                               String mainClass,
                               int apiVersion,
                               List<String> depends,
                               List<String> softDepends) {

    public static final String MANIFEST_NAME = "module.properties";

    /** API level this client implements. A module asking for a higher level is refused. */
    public static final int CURRENT_API_VERSION = 1;

    public ModuleDescriptor {
        depends = List.copyOf(depends);
        softDepends = List.copyOf(softDepends);
    }

    public static ModuleDescriptor read(InputStream in) throws IOException {
        Properties props = new Properties();
        props.load(in);

        String id = require(props, "id");
        if (!id.matches("[a-z0-9][a-z0-9-_]{1,63}")) {
            throw new IOException("Illegal module id '" + id + "' (expected [a-z0-9-_], 2-64 chars)");
        }
        return new ModuleDescriptor(
                id,
                props.getProperty("name", id),
                props.getProperty("version", "0.0.0"),
                require(props, "main"),
                Integer.parseInt(props.getProperty("api-version", "1").trim()),
                splitList(props.getProperty("depends")),
                splitList(props.getProperty("soft-depends")));
    }

    private static String require(Properties props, String key) throws IOException {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IOException("Missing required key '" + key + "' in " + MANIFEST_NAME);
        }
        return value.trim();
    }

    private static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    /** All dependencies, hard and soft, in one list — used for classloader delegation. */
    public List<String> allDependencies() {
        List<String> all = new ArrayList<>(depends);
        all.addAll(softDepends);
        return all;
    }
}
