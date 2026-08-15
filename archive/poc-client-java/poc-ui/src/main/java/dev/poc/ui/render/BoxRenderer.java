package dev.poc.ui.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Rendu instancié de boîtes filaires dans l'espace du monde.
 *
 * <p>Géométrie de base : les 12 arêtes d'un cube unitaire (24 sommets, {@code GL_LINES}), envoyées
 * une seule fois. Chaque boîte est une instance de 10 floats. 5 000 boîtes = 1 draw call, ~200 Ko
 * de données par frame.
 *
 * <h2>Le piège des grandes coordonnées</h2>
 * Les positions sont <b>relatives à la caméra</b>, pas absolues. Un {@code float} a 24 bits de
 * mantisse : à x = 1 000 000, deux valeurs consécutives représentables sont distantes de ~0,06
 * bloc. Une boîte dessinée en coordonnées absolues tremble donc visiblement dès qu'on s'éloigne du
 * spawn, et le tremblement dépend de l'angle de vue — un bug classique et déroutant. Minecraft
 * résout ça de la même façon ({@code poseStack.translate(-camX, -camY, -camZ)}).
 *
 * <h2>Épaisseur de trait</h2>
 * {@code glLineWidth} au-delà de 1.0 n'est pas garanti en profil core : la spec autorise une plage
 * {@code [1,1]} et les pilotes AMD/Intel s'y tiennent souvent, là où NVIDIA accepte des valeurs
 * plus grandes. Un trait « épais » qui ne l'est que sur une carte sur trois n'est pas exploitable.
 * Pour une épaisseur fiable il faut extruder des quads face à la caméra dans le vertex shader ;
 * ici on assume le trait d'un pixel, suffisant pour du diagnostic.
 */
public final class BoxRenderer implements AutoCloseable {

    /** floats par instance : origine(3) + taille(3) + couleur(4) */
    private static final int FLOATS_PER_INSTANCE = 10;
    private static final int MAX_INSTANCES = 16384;

    private final int program;
    private final int vao;
    private final int geometryVbo;
    private final int instanceVbo;
    private final FloatBuffer instances;

    private final int uViewProj;
    private final int uTint;

    private int count;

    private static final String VERTEX_SRC = """
            #version 330 core
            layout(location = 0) in vec3 aCorner;    // coin du cube unitaire, 0 ou 1 par axe

            layout(location = 1) in vec3 iOrigin;    // coin min, RELATIF À LA CAMÉRA
            layout(location = 2) in vec3 iSize;
            layout(location = 3) in vec4 iColor;

            uniform mat4 uViewProj;   // view-projection, translation caméra déjà retirée

            out vec4 vColor;

            void main() {
                vec3 pos = iOrigin + aCorner * iSize;
                gl_Position = uViewProj * vec4(pos, 1.0);
                vColor = iColor;
            }
            """;

    private static final String FRAGMENT_SRC = """
            #version 330 core
            in vec4 vColor;
            uniform vec4 uTint;      // multiplicateur de passe : 1.0 en visible, ~0.35 en occlusé
            out vec4 fragColor;

            void main() {
                fragColor = vColor * uTint;
            }
            """;

    /** Les 12 arêtes du cube unitaire, en paires de sommets. */
    private static final float[] EDGES = {
            // face inférieure
            0,0,0,  1,0,0,   1,0,0,  1,0,1,   1,0,1,  0,0,1,   0,0,1,  0,0,0,
            // face supérieure
            0,1,0,  1,1,0,   1,1,0,  1,1,1,   1,1,1,  0,1,1,   0,1,1,  0,1,0,
            // montants
            0,0,0,  0,1,0,   1,0,0,  1,1,0,   1,0,1,  1,1,1,   0,0,1,  0,1,1
    };

