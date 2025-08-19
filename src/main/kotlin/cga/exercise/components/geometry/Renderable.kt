package cga.exercise.components.geometry

import cga.exercise.components.shader.ShaderProgram
import org.joml.Vector3f

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

