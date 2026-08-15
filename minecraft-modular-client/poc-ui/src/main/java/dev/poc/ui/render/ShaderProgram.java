package dev.poc.ui.render;

import static org.lwjgl.opengl.GL33C.*;

public final class ShaderProgram {

    private ShaderProgram() {}

    public static int compile(String vertexSrc, String fragmentSrc) {
        int vs = compileStage(GL_VERTEX_SHADER, vertexSrc);
        int fs = compileStage(GL_FRAGMENT_SHADER, fragmentSrc);
        int program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new IllegalStateException("édition de liens du shader: " + glGetProgramInfoLog(program));
        }
        // Détacher puis supprimer : les objets shader ne sont plus nécessaires une fois liés, et
        // les oublier fait fuir des ressources pilote à chaque rechargement de shader à chaud.
        glDetachShader(program, vs);
        glDetachShader(program, fs);
        glDeleteShader(vs);
        glDeleteShader(fs);
        return program;
    }

    private static int compileStage(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException(
                    (type == GL_VERTEX_SHADER ? "vertex" : "fragment") + " shader: " + log);
        }
        return shader;
    }
}
