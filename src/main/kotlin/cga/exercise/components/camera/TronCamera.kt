package cga.exercise.components.camera

import cga.exercise.components.geometry.Transformable
import cga.exercise.components.shader.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.max
import kotlin.math.min

class TronCamera(
    // veränderbare Projektions-Parameter
    var fovRad: Float = Math.toRadians(90.0).toFloat(), // Blickwinkel in Radiant (änderbar -> Zoom)
    var aspect: Float = 16.0f / 9.0f,                   // Seitenverhältnis
    var near: Float = 0.1f,                             // Near-Plane
    var far: Float = 100.0f                             // Far-Plane
) : Transformable(), ICamera {

    // optionale FOV-Grenzen für Zoom (kannst du bei Bedarf anpassen)
    var fovMinRad: Float = Math.toRadians(20.0).toFloat()
    var fovMaxRad: Float = Math.toRadians(100.0).toFloat()

    /**
     * Berechnet die View-Matrix der Kamera.
     * Standard-Blickrichtung: -Z der Kamera.
     */
    override fun getCalculateViewMatrix(): Matrix4f {
        val eye = getWorldPosition()
        val center = Vector3f(eye).add(getWorldZAxis().negate()) // Blick entlang -Z
        val up = getWorldYAxis()
        return Matrix4f().lookAt(eye, center, up)
    }

    /**
     * Berechnet die Projektionsmatrix (perspektivisch) aus den aktuellen Parametern.
     */
    override fun getCalculateProjectionMatrix(): Matrix4f {
        return Matrix4f().perspective(fovRad, aspect, near, far)
    }

    /**
     * Schreibt View- und Proj-Matrix in den Shader.
     */
    override fun bind(shader: ShaderProgram) {
        val view = getCalculateViewMatrix()
        val proj = getCalculateProjectionMatrix()
        shader.setUniform("view_matrix", view, false)
        shader.setUniform("proj_matrix", proj, false)
    }

    // -------------------------
    // Komfort-Methoden / Helper
    // -------------------------

    /** Setzt das FOV in Grad. */
    fun setFovDeg(deg: Float) {
        fovRad = Math.toRadians(deg.toDouble()).toFloat().clamp(fovMinRad, fovMaxRad)
    }

    /** Additives Zoom in Grad (negativ = reinzoomen, positiv = rauszoomen). */
    fun addFovDeg(deltaDeg: Float) {
        val newFov = fovRad + Math.toRadians(deltaDeg.toDouble()).toFloat()
        fovRad = newFov.clamp(fovMinRad, fovMaxRad)
    }

    /** Additives Zoom in Radiant. */
    fun addFovRad(deltaRad: Float) {
        fovRad = (fovRad + deltaRad).clamp(fovMinRad, fovMaxRad)
    }

    /** Setzt das Aspect-Ratio, z. B. bei Fenster-Resize. */
    fun setAspect(width: Int, height: Int) {
        val h = if (height <= 0) 1 else height
        aspect = width.toFloat() / h.toFloat()
    }

    /** Setzt die Near/Far-Planes (optional). */
    fun setDepthRange(nearPlane: Float, farPlane: Float) {
        this.near = nearPlane
        this.far = farPlane
    }

    // kleine Hilfsfunktion fürs Clamping
    private fun Float.clamp(minVal: Float, maxVal: Float): Float = max(minVal, min(this, maxVal))
}
