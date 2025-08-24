package cga.exercise.components.blackscreen

import cga.exercise.components.shader.ShaderProgram
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL30.*

class FadeOverlay {
    private val shader = ShaderProgram(
        "assets/shaders/Black Screen/fade_vert.glsl",
        "assets/shaders/Black Screen/fade_frag.glsl"
    )

    // In Core-Profile muss irgendein VAO gebunden sein – aber kein VBO nötig.
    private val vao: Int = glGenVertexArrays()

    fun draw(alpha: Float) {
        val a = alpha.coerceIn(0f, 1f)
        if (a <= 0f) return  // trivialer Shortcut

        // UI-Overlay-States (kurz & bündig)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glDepthMask(false)

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        shader.use()
        shader.setUniform("uAlpha", a)

        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        glBindVertexArray(0)

        // States zurück
        glDisable(GL_BLEND)
        glDepthMask(true)
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
    }

    fun cleanup() {
        glDeleteVertexArrays(vao)
        shader.cleanup()
    }
}
