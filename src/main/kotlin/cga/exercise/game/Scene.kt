package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.*
import cga.exercise.components.light.PointLight
import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.texture.Texture2D
import cga.framework.GLError
import cga.framework.GameWindow
import cga.framework.ModelLoader
import cga.framework.OBJLoader.loadOBJ
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL11.*
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*

/**
 * Created by Fabian on 16.09.2017.
 */
class Scene(private val window: GameWindow) {
    //private val staticShader: ShaderProgram = ShaderProgram("assets/shaders/simple_vert.glsl", "assets/shaders/simple_frag.glsl")
    private val staticShader = ShaderProgram("assets/shaders/tron_vert.glsl", "assets/shaders/tron_frag.glsl")

    private val simpleMesh: Mesh

    private var groundRenderable: Renderable
    //private var sphereRenderable: Renderable

    // Die Kamera als TronCamera
    private val camera = TronCamera()
    private var motorrad: Renderable? = null

    private val pointLight = PointLight(Vector3f(0f, 1f, 0f), Vector3f(1f, 1f, 1f))




    //scene setup
    init {
        val vertices = floatArrayOf(
            //A
            -0.5f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.25f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.125f, 0.375f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.125f, 0.375f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.25f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.5f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.125f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.125f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f,

            // Triangle in A
            -0.125f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.125f, 0.5f, 0.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 0.75f, 0.0f, 0.0f, 0.0f, 0.0f,

            //S
            0.5f, -0.25f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.25f, -0.25f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.25f, -0.375f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.5f, -0.375f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.5f, -1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.5f, -1.0f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.5f, -0.75f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.25f, -0.75f, 0.0f, 1.0f, 1.0f, 1.0f,
            0.25f, -0.625f, 0.0f, 1.0f, 1.0f, 1.0f,
            -0.5f, -0.625f, 0.0f, 1.0f, 1.0f, 1.0f,
        )
        val indices = intArrayOf(
            // A
            0,1,2,
            0,2,7,
            0,7,6,
            5,7,6,
            3,4,5,
            2,3,6,
            2,3,7,
            6,3,5,
            7,3,6,
            7,2,6,
            // Triangle in A
            9,10,8,
            // S
            0,11,5,
            0,12,11,
            0,20,12,
            12,20,13,
            13,20,14,
            14,20,19,
            14,19,15,
            15,19,18,
            15,18,16,
            16,18,17

        )

        val attributes = arrayOf(
            VertexAttribute(3, GL11.GL_FLOAT, 24, 0),
            VertexAttribute(3, GL11.GL_FLOAT, 24, 12)
        )

        simpleMesh = Mesh(vertices, indices, attributes)


        val groundObj = loadOBJ("assets/models/ground.obj")     // Objekt aus dem Ordner laden
        val groundMeshList = groundObj.objects[0].meshes                // Meshes auf das Objekt setzen

        val groundAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),   // Position
            VertexAttribute(2, GL_FLOAT, 32, 12),  // Texture
            VertexAttribute(3, GL_FLOAT, 32, 20)   // Normal
        )

        // Texturen laden
        val diffuse = Texture2D("assets/textures/ground_diff.png", true)
        val specular = Texture2D("assets/textures/ground_spec.png", true)
        val emissive = Texture2D("assets/textures/ground_emit.png", true)

        // Texturparameter setzen
        val wrap = GL_REPEAT

        // sorgt für weichere Übergänge und schärfere Darstellung beim Verkleinern der Textur
        val filter = GL_LINEAR_MIPMAP_LINEAR

        // sorgt für glattere Darstellung ohne pixelige Kanten
        val mipmap = GL_LINEAR

        diffuse.setTexParams(wrap, wrap, filter, mipmap)
        specular.setTexParams(wrap, wrap, filter, mipmap)
        emissive.setTexParams(wrap, wrap, filter, mipmap)

        // Material erzeugen
        val groundMaterial = Material(
            diff = diffuse,
            emit = emissive,
            specular = specular,
            shininess = 60.0f,
            tcMultiplier = Vector2f(64.0f, 64.0f)
        )

        // Mesh mit Material erzeugen
        val groundMesh = Mesh(
            groundMeshList[0].vertexData,
            groundMeshList[0].indexData,
            groundAttribs,
            groundMaterial
        )

        // Ground Renderable mit Material verwenden

        groundRenderable = Renderable(mutableListOf(groundMesh)).apply {
            //rotate(Math.toRadians(90.0).toFloat(), 0f, 0f)        // Um X-Achse drehen
            //scale(Vector3f(0.03f))                                // Um den Faktor 0.03 verkleinern
        }

        // Motorrad-Modell laden und rotieren, danach skalieren
        motorrad = ModelLoader.loadModel("assets/models/Light Cycle/HQ_Movie cycle.obj", Math.toRadians(-90.0).toFloat(), Math.toRadians(90.0).toFloat(),0f)
        motorrad?.scale(Vector3f(0.8f))

        // Kamera an Motorrad parenten
        camera.parent = motorrad
        camera.rotate(Math.toRadians(-35.0).toFloat(), 0f, 0f) // Kamera um -35° um X-Achse neigen
        camera.translate(Vector3f(0.0f, 0.0f, 4.0f)) // Kamera um 4 Einheiten zurücksetzen

        //PointLight an Motorrad parenten
        pointLight.parent = motorrad
        pointLight.translate(Vector3f(0f, 1.5f, 0f))


        //initial opengl state
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f); GLError.checkThrow()
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)


    }

    fun render(dt: Float, t: Float) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        staticShader.use()
        //println("Using shader ${staticShader.programID}")
        //simpleMesh.render()
        staticShader.use()

        // Kamera-Matrizen an Shader binden
        camera.bind(staticShader)

        pointLight.bind(staticShader)

       // sphereRenderable.render(staticShader)
        groundRenderable.render(staticShader)
        motorrad?.render(staticShader) // oder über einen sceneGraph, je nach Struktur

    }

    fun update(dt: Float, t: Float) {
        val moveSpeed = 8.0f
        val rotateSpeed = Math.toRadians(90.0).toFloat()

        if (window.getKeyState(GLFW_KEY_W)) {
            motorrad?.translate(Vector3f(0f, 0f, -moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) {
                motorrad?.rotate(0f, rotateSpeed * dt, 0f)
            }
            if (window.getKeyState(GLFW_KEY_D)) {
                motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
                //motorrad?.translate(Vector3f(0f, 0f, -moveSpeed * dt))
            }
        }
        if (window.getKeyState(GLFW_KEY_S)) {
            motorrad?.translate(Vector3f(0f, 0f, moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) {
                motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
            }
            if (window.getKeyState(GLFW_KEY_D)) {
                motorrad?.rotate(0f, rotateSpeed * dt, 0f)
                //motorrad?.translate(Vector3f(0f, 0f, -moveSpeed * dt))
            }
        }


        // Kamera-Matrizen aktualisieren
        camera.getCalculateViewMatrix()
        camera.getCalculateProjectionMatrix()
    }



    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {}

    fun onMouseMove(xpos: Double, ypos: Double) {}

    fun cleanup() {}
}