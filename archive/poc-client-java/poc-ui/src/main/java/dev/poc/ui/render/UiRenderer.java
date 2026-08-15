package dev.poc.ui.render;

import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Renderer 2D par instanciation.
 *
 * <p><b>Un seul draw call pour toute l'interface.</b> Chaque primitive (panneau, bouton, ombre,
 * séparateur) est une instance de 20 floats poussée dans un VBO ; à la fin de la frame, un
 * {@code glDrawArraysInstanced} dessine le tout. Une UI façon Lunar comporte facilement 300
 * quads : en mode immédiat façon Minecraft vanilla, c'est 300 changements d'état et autant de
 * draw calls, soit ~2 ms de temps CPU pilote gaspillées par frame. Ici, une poignée de
 * microsecondes.
 *
 * <p>La forme est décrite analytiquement dans le fragment shader par une <b>SDF de rectangle
 * arrondi</b>. Conséquences : coins parfaitement lisses à n'importe quelle échelle sans MSAA,
 * bordures et ombres portées quasi gratuites (un décalage de la même distance signée), et aucune
 * texture d'atlas à gérer pour les formes.
 */
public final class UiRenderer implements AutoCloseable {

    /** floats par instance : rect(4) + radius(4) + couleur(4) + bordure(5) + ombre(3) */
    private static final int FLOATS_PER_INSTANCE = 20;
    private static final int MAX_INSTANCES = 8192;

    private final int program;
    private final int vao;
    private final int quadVbo;
    private final int instanceVbo;
    private final FloatBuffer instanceData;

    private final int uProjection;
    private int instanceCount;
    private int viewportWidth, viewportHeight;

    private static final String VERTEX_SRC = """
            #version 330 core
            // Quad unitaire, étendu par instance. Aucune géométrie n'est envoyée par frame.
            layout(location = 0) in vec2 aCorner;

            layout(location = 1) in vec4 iRect;      // x, y, w, h  (espace écran, pixels)
            layout(location = 2) in vec4 iRadius;    // rayons par coin : TL, TR, BR, BL
            layout(location = 3) in vec4 iColor;     // RGBA prémultipliée
            layout(location = 4) in vec4 iBorder;    // rgb bordure + épaisseur en w
            layout(location = 5) in vec4 iShadow;    // rayon flou, offsetX, offsetY, alpha

            uniform mat4 uProjection;

            out vec2 vLocal;        // position dans le rect, centrée
            out vec2 vHalfSize;
            out vec4 vRadius;
            out vec4 vColor;
            out vec4 vBorder;
            out vec4 vShadow;

            void main() {
                // On dilate le quad pour laisser la place à l'ombre : sans marge, le flou est
                // coupé net au bord de la géométrie — l'artefact le plus courant en SDF UI.
                float pad = iShadow.x + max(abs(iShadow.y), abs(iShadow.z)) + 1.0;
                vec2 size = iRect.zw + vec2(pad * 2.0);
                vec2 origin = iRect.xy - vec2(pad);
                vec2 pos = origin + aCorner * size;

                vHalfSize = iRect.zw * 0.5;
                vLocal = pos - (iRect.xy + vHalfSize);
                vRadius = iRadius;
                vColor = iColor;
                vBorder = iBorder;
                vShadow = iShadow;

                gl_Position = uProjection * vec4(pos, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SRC = """
            #version 330 core
            in vec2 vLocal;
            in vec2 vHalfSize;
            in vec4 vRadius;
            in vec4 vColor;
            in vec4 vBorder;
            in vec4 vShadow;

            out vec4 fragColor;

            // Distance signée à un rectangle à coins arrondis indépendants.
            float sdRoundedBox(vec2 p, vec2 b, vec4 r) {
                // Sélection du rayon du quadrant courant : deux mix au lieu de branches.
                r.xy = (p.x > 0.0) ? r.yz : r.xw;
                r.x  = (p.y > 0.0) ? r.x  : r.y;
                vec2 q = abs(p) - b + r.x;
                return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r.x;
            }

