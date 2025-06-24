package cga.exercise.components.light

import cga.exercise.components.geometry.Transformable
import cga.exercise.components.shader.ShaderProgram
import org.joml.Vector3f


class PointLight(
    var color: Vector3f,        // Lichtfarbe & -intensität (RGB)
    initialPosition: Vector3f   // Anfangsposition im Weltkoordinatensystem
) : Transformable(), IPointLight {

    init {
        this.translate(initialPosition)
    }

    override fun bind(shaderProgram: ShaderProgram) {
        shaderProgram.setUniform("light_position", getWorldPosition())  // aus Transformable
        shaderProgram.setUniform("light_color", color)
    }
}

