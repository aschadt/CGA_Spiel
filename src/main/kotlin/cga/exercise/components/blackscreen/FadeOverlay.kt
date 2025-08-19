package cga.exercise.components.blackscreen

import cga.exercise.components.shader.ShaderProgram
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

class FadeOverlay {
    private val shader = ShaderProgram(
        "assets/shaders/Black Screen/fade_vert.glsl",
        "assets/shaders/Black Screen/fade_frag.glsl"
    )

    private val vao: Int
    private val vbo: Int
    private val uAlphaLoc: Int

    init {
        // Fullscreen-Triangle (deckt ohne Präzisionsprobleme den ganzen Screen)
        val verts = floatArrayOf(
            -1f, -1f,
            3f, -1f,
            -1f,  3f
        )

        vao = glGenVertexArrays()
        glBindVertexArray(vao)

        vbo = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW)

        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0L)

        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)

        uAlphaLoc = glGetUniformLocation(shader.programID, "uAlpha")
    }

    fun draw(alpha: Float) {
        // GL-State: Overlay muss mischen + nicht vom Depth-Test/Culling blockiert werden
        glDisable(GL_CULL_FACE)
        glDisable(GL_DEPTH_TEST)
        glDepthMask(false)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        shader.use()
        glUniform1f(uAlphaLoc, alpha.coerceIn(0f, 1f))

        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glBindVertexArray(0)

        // State zurücksetzen (damit die restliche Engine nicht leidet)
        glDisable(GL_BLEND)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
    }

    fun cleanup() {
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        shader.cleanup()
    }
}
