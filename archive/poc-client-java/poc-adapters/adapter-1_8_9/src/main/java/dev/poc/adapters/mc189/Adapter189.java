package dev.poc.adapters.mc189;

import dev.poc.api.game.GameAdapter;
import dev.poc.api.game.GameEnvironment;
import dev.poc.api.module.GameBridge;

/**
 * Adaptateur 1.8.9 — squelette. C'est le cas difficile, et il faut savoir pourquoi avant de s'y
 * engager.
 *
 * <h2>Ce qui change par rapport à 1.20.1</h2>
 * <ul>
 *   <li><b>LWJGL 2.</b> 1.8.9 appelle {@code org.lwjgl.opengl.Display},
 *       {@code org.lwjgl.input.Keyboard} et {@code Mouse}, qui n'existent plus en LWJGL 3. Comme
 *       le shell impose LWJGL 3 (contrainte JNI : un natif ne se charge qu'une fois par JVM), il
 *       faut fournir des <b>shims</b> portant ces noms exacts et réimplémentés sur GLFW. D'où la
 *       liste {@code LOCAL_OVERRIDES} de {@code VersionClassLoader}, qui force ces classes à être
 *       chargées localement malgré le préfixe {@code org.lwjgl.} normalement parent-first.</li>
 *   <li><b>Pipeline fixe.</b> 1.8.9 utilise glBegin/glEnd et la matrix stack. En profil core
 *       OpenGL 3.3, rien de tout cela n'existe. Deux options : demander un contexte de
 *       compatibilité au démarrage du shell (simple, mais indisponible sur macOS au-delà de
 *       GL 2.1), ou émuler le pipeline fixe (ce que fait {@code lwjgl3ify}).</li>
 *   <li><b>Mappings.</b> Ni Mojmap ni Yarn ne couvrent 1.8.9. Il faut passer par MCP/Searge, dont
 *       la distribution est moins commode et la qualité inégale.</li>
 *   <li><b>Java.</b> 1.8.9 cible Java 8 ; du bytecode 52 tourne sans problème sur une JVM 21, mais
 *       certains appels réflexifs sur des internes du JDK échouent et demandent des correctifs
 *       ponctuels.</li>
 * </ul>
 *
 * <p>Ordre des transformations, imposé : la réécriture LWJGL 2 → 3 passe <b>avant</b> les mixins,
 * puisque ceux-ci ciblent l'API LWJGL 3 résultante. C'est ce que gère
 * {@code BytecodeTransformers.chainFor}.
 *
 * <p>Recommandation : ne pas commencer par cette version. Faire fonctionner deux versions LWJGL 3
 * (1.16.5 et 1.20.1) d'abord, pour valider le mécanisme de bascule et les fuites, puis attaquer
 * 1.8.9 avec un système déjà stable.
 */
public final class Adapter189 implements GameAdapter {

    @Override public String versionId() { return "1.8.9"; }

    @Override public String family() { return "1.8"; }

    @Override
    public GameBridge boot(GameEnvironment env) {
        throw new UnsupportedOperationException(
                "adaptateur 1.8.9 non implémenté : nécessite la passe LWJGL2→LWJGL3 "
                        + "et l'émulation du pipeline fixe (voir docs/03-version-switching.md)");
    }

    @Override public void tick() {}

    @Override public void render(float partialTicks) {}

    @Override public void shutdown() {}
}
