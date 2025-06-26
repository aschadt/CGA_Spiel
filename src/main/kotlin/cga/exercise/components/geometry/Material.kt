package cga.exercise.components.geometry

import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.texture.Texture2D
import org.joml.Vector2f

class Material(var diff: Texture2D,
               var emit: Texture2D,
               var specular: Texture2D,
               var shininess: Float = 50.0f,
               var tcMultiplier : Vector2f = Vector2f(1.0f)){

    fun bind(shaderProgram: ShaderProgram) {
        // Diffuse-Textur → Texture Unit 0
        diff.bind(0)
        shaderProgram.setUniform("material_diffuse", 0)

        // Emissive-Textur → Texture Unit 1
        emit.bind(1)
        shaderProgram.setUniform("material_emissive", 1)

        // Specular-Textur → Texture Unit 2
        specular.bind(2)
        shaderProgram.setUniform("material_specular", 2)

        // Material-Parameter
        shaderProgram.setUniform("material_shininess", shininess)
        shaderProgram.setUniform("material_tcMultiplier", tcMultiplier)
    }

}