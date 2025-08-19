package cga.exercise.components.geometry

import cga.exercise.components.shader.ShaderProgram

class Renderable(
    private val meshes: MutableList<Mesh>
) : Transformable(), IRenderable {

    override fun render(shaderProgram: ShaderProgram) {
        // Model-Matrix an den Shader übergeben
        shaderProgram.setUniform("model_matrix", getWorldModelMatrix())

        // Alle Meshes mit Material binden und rendern
        for (mesh in meshes) {
            mesh.material?.bind(shaderProgram)
            mesh.render(shaderProgram)
        }
    }


   //   Rendert nur die Geometrie für den Shadow-Depth-Pass.
   //   Material wird hier NICHT gebunden, da nur Tiefenwerte gebraucht werden.

    fun renderDepth(shaderProgram: ShaderProgram) {
        // Model-Matrix setzen (wichtig für korrekte Transformation in Light Space)
        shaderProgram.setUniform("model_matrix", getWorldModelMatrix())


        // Meshes ohne Material rendern
        for (mesh in meshes) {
            mesh.render() // ohne shaderProgram, bindet nicht das Material
        }
    }
}