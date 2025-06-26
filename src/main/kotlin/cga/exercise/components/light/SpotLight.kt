package cga.exercise.components.light

import cga.exercise.components.shader.ShaderProgram
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.cos

class SpotLight(
    position: Vector3f,
    override var color: Vector3f, // Farbe aus der Elternklasse überschreiben
    private val innerAngle: Float, // Cosinus-Wert
    private val outerAngle: Float  // Cosinus-Wert
) : PointLight(position, color), ISpotLight {

    override fun bind(shaderProgram: ShaderProgram, viewMatrix: Matrix4f) {
        // 1. Rufe Bind von PointLight auf, um Position + Farbe zu binden
        super.bind(shaderProgram)

        // 2. Berechne Spot-Richtung im Viewspace
        val dir = getWorldZAxis().negate().normalize() // -Z-Richtung
        val viewDir = viewMatrix.transformDirection(dir, Vector3f())

        // 3. An Shader senden
        shaderProgram.setUniform("spot_direction_view", viewDir)
        shaderProgram.setUniform("spot_innerCutoff", cos(innerAngle))
        shaderProgram.setUniform("spot_outerCutoff", cos(outerAngle))
    }
}