    public BoxRenderer() {
        program = ShaderProgram.compile(VERTEX_SRC, FRAGMENT_SRC);
        uViewProj = glGetUniformLocation(program, "uViewProj");
        uTint = glGetUniformLocation(program, "uTint");

        // VAO dédié. Indispensable : Minecraft lie ses propres VAO, et modifier l'état de vertex
        // attribs sans VAO à soi corromprait son rendu à la frame suivante.
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        geometryVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, geometryVbo);
        glBufferData(GL_ARRAY_BUFFER, EDGES, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0L);

        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER,
                (long) MAX_INSTANCES * FLOATS_PER_INSTANCE * Float.BYTES, GL_STREAM_DRAW);

        int stride = FLOATS_PER_INSTANCE * Float.BYTES;
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 0L);
        glVertexAttribDivisor(1, 1);
        glEnableVertexAttribArray(2);
        glVertexAttribPointer(2, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glVertexAttribDivisor(2, 1);
        glEnableVertexAttribArray(3);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glVertexAttribDivisor(3, 1);

        glBindVertexArray(0);
        instances = MemoryUtil.memAllocFloat(MAX_INSTANCES * FLOATS_PER_INSTANCE);
    }

    public void begin() {
        count = 0;
        instances.clear();
    }

    /** Coordonnées déjà relatives à la caméra. */
    public void addRelative(float minX, float minY, float minZ,
                            float sizeX, float sizeY, float sizeZ, int argb) {
        if (count >= MAX_INSTANCES) return;   // saturation silencieuse : voir pendingPrimitives()
        instances.put(minX).put(minY).put(minZ);
        instances.put(sizeX).put(sizeY).put(sizeZ);
        instances.put(((argb >>> 16) & 0xFF) / 255f)
                 .put(((argb >>> 8) & 0xFF) / 255f)
                 .put((argb & 0xFF) / 255f)
                 .put(((argb >>> 24) & 0xFF) / 255f);
        count++;
    }

    public int count() { return count; }

    public boolean isFull() { return count >= MAX_INSTANCES; }

    /**
     * Émet les draw calls.
     *
     * @param dimmedPass si vrai, effectue en plus une passe pour les parties occluses, atténuée,
     *                   avec {@code glDepthFunc(GL_GREATER)}
     * @param throughWalls si vrai, une seule passe sans test de profondeur
     */
    public void draw(float[] viewProjection, boolean dimmedPass, boolean throughWalls) {
        if (count == 0) return;
        instances.flip();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        // Orphaning : réallouer avant d'écrire évite d'attendre que le GPU ait fini de lire le
        // contenu précédent (sinon, stall visible dès quelques milliers d'instances).
        glBufferData(GL_ARRAY_BUFFER,
                (long) MAX_INSTANCES * FLOATS_PER_INSTANCE * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instances);

        glUseProgram(program);
        glUniformMatrix4fv(uViewProj, false, viewProjection);

        if (throughWalls) {
            glDisable(GL_DEPTH_TEST);
            glUniform4f(uTint, 1f, 1f, 1f, 1f);
            glDrawArraysInstanced(GL_LINES, 0, 24, count);
        } else {
            glEnable(GL_DEPTH_TEST);
            if (dimmedPass) {
                // Passe 1 — ce qui est CACHÉ. GL_GREATER ne garde que les fragments plus loin que
                // le terrain déjà écrit. Écriture de profondeur coupée pour ne pas polluer le
                // depth buffer avec des lignes situées derrière le décor.
                glDepthFunc(GL_GREATER);
                glDepthMask(false);
                glUniform4f(uTint, 1f, 1f, 1f, 0.28f);
                glDrawArraysInstanced(GL_LINES, 0, 24, count);
            }
            // Passe 2 — ce qui est VISIBLE, en pleine intensité.
            glDepthFunc(GL_LEQUAL);
            glDepthMask(true);
            glUniform4f(uTint, 1f, 1f, 1f, 1f);
            glDrawArraysInstanced(GL_LINES, 0, 24, count);
        }

        glBindVertexArray(0);
        instances.clear();
        count = 0;
    }

    @Override
    public void close() {
        MemoryUtil.memFree(instances);
        glDeleteBuffers(geometryVbo);
        glDeleteBuffers(instanceVbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
