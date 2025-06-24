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
        emit.bind(1)                                                   //Textur wird auf Textur Unit 1 gebunden
        shaderProgram.setUniform("material_emissive", 1)             //Daten in den Shader laden
        shaderProgram.setUniform("material_tcMultiplier", tcMultiplier)    //Daten in den Shader laden

    }
}