            void main() {
                float d = sdRoundedBox(vLocal, vHalfSize, vRadius);

                // fwidth() donne la largeur d'un pixel dans l'espace de la SDF : l'antialiasing
                // reste d'exactement un pixel quel que soit le zoom ou le facteur DPI.
                float aa = fwidth(d);
                float shapeAlpha = 1.0 - smoothstep(-aa, aa, d);

                // Ombre portée : même SDF, décalée et élargie. Coût : ~4 instructions.
                float sd = sdRoundedBox(vLocal - vShadow.yz, vHalfSize, vRadius);
                float shadowAlpha = (1.0 - smoothstep(-vShadow.x, vShadow.x, sd)) * vShadow.w;
                shadowAlpha *= (1.0 - shapeAlpha);   // pas d'ombre sous une forme opaque

                // Bordure interne : bande de largeur vBorder.w le long du contour.
                float borderAlpha = 0.0;
                if (vBorder.w > 0.0) {
                    borderAlpha = (1.0 - smoothstep(-aa, aa, d + vBorder.w)) ;
                    borderAlpha = shapeAlpha - borderAlpha;
                }

                vec3 rgb = mix(vColor.rgb, vBorder.rgb, clamp(borderAlpha, 0.0, 1.0));
                float alpha = max(shapeAlpha * vColor.a, shadowAlpha);
                if (alpha < 0.001) discard;
                fragColor = vec4(rgb, alpha);
            }
            """;

    public UiRenderer() {
        program = ShaderProgram.compile(VERTEX_SRC, FRAGMENT_SRC);
        uProjection = glGetUniformLocation(program, "uProjection");

        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        // Quad unitaire statique, deux triangles en strip.
        quadVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, new float[]{0, 0, 1, 0, 0, 1, 1, 1}, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * Float.BYTES, 0L);

        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER,
                (long) MAX_INSTANCES * FLOATS_PER_INSTANCE * Float.BYTES, GL_STREAM_DRAW);

        int stride = FLOATS_PER_INSTANCE * Float.BYTES;
        for (int i = 0; i < 5; i++) {
            int loc = 1 + i;
            glEnableVertexAttribArray(loc);
            glVertexAttribPointer(loc, 4, GL_FLOAT, false, stride, (long) i * 4 * Float.BYTES);
            glVertexAttribDivisor(loc, 1);   // une valeur par instance, pas par sommet
        }

        glBindVertexArray(0);
        instanceData = MemoryUtil.memAllocFloat(MAX_INSTANCES * FLOATS_PER_INSTANCE);
    }

    public void begin(int width, int height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
        instanceCount = 0;
        instanceData.clear();
    }

    /** Empile une primitive. Rien n'est envoyé au GPU avant {@link #flush()}. */
    public void rect(float x, float y, float w, float h,
                     float radius, int argb,
                     int borderArgb, float borderWidth,
                     float shadowBlur, float shadowOffsetY, float shadowAlpha) {
        if (instanceCount >= MAX_INSTANCES) flush();

        instanceData.put(x).put(y).put(w).put(h);
        instanceData.put(radius).put(radius).put(radius).put(radius);
        putColor(argb);
        putRgb(borderArgb);
        instanceData.put(borderWidth);
        instanceData.put(shadowBlur).put(0f).put(shadowOffsetY).put(shadowAlpha);
        instanceCount++;
    }

    public void rect(float x, float y, float w, float h, float radius, int argb) {
        rect(x, y, w, h, radius, argb, 0, 0f, 0f, 0f, 0f);
    }

    private void putColor(int argb) {
        instanceData.put(((argb >>> 16) & 0xFF) / 255f)
                .put(((argb >>> 8) & 0xFF) / 255f)
                .put((argb & 0xFF) / 255f)
                .put(((argb >>> 24) & 0xFF) / 255f);
    }

    private void putRgb(int argb) {
        instanceData.put(((argb >>> 16) & 0xFF) / 255f)
                .put(((argb >>> 8) & 0xFF) / 255f)
                .put((argb & 0xFF) / 255f);
    }

    public void flush() {
        if (instanceCount == 0) return;
        instanceData.flip();

        glUseProgram(program);
        glUniformMatrix4fv(uProjection, false, orthoProjection(viewportWidth, viewportHeight));

        glEnable(GL_BLEND);
        glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_DEPTH_TEST);

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        // orphaning : on invalide le buffer précédent pour que le pilote n'attende pas la fin de
        // la frame en cours (sinon, stall CPU/GPU visible dès ~1000 instances).
        glBufferData(GL_ARRAY_BUFFER,
                (long) MAX_INSTANCES * FLOATS_PER_INSTANCE * Float.BYTES, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, instanceData);

        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, instanceCount);

        glBindVertexArray(0);
        instanceData.clear();
        instanceCount = 0;
    }

    private static float[] orthoProjection(int w, int h) {
        // Origine en haut à gauche, y vers le bas — convention écran.
        return new float[]{
                2f / w, 0, 0, 0,
                0, -2f / h, 0, 0,
                0, 0, -1, 0,
                -1, 1, 0, 1
        };
    }

    @Override
    public void close() {
        MemoryUtil.memFree(instanceData);
        glDeleteBuffers(quadVbo);
        glDeleteBuffers(instanceVbo);
        glDeleteVertexArrays(vao);
        glDeleteProgram(program);
    }
}
