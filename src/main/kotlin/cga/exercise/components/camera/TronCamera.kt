package cga.exercise.components.camera

import cga.exercise.components.geometry.Transformable
import cga.exercise.components.shader.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f

class TronCamera(
    private val fov: Float = Math.toRadians(90.0).toFloat(),    // Sichtfeld (Field of View) in Radiant
    private val aspect: Float = 16.0f / 9.0f,                   // Seitenverhältnis (Breite / Höhe)
    private val near: Float = 0.1f,                             // Nahe Clipping-Ebene (Objekte davor werden nicht gezeichnet)
    private val far: Float = 100.0f                             // Ferne Clipping-Ebene (Objekte dahinter werden nicht gezeichnet)
) : Transformable(), ICamera {

    /**
     * Berechnet die View-Matrix der Kamera.
     * Die View-Matrix beschreibt, wie die Welt aus Sicht der Kamera aussieht.
     * Die Kamera schaut standardmäßig entlang ihrer -Z-Achse.
     */
    override fun getCalculateViewMatrix(): Matrix4f {
        val eye = getWorldPosition()                                // Position der Kamera im Weltkoordinatensystem
        val center = Vector3f(eye).add(getWorldZAxis().negate())    // Zielpunkt: Kamera blickt entlang -Z
        val up = getWorldYAxis()                                    // "Oben"-Vektor aus der aktuellen Kamerahaltung
        return Matrix4f().lookAt(eye, center, up)                   // Erzeugt die View-Matrix basierend auf Auge, Blickrichtung und Up-Vektor
    }

    /**
     * Berechnet die Projektionsmatrix der Kamera.
     * Die Projektionsmatrix wandelt 3D-Koordinaten in 2D-Bildschirmkoordinaten um (mit Perspektive).
     */
    override fun getCalculateProjectionMatrix(): Matrix4f {
        return Matrix4f().perspective(fov, aspect, near, far)       // Erzeugt eine perspektivische Projektion
    }

    /**
     * Bindet die View- und Projektionsmatrix an einen Shader.
     * Das ist notwendig, damit der Shader weiß, wie er die Objekte aus Sicht der Kamera transformieren soll.
     */
    override fun bind(shader: ShaderProgram) {
        val view = getCalculateViewMatrix()
        val proj = getCalculateProjectionMatrix()

        // Überträgt die View-Matrix an den Shader (Uniform muss im Shader vorhanden sein)
        shader.setUniform("view_matrix", getCalculateViewMatrix(), false)

        // Überträgt die Projektionsmatrix an den Shader
        shader.setUniform("proj_matrix", getCalculateProjectionMatrix(), false)
    }
}
