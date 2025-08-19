package cga.exercise.components.blackscreen

import cga.exercise.components.shader.ShaderProgram
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import org.lwjgl.opengl.GL11.*

class FadeOverlay {
    private val shader = ShaderProgram(
        "assets/shaders/fade_vert.glsl",
        "assets/shaders/fade_frag.glsl"
    )

    private var vao = 0
    private var vbo = 0

    init {
        val verts = floatArrayOf(
            -1f, -1f,
            3f, -1f,
            -1f,  3f
        )
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, verts, GL_STATIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0L)
        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
    }

    fun render(alpha: Float) {
        if (alpha <= 0f) return
        glDisable(GL_DEPTH_TEST)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        shader.use()
        shader.setUniform("u_alpha", alpha)
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glBindVertexArray(0)

        glDisable(GL_BLEND)
        glEnable(GL_DEPTH_TEST)
    }

    fun cleanup() {
        if (vbo != 0) glDeleteBuffers(vbo)
        if (vao != 0) glDeleteVertexArrays(vao)
        shader.cleanup()
    }
}
