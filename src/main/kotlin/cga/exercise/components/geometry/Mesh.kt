package cga.exercise.components.geometry

import cga.exercise.components.shader.ShaderProgram
import org.lwjgl.opengl.GL20
import org.lwjgl.opengl.GL30.*

/**
 * Creates a Mesh object from vertexdata, intexdata and a given set of vertex attributes
 *
 * @param vertexdata plain float array of vertex data
 * @param indexdata  index data
 * @param attributes vertex attributes contained in vertex data
 * @throws Exception If the creation of the required OpenGL objects fails, an exception is thrown
 *
 * Created by Fabian on 16.09.2017.
 */
class Mesh(val vertexdata: FloatArray,val indexdata: IntArray, attributes: Array<VertexAttribute>, val material: Material? = null) {
    //private data
    private var vaoId = 0
    private var vboId = 0
    private var iboId = 0
    private var indexcount = 0

    init {
        vaoId = glGenVertexArrays()
        glBindVertexArray(vaoId)

        vboId = glGenBuffers()
        glBindBuffer(GL_ARRAY_BUFFER, vboId)
        glBufferData(GL_ARRAY_BUFFER, vertexdata, GL_STATIC_DRAW)

        iboId = glGenBuffers()
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, iboId)
        glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexdata, GL_STATIC_DRAW)

        var index = 0
        for (attribute in attributes) {
            glEnableVertexAttribArray(index)
            glVertexAttribPointer(index,attribute.n,attribute.type,false, attribute.stride, attribute.offset.toLong())
            index++
        }

        glBindVertexArray(0)
        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)

        indexcount = indexdata.size

    }

    //Only send the geometry to the gpu
    /**
     * renders the mesh
     */
    // Neue render()-Methode mit ShaderProgram
    fun render(shaderProgram: ShaderProgram) {
        material?.bind(shaderProgram) // Material-Uniforms setzen (Texturen, Shininess etc.)
        render()                      // Normales Rendering aufrufen
    }

    fun render() {
        glBindVertexArray(vaoId)
        glDrawElements(GL_TRIANGLES, indexcount, GL_UNSIGNED_INT, 0)
        glBindVertexArray(0)
    }

    /**
     * Deletes the previously allocated OpenGL objects for this mesh
     */
    fun cleanup() {
        if (vboId != 0) glDeleteBuffers(vboId)
        if (iboId != 0) glDeleteBuffers(iboId)
        if (vaoId != 0) glDeleteVertexArrays(vaoId)
    }
}