package dev.poc.core.module;

import dev.poc.api.module.ModuleMetadata;
import dev.poc.core.util.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Découverte des modules : scan de {@code modules/*.jar} et lecture de {@code module.json}. */
public final class ModuleDiscovery {

    public record Candidate(Path jar, ModuleMetadata metadata) {}

    private ModuleDiscovery() {}

    public static List<Candidate> scan(Path modulesDir) {
        if (!Files.isDirectory(modulesDir)) return List.of();
        List<Candidate> found = new ArrayList<>();
        try (Stream<Path> paths = Files.list(modulesDir)) {
            for (Path jar : paths.filter(p -> p.toString().endsWith(".jar")).sorted().toList()) {
                try {
                    found.add(new Candidate(jar, readMetadata(jar)));
                } catch (Exception e) {
                    // Un jar corrompu ne doit pas empêcher le chargement des autres modules.
                    System.getLogger("modules").log(System.Logger.Level.WARNING,
                            "module ignoré (" + jar.getFileName() + "): " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return found;
    }

    public static ModuleMetadata readMetadata(Path jar) throws IOException {
        try (JarFile jf = new JarFile(jar.toFile())) {
            var entry = jf.getJarEntry("module.json");
            if (entry == null) throw new IOException("module.json absent");
            try (var in = jf.getInputStream(entry)) {
                return fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    public static ModuleMetadata fromJson(String json) {
        Map<String, Object> o = Json.parseObject(json);
        return new ModuleMetadata(
                Json.str(o, "id", null),
                Json.str(o, "version", "0.0.0"),
                Json.str(o, "name", Json.str(o, "id", "?")),
                Json.str(o, "description", ""),
                Json.str(o, "entrypoint", null),
                Json.str(o, "apiVersion", "0.1"),
                Json.strList(o, "authors"),
                Json.strList(o, "gameVersions"),
                Json.strMap(o, "depends"),
                Json.strMap(o, "suggests"),
                Json.strMap(o, "conflicts"),
                Json.bool(o, "isolated", true));
    }
}
