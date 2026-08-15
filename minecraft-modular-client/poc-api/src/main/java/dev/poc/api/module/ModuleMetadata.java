package dev.poc.api.module;

import java.util.List;
import java.util.Map;

/**
 * Descripteur lu depuis {@code module.json} à la racine du jar.
 *
 * <pre>{@code
 * {
 *   "id": "hud-example",
 *   "version": "1.2.0",
 *   "name": "Exemple HUD",
 *   "entrypoint": "dev.poc.modules.hud.HudModule",
 *   "apiVersion": "0.1",
 *   "gameVersions": [">=1.8.9"],
 *   "depends":  { "core-render": ">=1.0.0 <2.0.0" },
 *   "suggests": { "themes": ">=0.3" },
 *   "conflicts":{ "old-hud": "*" },
 *   "isolated": true
 * }
 * }</pre>
 *
 * @param isolated si {@code true}, le module obtient son propre classloader enfant et ses
 *                 dépendances embarquées ne polluent pas les autres modules. Sinon il partage
 *                 le classloader « plat » (utile pour les modules qui s'étendent mutuellement).
 */
public record ModuleMetadata(
        String id,
        String version,
        String name,
        String description,
        String entrypoint,
        String apiVersion,
        List<String> authors,
        List<String> gameVersions,
        Map<String, String> depends,
        Map<String, String> suggests,
        Map<String, String> conflicts,
        boolean isolated) {

    public ModuleMetadata {
        if (id == null || !id.matches("[a-z0-9][a-z0-9_-]{1,63}")) {
            throw new IllegalArgumentException("id de module invalide: " + id);
        }
        if (entrypoint == null || entrypoint.isBlank()) {
            throw new IllegalArgumentException("entrypoint manquant pour " + id);
        }
        authors = List.copyOf(authors == null ? List.of() : authors);
        gameVersions = List.copyOf(gameVersions == null ? List.of("*") : gameVersions);
        depends = Map.copyOf(depends == null ? Map.of() : depends);
        suggests = Map.copyOf(suggests == null ? Map.of() : suggests);
        conflicts = Map.copyOf(conflicts == null ? Map.of() : conflicts);
    }
}
