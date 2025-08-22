package cga.exercise.components.camera

import cga.exercise.components.geometry.Transformable
import cga.exercise.components.shader.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

class TronCamera(
    // Perspektivische Projektions-Parameter
    var fovRad: Float = Math.toRadians(90.0).toFloat(),
    var aspect: Float = 16.0f / 9.0f,
    var near: Float = 0.1f,
    var far: Float = 100.0f
) : Transformable(), ICamera {

    /** Aktueller Projektionsmodus der Kamera. */
    enum class ProjectionMode { Perspective, Orthographic }
    var projectionMode: ProjectionMode = ProjectionMode.Perspective

    /** Ortho-Größe (sichtbare Höhe in Welt­einheiten) + Grenzen für Ortho-"Zoom". */
    var orthoHeight: Float = 10f
    var orthoMin: Float = 0.5f
    var orthoMax: Float = 500f

    /** Optionale FOV-Grenzen für perspektivischen Zoom. */
    var fovMinRad: Float = Math.toRadians(20.0).toFloat()
    var fovMaxRad: Float = Math.toRadians(100.0).toFloat()

    // -------------------------
    // View / Projection
    // -------------------------

    /** View-Matrix: Blick entlang -Z der Kamera. */
    override fun getCalculateViewMatrix(): Matrix4f {
        val eye = getWorldPosition()
        val center = Vector3f(eye).add(getWorldZAxis().negate()) // -Z = Blickrichtung
        val up = getWorldYAxis()
        return Matrix4f().lookAt(eye, center, up)
    }

    /** Projektionsmatrix abhängig vom Modus. */
    override fun getCalculateProjectionMatrix(): Matrix4f {
        return if (projectionMode == ProjectionMode.Perspective) {
            Matrix4f().perspective(fovRad, aspect, near, far)
        } else {
            val halfH = orthoHeight * 0.5f
            val halfW = halfH * aspect
            Matrix4f().ortho(-halfW, halfW, -halfH, halfH, near, far)
        }
    }

    /** View- und Proj-Matrix in den Shader schreiben. */
    override fun bind(shader: ShaderProgram) {
        shader.setUniform("view_matrix", getCalculateViewMatrix(), false)
        shader.setUniform("proj_matrix", getCalculateProjectionMatrix(), false)
    }

    // -------------------------
    // Komfort-Methoden / Helper
    // -------------------------

    /** Zwischen Perspektive und Orthografisch umschalten. */
    fun toggleProjection() {
        projectionMode = if (projectionMode == ProjectionMode.Perspective)
            ProjectionMode.Orthographic else ProjectionMode.Perspective
    }

    /** Ortho-"Zoom": sichtbare Höhe ändern (kleiner = näher ran). */
    fun addOrthoHeight(delta: Float) {
        orthoHeight = (orthoHeight + delta).coerceIn(orthoMin, orthoMax)
    }

    /** FOV direkt in Grad setzen (clamped). */
    fun setFovDeg(deg: Float) {
        fovRad = Math.toRadians(deg.toDouble()).toFloat().clamp(fovMinRad, fovMaxRad)
    }

    /** Additiver Zoom in Grad (negativ = reinzoomen). */
    fun addFovDeg(deltaDeg: Float) {
        val newFov = fovRad + Math.toRadians(deltaDeg.toDouble()).toFloat()
        fovRad = newFov.clamp(fovMinRad, fovMaxRad)
    }

    /** Additiver Zoom in Radiant (negativ = reinzoomen). */
    fun addFovRad(deltaRad: Float) {
        fovRad = (fovRad + deltaRad).clamp(fovMinRad, fovMaxRad)
    }

    /** Aspect-Ratio aktualisieren (z. B. bei Resize). */
    fun setAspect(width: Int, height: Int) {
        val h = if (height <= 0) 1 else height
        aspect = width.toFloat() / h.toFloat()
    }

    /** Near/Far-Planes setzen. */
    fun setDepthRange(nearPlane: Float, farPlane: Float) {
        near = nearPlane
        far = farPlane
    }

    // clamp helper
    private fun Float.clamp(minVal: Float, maxVal: Float): Float = max(minVal, min(this, maxVal))
}