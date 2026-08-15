package dev.poc.core.version;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Chaîne de transformation bytecode appliquée au chargement de chaque classe d'une version.
 *
 * <p>C'est le point d'insertion des hooks. Deux approches, à combiner :
 * <ul>
 *   <li><b>Mixin (SpongePowered)</b> pour les hooks structurels — {@code @Inject},
 *       {@code @Redirect}, {@code @ModifyVariable}. Un fichier de config par famille de version,
 *       car les cibles changent : {@code net.minecraft.client.gui.GuiIngame#renderGameOverlay} en
 *       1.8.9 devient {@code net.minecraft.client.gui.Gui#render} en 1.20.1. Le mixin est appliqué
 *       ici, dans le classloader de la version, jamais dans le shell.</li>
 *   <li><b>ASM direct</b> pour les patchs de compatibilité mécaniques et massifs — notamment la
 *       réécriture LWJGL 2 → LWJGL 3 sur les versions ≤ 1.12.2, qui consiste à remplacer des
 *       milliers d'appels {@code org.lwjgl.opengl.Display.*} par des équivalents GLFW. Trop
 *       systématique pour un mixin, c'est un simple {@code ClassRemapper} + un
 *       {@code MethodVisitor} de substitution.</li>
 * </ul>
 *
 * <p>Le core reste sans dépendance : les implémentations concrètes sont fournies par le shell au
 * démarrage via {@link #register}. Cela évite d'imposer ASM à toute la JVM (et donc un conflit
 * potentiel avec l'ASM que Minecraft embarque déjà pour son propre chargeur).
 */
public final class BytecodeTransformers {

    /**
     * @param appliesTo prédicat sur le nom de classe. Filtrer AVANT de parser évite de payer un
     *                  {@code ClassReader}/{@code ClassWriter} sur les ~6000 classes du jeu :
     *                  sur 1.20.1, transformer aveuglément coûte plusieurs secondes de démarrage.
     */
    public record Stage(String name,
                        java.util.function.Predicate<String> appliesTo,
                        Function<byte[], byte[]> transform) {}

    private static final List<Stage> STAGES = new ArrayList<>();

    private BytecodeTransformers() {}

    public static void register(Stage stage) { STAGES.add(stage); }

    public static void clear() { STAGES.clear(); }

    /**
     * Compose les étapes applicables à une version donnée. Retourne l'identité quand aucune étape
     * n'est enregistrée — le POC démarre donc sans ASM.
     */
    public static Function<byte[], byte[]> chainFor(VersionInstall install) {
        List<Stage> applicable = new ArrayList<>(STAGES);
        if (install.requiresLwjglShim()) {
            // Le shim est enregistré par le shell ; on documente seulement l'ordre attendu :
            // il doit passer en premier, avant les mixins, car ceux-ci ciblent l'API LWJGL 3.
            applicable.sort((a, b) -> Boolean.compare(
                    !a.name().startsWith("lwjgl3ify"), !b.name().startsWith("lwjgl3ify")));
        }
        if (applicable.isEmpty()) return Function.identity();

        return bytes -> {
            byte[] current = bytes;
            for (Stage stage : applicable) {
                current = stage.transform().apply(current);
            }
            return current;
        };
    }
}
