package cga.exercise.components.geometry

import cga.exercise.components.shader.ShaderProgram
import org.joml.Vector3f

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

    fun getKollision(): Kollision {
        var min = Vector3f(Float.POSITIVE_INFINITY)
        var max = Vector3f(Float.NEGATIVE_INFINITY)

        for (mesh in meshes) {
            val stride = 8
            val vertices = mesh.vertexdata
            for (i in vertices.indices step stride) {
                val pos = Vector3f(vertices[i], vertices[i+1], vertices[i+2])
                min.min(pos)
                max.max(pos)
            }
        }

        val modelMatrix = this.getWorldModelMatrix()
        val corners = arrayOf(
            Vector3f(min.x, min.y, min.z),
            Vector3f(min.x, min.y, max.z),
            Vector3f(min.x, max.y, min.z),
            Vector3f(min.x, max.y, max.z),
            Vector3f(max.x, min.y, min.z),
            Vector3f(max.x, min.y, max.z),
            Vector3f(max.x, max.y, min.z),
            Vector3f(max.x, max.y, max.z)
        )

        val minWorld = Vector3f(Float.MAX_VALUE)
        val maxWorld = Vector3f(-Float.MAX_VALUE)

        for (corner in corners) {
            val transformed = modelMatrix.transformPosition(Vector3f(corner))
            minWorld.min(transformed)
            maxWorld.max(transformed)
        }

        return Kollision(minWorld, maxWorld)
    }

    fun collidesWith(other: Renderable): Boolean {
        return this.getKollision().intersects(other.getKollision())
    }
}