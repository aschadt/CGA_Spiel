package cga.exercise.components.overlay

import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.texture.Texture2D
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*
import kotlin.math.max

/**
 * Vereinfachtes HUD: 7-Segment Zeit (MM:SS) + optional gespiegeltens Icon.
 * Zeichnet in Screen-Pixel-Koordinaten; UI-Shader rechnet nach NDC um.
 */
class Hud(
    private val color: Vector3f = Vector3f(1f, 1f, 1f),
    private val topMargin: Float = 20f,
    private val scale: Float = 1.0f,
    defaultIconPath: String? = "assets/picture/bauerfigur.png"
) {
    // --- Shader
    private val shader = ShaderProgram(
        "assets/shaders/UI/ui_simple.vert",
        "assets/shaders/UI/ui_simple.frag"
    )
    private val texShader = ShaderProgram(
        "assets/shaders/UI/ui_tex.vert",
        "assets/shaders/UI/ui_tex.frag"
    )

    // --- Batcher für farbige Rechtecke (7-Segment)
    private val rectBatch = RectBatch()

    // --- Icon Quad (pos + uv)
    private val iconVao: Int
    private val iconVbo: Int

    // --- Aktuelles Icon
    private var iconTex: Texture2D? = defaultIconPath?.let { Texture2D(it, true) }

    init {
        // Icon-VAO/VBO (pos.xy, uv)
        iconVao = glGenVertexArrays()
        iconVbo = glGenBuffers()
        glBindVertexArray(iconVao)
        glBindBuffer(GL_ARRAY_BUFFER, iconVbo)
        glBufferData(GL_ARRAY_BUFFER, (6 * 4 * 4).toLong(), GL_DYNAMIC_DRAW)
        glEnableVertexAttribArray(0) // pos
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0L)
        glEnableVertexAttribArray(1) // uv
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, 2L * 4)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
    }

    fun setIcon(path: String?) {
        iconTex?.cleanup()
        iconTex = path?.let { Texture2D(it, true) }
    }

    fun draw(viewW: Int, viewH: Int, secondsLeft: Float) =
        draw(viewW, viewH, secondsLeft, showIcon = false)

    fun draw(viewW: Int, viewH: Int, secondsLeft: Float, showIcon: Boolean) {
        val secs = max(0, secondsLeft.toInt())
        val mm = secs / 60
        val ss = secs % 60

        // Maße der 7-Segment-Ziffern
        val W = 30f * scale
        val H = 50f * scale
        val T = 6f  * scale   // Segment-Dicke
        val S = 8f  * scale   // Abstand zwischen Ziffern
        val colonW = 10f * scale

        val totalWidth = (2 * W + S + colonW + S + 2 * W)
        val x0 = (viewW - totalWidth) / 2f
        val y0 = topMargin

        withUiState {
            // ---- Ziffern in einem Batch zeichnen
            shader.use()
            shader.setUniform("uViewport", Vector2f(viewW.toFloat(), viewH.toFloat()))
            shader.setUniform("uColor", color)

            rectBatch.begin() // bindet VAO/VBO des Batches

            var x = x0
            drawDigit(mm / 10, x, y0, W, H, T); x += W + S
            drawDigit(mm % 10, x, y0, W, H, T); x += W
            drawColon(x + (S / 2f), y0, colonW, H, T); x += S + colonW
            drawDigit(ss / 10, x, y0, W, H, T); x += W + S
            drawDigit(ss % 10, x, y0, W, H, T)

            rectBatch.flush(shader) // einmal hochladen & Drawcall

            // ---- Icon optional rechts oben
            if (showIcon) {
                drawIconTopRight(viewW, viewH)
            }
        }
    }

    fun cleanup() {
        iconTex?.cleanup()
        glDeleteBuffers(iconVbo)
        glDeleteVertexArrays(iconVao)
        rectBatch.dispose()
        texShader.cleanup()
        shader.cleanup()
    }

    // ---------------- Vereinfachungen / Helper ----------------

    /** Kapselt UI-typische GL-States (Depth/Cull aus, Blending an) inkl. Restore. */
    private inline fun withUiState(block: () -> Unit) {
        val depthWas = glIsEnabled(GL_DEPTH_TEST)
        val cullWas  = glIsEnabled(GL_CULL_FACE)
        val blendWas = glIsEnabled(GL_BLEND)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        try { block() } finally {
            if (depthWas) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
            if (cullWas)  glEnable(GL_CULL_FACE)  else glDisable(GL_CULL_FACE)
            if (blendWas) glEnable(GL_BLEND)      else glDisable(GL_BLEND)
        }
    }

    private fun drawIconTopRight(viewW: Int, viewH: Int) {
        val tex = iconTex ?: return
        val pad = 20f
        val h = 120f
        val w = 120f
        val x = viewW - pad - w
        val y = pad
        drawIcon(x, y, w, h, viewW, viewH, tex, flipU = true, flipV = false)
    }

    /** Zeichnet ein Icon-Quad; Flip über vertauschte UVs. */
    private fun drawIcon(
        x: Float, y: Float, w: Float, h: Float,
        viewW: Int, viewH: Int,
        tex: Texture2D,
        flipU: Boolean = true,
        flipV: Boolean = false,
    ) {
        val x0 = x
        val y0 = y
        val x1 = x + w
        val y1 = y + h

        val u0 = if (flipU) 1f else 0f
        val u1 = if (flipU) 0f else 1f
        val v0 = if (flipV) 1f else 0f
        val v1 = if (flipV) 0f else 1f

        val verts = floatArrayOf( // pos.x, pos.y, u, v
            x0, y0, u0, v1,
            x1, y0, u1, v1,
            x1, y1, u1, v0,
            x0, y0, u0, v1,
            x1, y1, u1, v0,
            x0, y1, u0, v0
        )

        texShader.use()
        texShader.setUniform("uViewport", Vector2f(viewW.toFloat(), viewH.toFloat()))
        texShader.setUniform("uTex", 0)

        tex.bind(0)
        glBindVertexArray(iconVao)
        glBindBuffer(GL_ARRAY_BUFFER, iconVbo)
        glBufferData(GL_ARRAY_BUFFER, verts, GL_DYNAMIC_DRAW)
        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
        tex.unbind()
    }

    // ---------------- 7-Segment Logik (vereinfacht) ----------------

    /** 7-Segment Patterns: A B C D E F G (wie gewohnt) */
    private val SEGMENTS = arrayOf(
        booleanArrayOf(true,  true,  true,  true,  true,  true,  false), // 0
        booleanArrayOf(false, true,  true,  false, false, false, false), // 1
        booleanArrayOf(true,  true,  false, true,  true,  false, true ), // 2
        booleanArrayOf(true,  true,  true,  true,  false, false, true ), // 3
        booleanArrayOf(false, true,  true,  false, false, true,  true ), // 4
        booleanArrayOf(true,  false, true,  true,  false, true,  true ), // 5
        booleanArrayOf(true,  false, true,  true,  true,  true,  true ), // 6
        booleanArrayOf(true,  true,  true,  false, false, false, false), // 7
        booleanArrayOf(true,  true,  true,  true,  true,  true,  true ), // 8
        booleanArrayOf(true,  true,  true,  true,  false, true,  true )  // 9
    )

    private fun drawColon(cx: Float, y: Float, w: Float, h: Float, t: Float) {
        val dot = t
        val x = cx - dot / 2f
        val y1 = y + h * 0.35f
        val y2 = y + h * 0.65f
        rectBatch.rect(x, y1, dot, dot)
        rectBatch.rect(x, y2, dot, dot)
    }

    private fun drawDigit(d: Int, x: Float, y: Float, W: Float, H: Float, T: Float) {
        if (d !in 0..9) return
        val seg = SEGMENTS[d]
        val half = H * 0.5f
        // A, B, C, D, E, F, G
        if (seg[0]) rectBatch.rect(x + T,        y,                 W - 2*T, T)
        if (seg[1]) rectBatch.rect(x + W - T,    y + T,             T,        half - 1.5f*T)
        if (seg[2]) rectBatch.rect(x + W - T,    y + half + 0.5f*T, T,        half - 1.5f*T)
        if (seg[3]) rectBatch.rect(x + T,        y + H - T,         W - 2*T,  T)
        if (seg[4]) rectBatch.rect(x,            y + half + 0.5f*T, T,        half - 1.5f*T)
        if (seg[5]) rectBatch.rect(x,            y + T,             T,        half - 1.5f*T)
        if (seg[6]) rectBatch.rect(x + T,        y + half - 0.5f*T, W - 2*T,  T)
    }

    // ---------------- Kleiner Rechteck-Batcher ----------------

    private class RectBatch {
        private val vao: Int = glGenVertexArrays()
        private val vbo: Int = glGenBuffers()
        private val verts = ArrayList<Float>(6 * 2 * 16) // Startkapazität

        init {
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
            glBufferData(GL_ARRAY_BUFFER, (6 * 2 * 4).toLong(), GL_DYNAMIC_DRAW)
            glEnableVertexAttribArray(0)
            glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0L)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)
        }

        fun begin() {
            verts.clear()
            glBindVertexArray(vao)
            glBindBuffer(GL_ARRAY_BUFFER, vbo)
        }

        fun rect(x: Float, y: Float, w: Float, h: Float) {
            val x0 = x
            val y0 = y
            val x1 = x + w
            val y1 = y + h
            // 2D-Pos, 6 Vertices
            verts.addAll(listOf(
                x0, y0,  x1, y0,  x1, y1,
                x0, y0,  x1, y1,  x0, y1
            ))
        }

        fun flush(shader: ShaderProgram) {
            if (verts.isEmpty()) {
                glBindBuffer(GL_ARRAY_BUFFER, 0)
                glBindVertexArray(0)
                return
            }
            val arr = verts.toFloatArray()
            glBufferData(GL_ARRAY_BUFFER, arr, GL_DYNAMIC_DRAW)
            glDrawArrays(GL_TRIANGLES, 0, arr.size / 2)
            glBindBuffer(GL_ARRAY_BUFFER, 0)
            glBindVertexArray(0)
        }

        fun dispose() {
            glDeleteBuffers(vbo)
            glDeleteVertexArrays(vao)
        }
    }
}
