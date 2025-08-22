package cga.exercise.components.overlay

import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.texture.Texture2D
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL15.*
import org.lwjgl.opengl.GL20.*
import org.lwjgl.opengl.GL30.*

/**
 * HUD: 7-Segment Zeit (MM:SS) oben mittig + optionales (gespiegeltes) Icon oben rechts.
 * Zeichnet in Screen-Pixel-Koordinaten (per UI-Shader nach NDC umgerechnet).
 */
class Hud(
    private val color: Vector3f = Vector3f(1f, 1f, 1f),
    private val topMargin: Float = 20f,
    private val scale: Float = 1.0f,
    defaultIconPath: String? = "assets/picture/bauerfigur.png"
) {
    private val shader = ShaderProgram(
        "assets/shaders/UI/ui_simple.vert",
        "assets/shaders/UI/ui_simple.frag"
    )

    private val texShader = ShaderProgram(
        "assets/shaders/UI/ui_tex.vert",
        "assets/shaders/UI/ui_tex.frag"
    )

    // VAO/VBO für 7-Segment
    private val vao: Int
    private val vbo: Int

    // VAO/VBO für Icon (pos + uv)
    private val iconVao: Int
    private val iconVbo: Int

    // Aktuelles Icon
    private var iconTex: Texture2D? = defaultIconPath?.let { Texture2D(it, true) }

    init {
        // 7-Segment-VAO/VBO
        vao = glGenVertexArrays()
        vbo = glGenBuffers()
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, (6 * 2 * 4).toLong(), GL_DYNAMIC_DRAW)
        glEnableVertexAttribArray(0)
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 2 * 4, 0L)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)

        // Icon-VAO/VBO
        iconVao = glGenVertexArrays()
        iconVbo = glGenBuffers()
        glBindVertexArray(iconVao)
        glBindBuffer(GL_ARRAY_BUFFER, iconVbo)
        glBufferData(GL_ARRAY_BUFFER, (6 * 4 * 4).toLong(), GL_DYNAMIC_DRAW)
        glEnableVertexAttribArray(0) // pos
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 4 * 4, 0L)
        glEnableVertexAttribArray(1) // uv
        glVertexAttribPointer(1, 2, GL_FLOAT, false, 4 * 4, (2L * 4))
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
        val secs = if (secondsLeft > 0f) secondsLeft.toInt() else 0
        val mm = secs / 60
        val ss = secs % 60

        val W = 30f * scale
        val H = 50f * scale
        val T = 6f  * scale
        val S = 8f  * scale
        val colonW = 10f * scale

        val totalWidth = (2 * W + S + colonW + S + 2 * W)
        val x0 = (viewW - totalWidth) / 2f
        val y0 = topMargin

        val depthWasEnabled = glIsEnabled(GL_DEPTH_TEST)
        val cullWasEnabled  = glIsEnabled(GL_CULL_FACE)
        val blendWasEnabled = glIsEnabled(GL_BLEND)
        glDisable(GL_DEPTH_TEST)
        glDisable(GL_CULL_FACE)
        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        shader.use()
        shader.setUniform("uViewport", Vector2f(viewW.toFloat(), viewH.toFloat()))
        shader.setUniform("uColor", color)

        var x = x0
        drawDigit(mm / 10, x, y0, W, H, T, viewW, viewH); x += W + S
        drawDigit(mm % 10, x, y0, W, H, T, viewW, viewH); x += W
        drawColon(x + (S / 2f), y0, colonW, H, T, viewW, viewH); x += S + colonW
        drawDigit(ss / 10, x, y0, W, H, T, viewW, viewH); x += W + S
        drawDigit(ss % 10, x, y0, W, H, T, viewW, viewH)

        if (showIcon) {
            drawIconTopRight(viewW, viewH)
        }

        if (depthWasEnabled) glEnable(GL_DEPTH_TEST) else glDisable(GL_DEPTH_TEST)
        if (cullWasEnabled)  glEnable(GL_CULL_FACE)  else glDisable(GL_CULL_FACE)
        if (blendWasEnabled) glEnable(GL_BLEND)      else glDisable(GL_BLEND)
    }

    fun cleanup() {
        iconTex?.cleanup()
        glDeleteBuffers(iconVbo)
        glDeleteVertexArrays(iconVao)
        glDeleteBuffers(vbo)
        glDeleteVertexArrays(vao)
        texShader.cleanup()
        shader.cleanup()
    }

    // ---------------- intern ----------------

    private fun drawIconTopRight(viewW: Int, viewH: Int) {
        val tex = iconTex ?: return
        val pad = 20f
        val h = 120f
        val w = 120f
        val x = viewW - pad - w
        val y = pad
        // Spiegelung (horizontal): flipU = true, flipV = false
        drawIcon(x, y, w, h, viewW, viewH, tex, flipU = true, flipV = false)
    }

    /**
     * Zeichnet das Icon. Mit flipU/flipV kann gespiegelt werden (horizontal/vertikal).
     */
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
            x0, y0, 1f, 1f,
            x1, y0, 0f, 1f,
            x1, y1, 0f, 0f,

            x0, y0, 1f, 1f,
            x1, y1, 0f, 0f,
            x0, y1, 1f, 0f )

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

    private fun drawColon(cx: Float, y: Float, w: Float, h: Float, t: Float, vw: Int, vh: Int) {
        val dotW = t
        val dotH = t
        val x = cx - dotW / 2f
        val y1 = y + h * 0.35f
        val y2 = y + h * 0.65f
        drawRect(x, y1, dotW, dotH, vw, vh)
        drawRect(x, y2, dotW, dotH, vw, vh)
    }

    private fun drawDigit(d: Int, x: Float, y: Float, W: Float, H: Float, T: Float, vw: Int, vh: Int) {
        val seg = when (d) {
            0 -> booleanArrayOf(true,  true,  true,  true,  true,  true,  false)
            1 -> booleanArrayOf(false, true,  true,  false, false, false, false)
            2 -> booleanArrayOf(true,  true,  false, true,  true,  false, true )
            3 -> booleanArrayOf(true,  true,  true,  true,  false, false, true )
            4 -> booleanArrayOf(false, true,  true,  false, false, true,  true )
            5 -> booleanArrayOf(true,  false, true,  true,  false, true,  true )
            6 -> booleanArrayOf(true,  false, true,  true,  true,  true,  true )
            7 -> booleanArrayOf(true,  true,  true,  false, false, false, false)
            8 -> booleanArrayOf(true,  true,  true,  true,  true,  true,  true )
            9 -> booleanArrayOf(true,  true,  true,  true,  false, true,  true )
            else -> booleanArrayOf(false, false, false, false, false, false, false)
        }

        val half = H * 0.5f
        if (seg[0]) drawRect(x + T,        y,                 W - 2*T, T,                 vw, vh) // A
        if (seg[1]) drawRect(x + W - T,    y + T,             T,        half - 1.5f*T,    vw, vh) // B
        if (seg[2]) drawRect(x + W - T,    y + half + 0.5f*T, T,        half - 1.5f*T,    vw, vh) // C
        if (seg[3]) drawRect(x + T,        y + H - T,         W - 2*T,  T,                vw, vh) // D
        if (seg[4]) drawRect(x,            y + half + 0.5f*T, T,        half - 1.5f*T,    vw, vh) // E
        if (seg[5]) drawRect(x,            y + T,             T,        half - 1.5f*T,    vw, vh) // F
        if (seg[6]) drawRect(x + T,        y + half - 0.5f*T, W - 2*T,  T,                vw, vh) // G
    }

    private fun drawRect(x: Float, y: Float, w: Float, h: Float, viewW: Int, viewH: Int) {
        val x0 = x
        val y0 = y
        val x1 = x + w
        val y1 = y + h
        val verts = floatArrayOf(
            x0, y0,  x1, y0,  x1, y1,
            x0, y0,  x1, y1,  x0, y1
        )
        glBindVertexArray(vao)
        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        glBufferData(GL_ARRAY_BUFFER, verts, GL_DYNAMIC_DRAW)

        shader.use()
        shader.setUniform("uViewport", Vector2f(viewW.toFloat(), viewH.toFloat()))
        shader.setUniform("uColor", color)

        glDrawArrays(GL_TRIANGLES, 0, 6)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindVertexArray(0)
    }
}
