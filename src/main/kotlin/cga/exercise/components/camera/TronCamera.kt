package cga.exercise.components.camera

import cga.exercise.components.geometry.Transformable
import cga.exercise.components.shader.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

class TronCamera(
    // Perspektiv-Parameter (änderbar → Zoom)
    var fovRad: Float = Math.toRadians(90.0).toFloat(),
    var aspect: Float = 16.0f / 9.0f,
    var near: Float = 0.1f,
    var far: Float = 100.0f
) : Transformable(), ICamera {

    // -------- Projektionsmodus --------
    enum class ProjectionMode { Perspective, Orthographic }
    var projectionMode: ProjectionMode = ProjectionMode.Perspective
        private set

    // FOV-Grenzen (für Zoom in Perspektive)
    var fovMinRad: Float = Math.toRadians(20.0).toFloat()
    var fovMaxRad: Float = Math.toRadians(100.0).toFloat()

    // Ortho-Parameter (Hälfte der Höhe; Breite = Höhe * Aspect)
    private var orthoHalfHeight: Float = 5.0f

    // ---------------- ICamera ----------------

    /** View-Matrix (Blick entlang -Z der Kamera) */
    override fun getCalculateViewMatrix(): Matrix4f {
        val eye = getWorldPosition()
        val center = Vector3f(eye).add(getWorldZAxis().mul(-1f)) // Blick entlang -Z
        val up = getWorldYAxis()
        return Matrix4f().lookAt(eye, center, up)
    }

    /** Projektionsmatrix je nach Modus */
    override fun getCalculateProjectionMatrix(): Matrix4f {
        return when (projectionMode) {
            ProjectionMode.Perspective ->
                Matrix4f().perspective(fovRad, aspect, near, far)
            ProjectionMode.Orthographic -> {
                val hh = orthoHalfHeight
                val hw = hh * aspect
                Matrix4f().ortho(-hw, hw, -hh, hh, near, far)
            }
        }
    }

    /** View & Proj in den Shader schreiben */
    override fun bind(shader: ShaderProgram) {
        val view = getCalculateViewMatrix()
        val proj = getCalculateProjectionMatrix()
        shader.setUniform("view_matrix", view, false)
        shader.setUniform("proj_matrix", proj, false)
    }

    // --------------- Komfort/Helper ---------------

    /** Zwischen Perspektive und Ortho umschalten. */
    fun toggleProjection() {
        projectionMode = when (projectionMode) {
            ProjectionMode.Perspective  -> ProjectionMode.Orthographic
            ProjectionMode.Orthographic -> ProjectionMode.Perspective
        }
    }

    /** Projektionsmodus explizit setzen. */
    fun setProjection(mode: ProjectionMode) { projectionMode = mode }

    /** True, wenn gerade Ortho aktiv ist. */
    fun isOrthographic() = projectionMode == ProjectionMode.Orthographic

    /** True, wenn gerade Perspektive aktiv ist. */
    fun isPerspective() = projectionMode == ProjectionMode.Perspective

    /** Ortho: absolute halbe Höhe setzen (min. 0.1) */
    fun setOrthoHalfHeight(hh: Float) {
        orthoHalfHeight = max(0.1f, hh)
    }

    /** Ortho: Höhe additiv ändern (positiv = vergrößern, negativ = verkleinern) */
    fun addOrthoHeight(delta: Float) {
        setOrthoHalfHeight(orthoHalfHeight + delta)
    }

    /** Ortho: aktuelle halbe Höhe abfragen (z. B. für UI/Debug). */
    fun getOrthoHalfHeight(): Float = orthoHalfHeight

    /** FOV in Grad setzen (mit Grenzen) */
    fun setFovDeg(deg: Float) {
        fovRad = Math.toRadians(deg.toDouble()).toFloat().clamp(fovMinRad, fovMaxRad)
    }

    /** Additiver Zoom in Grad (negativ = reinzoomen) */
    fun addFovDeg(deltaDeg: Float) {
        val newFov = fovRad + Math.toRadians(deltaDeg.toDouble()).toFloat()
        fovRad = newFov.clamp(fovMinRad, fovMaxRad)
    }

    /** Additiver Zoom in Radiant */
    fun addFovRad(deltaRad: Float) {
        fovRad = (fovRad + deltaRad).clamp(fovMinRad, fovMaxRad)
    }

    /**
     * Einfache Zoom-Funktion, die je nach Modus das Richtige tut:
     *  - Perspektive: delta interpretiert als ΔFOV (Radiant, negativ = rein)
     *  - Ortho: delta interpretiert als ΔHöhe  (negativ = rein)
     */
    fun zoom(delta: Float) {
        if (isPerspective()) addFovRad(delta) else addOrthoHeight(delta)
    }

    /** Aspect anpassen (z. B. bei Resize) */
    fun setAspect(width: Int, height: Int) {
        val h = if (height <= 0) 1 else height
        aspect = width.toFloat() / h.toFloat()
    }

    /** Near/Far anpassen */
    fun setDepthRange(nearPlane: Float, farPlane: Float) {
        near = nearPlane
        far = farPlane
    }

    /** FOV-Limits setzen (optional). */
    fun setFovLimits(minRad: Float? = null, maxRad: Float? = null) {
        minRad?.let { fovMinRad = it }
        maxRad?.let { fovMaxRad = it }
        fovRad = fovRad.clamp(fovMinRad, fovMaxRad)
    }

    // kleine Clamp-Hilfsfunktion
    private fun Float.clamp(minVal: Float, maxVal: Float): Float = max(minVal, min(this, maxVal))
}
