package cga.exercise.components.geometry

import cga.exercise.components.shader.ShaderProgram

class Renderable(
    private val meshes: MutableList<Mesh>
) : Transformable(), IRenderable {

    override fun render(shaderProgram: ShaderProgram) {
        // Setze die Model-Matrix im Shader
        //println("Rendering ground...") // DEBUG
        shaderProgram.setUniform("model_matrix", getWorldModelMatrix())

        // Rendere alle Meshes
        for (mesh in meshes) {
            mesh.material?.bind(shaderProgram)  // Material an den Shader binden
            mesh.render(shaderProgram)
        }
    }
}
