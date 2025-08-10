package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.*
import cga.exercise.components.light.PointLight
import cga.exercise.components.light.SpotLight
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
import kotlin.math.sin

/**
 * Created by Fabian on 16.09.2017.
 */
class Scene(private val window: GameWindow) {
    private val staticShader = ShaderProgram("assets/shaders/tron_vert.glsl", "assets/shaders/tron_frag.glsl")

    private var groundRenderable: Renderable

    private var cubeRenderable: Renderable? = null
    private var coneRenderable: Renderable? = null

    private var roomRenderable: Renderable

    private var cubeMoveMode = true
    private var coneMoveMode = true

    private var chooseObj: Int = 0

    // Die Kamera als TronCamera
    private val camera = TronCamera()
    private var motorrad: Renderable? = null

    private val pointLight = PointLight(Vector3f(0f, 1f, 0f), Vector3f(1f, 1f, 1f))
    private var spotLight: SpotLight? = null

    private var lastMouseX = 0.0
    private var firstMouseMove = true

    private val pointLights = listOf(
        pointLight,
        PointLight(Vector3f(-20f, 1.5f, -20f), Vector3f(1f, 0f, 0f)),   // rot
        PointLight(Vector3f(20f, 1.5f, -20f), Vector3f(0f, 1f, 0f)),    // grün
        PointLight(Vector3f(20f, 1.5f, 20f), Vector3f(0f, 0f, 1f)),     // blau
        PointLight(Vector3f(-20f, 1.5f, 20f), Vector3f(1f, 1f, 0f))     // gelb
    )





    //scene setup
    init {

        //Ground Mesh

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
        groundRenderable = Renderable(mutableListOf(groundMesh))

        //Motorrad Mesh

        // Motorrad-Modell laden und rotieren, danach skalieren
        motorrad = ModelLoader.loadModel("assets/models/Light Cycle/HQ_Movie cycle.obj", Math.toRadians(-90.0).toFloat(), Math.toRadians(90.0).toFloat(),0f)
        motorrad?.scale(Vector3f(0.8f))

        // Kamera an Motorrad parenten
        camera.parent = motorrad
        camera.rotate(Math.toRadians(-25.0).toFloat(), 0f, 0f) // Kamera um -35° um X-Achse neigen
        camera.translate(Vector3f(0.0f, 1.0f, 4.0f)) // Kamera um 4 Einheiten zurücksetzen

        //PointLight an Motorrad parenten
        pointLight.parent = motorrad
        pointLight.translate(Vector3f(0f, 1.5f, 0f))

        // Spotlight erstellen
        spotLight = SpotLight(
            position = Vector3f(0f, 1.5f, 0f),         // leicht erhöht über dem Motorrad
            color = Vector3f(1f, 1f, 1f),              // weißes Licht
            innerAngle = Math.toRadians(20.0).toFloat(),
            outerAngle = Math.toRadians(25.0).toFloat()
        )

        // SpotLight an das Motorrad anhängen (mitbewegen!)
        spotLight?.parent = motorrad

        //Room Mesh

        val roomObj = loadOBJ("assets/models/room.obj")         // Objekt aus dem Ordner laden
        val roomMeshList = roomObj.objects[0].meshes                   // Meshes auf das Objekt setzen

        val roomAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),   // Position
            VertexAttribute(2, GL_FLOAT, 32, 12),  // Texture
            VertexAttribute(3, GL_FLOAT, 32, 20)   // Normal
        )

        // Mesh mit Material erzeugen
        val roomMesh = Mesh(
            roomMeshList[0].vertexData,
            roomMeshList[0].indexData,
            roomAttribs,
            groundMaterial
        )
        // Room Renderable mit Material verwenden
        roomRenderable = Renderable(mutableListOf(roomMesh))

        roomRenderable.scale(Vector3f(23.0f, 5.0f, 23.0f))                  // Objekt skalieren, rotieren und verschieben
        roomRenderable.rotate(0f, Math.toRadians(-90.0).toFloat(), 0f)
        roomRenderable.translate(Vector3f(0.0f, 0.0f, 0.0f))


        //Cube Mesh

        val cubeObj = loadOBJ("assets/models/cube.obj")             // Objekt aus dem Ordner laden
        val cubeMeshList = cubeObj.objects[0].meshes                       // Meshes auf das Objekt setzen

        val cubeAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),   // Position
            VertexAttribute(2, GL_FLOAT, 32, 12),  // Texture
            VertexAttribute(3, GL_FLOAT, 32, 20)   // Normal
        )
        // Mesh mit Material erzeugen
        val cubeMesh = Mesh(
            cubeMeshList[0].vertexData,
            cubeMeshList[0].indexData,
            cubeAttribs,
            groundMaterial
        )
        // Cube Renderable mit Material verwenden
        cubeRenderable = Renderable(mutableListOf(cubeMesh))
        // Cube verschieben
        cubeRenderable?.translate(Vector3f(0.0f, 2.0f, -4.0f))

        //Cone Mesh

        val coneObj = loadOBJ("assets/models/cone.obj")             // Objekt aus dem Ordner laden
        val coneMeshList = coneObj.objects[0].meshes                        // Meshes auf das Objekt setzen

        val coneAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),   // Position
            VertexAttribute(2, GL_FLOAT, 32, 12),  // Texture
            VertexAttribute(3, GL_FLOAT, 32, 20)   // Normal
        )
        // Mesh mit Material erzeugen
        val coneMesh = Mesh(
            coneMeshList[0].vertexData,
            coneMeshList[0].indexData,
            coneAttribs,
            groundMaterial
        )
        // Cone Renderable mit Material verwenden
        coneRenderable = Renderable(mutableListOf(coneMesh))
        // Cone verschieben und skalieren
        coneRenderable?.translate(Vector3f(0.0f, 2.0f, -2.0f))
        coneRenderable?.scale(Vector3f(0.5f, 0.5f, 0.5f))




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

        // Kamera-Matrizen an Shader binden
        camera.bind(staticShader)

        // Anzahl der Punktlichter setzen
        staticShader.setUniform("numPointLights", pointLights.size)

        // Für jedes Punktlicht: Position und Farbe setzen
        for ((index, light) in pointLights.withIndex()) {
            // Position in Viewspace berechnen
            val viewPos = camera.getCalculateViewMatrix().transformPosition(light.getWorldPosition())

            // Uniform-Namen dynamisch zusammenbauen
            staticShader.setUniform("pointLight_positions[$index]", viewPos)
            staticShader.setUniform("pointLight_colors[$index]", light.color)
        }

        // View-Matrix abrufen
        val viewMatrix = camera.getCalculateViewMatrix()

        staticShader.setUniform("pointLight_color", pointLight.color)
        spotLight?.let { staticShader.setUniform("spotLight_color", it.color) }


        spotLight?.bind(staticShader, camera.getCalculateViewMatrix())

        staticShader.setUniform("emission_tint", Vector3f(0f, 1f, 0f)) // Boden auf grün
        //groundRenderable.render(staticShader)

        cubeRenderable?.render(staticShader)
        coneRenderable?.render(staticShader)
        roomRenderable.render(staticShader)

        // Farbwechsel abhängig von der Zeit t
        val r = (sin(t * 2.0) * 0.5 + 0.5).toFloat()
        val g = (sin(t * 0.7 + 2.0) * 0.5 + 0.5).toFloat()
        val b = (sin(t * 1.3 + 4.0) * 0.5 + 0.5).toFloat()
        val animatedTint = Vector3f(r, g, b)

        // Motorrad-Emission setzen
        staticShader.setUniform("emission_tint", animatedTint)

        // Lichtfarbe anpassen (optional)
        pointLight.color = animatedTint

        motorrad?.render(staticShader) // oder über einen sceneGraph, je nach Struktur

    }

    fun update(dt: Float, t: Float) {
        val moveSpeed = 8.0f
        val rotateSpeed = Math.toRadians(90.0).toFloat()

        //Motorrad controlls
        if (window.getKeyState(GLFW_KEY_W)) {
            motorrad?.translate(Vector3f(0f, 0f, -moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) {
                motorrad?.rotate(0f, rotateSpeed * dt, 0f)
            }
            if (window.getKeyState(GLFW_KEY_D)) {
                motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
            }
        }
        if (window.getKeyState(GLFW_KEY_S)) {
            motorrad?.translate(Vector3f(0f, 0f, moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) {
                motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
            }
            if (window.getKeyState(GLFW_KEY_D)) {
                motorrad?.rotate(0f, rotateSpeed * dt, 0f)
            }
        }

        // Controls
        if(chooseObj == 1) {
            objectControl(dt,cubeMoveMode,cubeRenderable)               // wendet die move und rotation logik auf Objekt 1 an
        } else if(chooseObj == 2) {
            objectControl(dt,coneMoveMode,coneRenderable)               // wendet die move und rotation logik auf Objekt 2 an
        }


        // Kamera-Matrizen aktualisieren
        camera.getCalculateViewMatrix()
        camera.getCalculateProjectionMatrix()
    }

    fun objectControl (dt: Float, moveMode: Boolean, renderable: Renderable?) {     // Implementiert die move und rotation logik der Objekte
        val speed = Math.toRadians(90.0).toFloat()

        if(moveMode) {
            if (window.getKeyState(GLFW_KEY_UP)) {
                renderable?.translate(Vector3f(0f, 0f, -speed * dt))
            }
            if (window.getKeyState(GLFW_KEY_DOWN)) {
                renderable?.translate(Vector3f(0f, 0f, speed * dt))
            }
            if (window.getKeyState(GLFW_KEY_LEFT)) {
                renderable?.translate(Vector3f(-speed * dt, 0f, 0f))
            }
            if (window.getKeyState(GLFW_KEY_RIGHT)) {
                renderable?.translate(Vector3f(speed * dt, 0f, 0f))
            }
        } else {
            if (window.getKeyState(GLFW_KEY_UP)) {
                renderable?.rotate(-speed * dt, 0f, 0f)
            }
            if (window.getKeyState(GLFW_KEY_DOWN)) {
                renderable?.rotate(speed * dt, 0f, 0f)
            }
            if (window.getKeyState(GLFW_KEY_LEFT)) {
                renderable?.rotate(0f, -speed * dt, 0f)
            }
            if (window.getKeyState(GLFW_KEY_RIGHT)) {
                renderable?.rotate(0f, speed * dt, 0f)
            }
        }
    }



    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {                            // Tastatur Interaktion
        if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {                                  // beim drücken von TAB
            when (chooseObj) {                                                              // wird geschaut welchen Wert chooseObj hat
                1 -> {cubeMoveMode = !cubeMoveMode                                          // bei 1 wird der Mode von Objekt 1 verändert
                    println("Move-Mode: ${if(cubeMoveMode) "Bewegen" else "Rotieren" }")
                }
                2 -> {coneMoveMode = !coneMoveMode                                          // bei 2 wird der Mode von Objekt 2 verändert
                    println("Move-Mode: ${if(coneMoveMode) "Bewegen" else "Rotieren" }")
                }
            }


        }

        if( key == GLFW_KEY_1 && action == GLFW_PRESS) {                                    // beim drücken von 1 bekommt chooseObj den Wert 1
            chooseObj = 1
            println("Objekt $chooseObj ausgewählt")
        } else if (key == GLFW_KEY_2 && action == GLFW_PRESS) {                             // beim drücken von 1 bekommt chooseObj den Wert 2
            chooseObj = 2
            println("Objekt $chooseObj ausgewählt")
        }
    }

    fun onMouseMove(xpos: Double, ypos: Double) {
        // Wird beim ersten MouseMove ausgelöst, um den Startwert zu speichern
        if (firstMouseMove) {
            lastMouseX = xpos           // Startposition merken
            firstMouseMove = false
            return
        }

        val dx = xpos - lastMouseX
        lastMouseX = xpos               // Neue Position merken

        val sensitivity = 0.002f
        val yaw = (-dx * sensitivity).toFloat()  // Umrechnung in Winkel (negiert, damit Richtung stimmt)

        camera.rotateAroundPoint(0f, yaw, 0f, Vector3f(0f))
    }


    fun cleanup() {}
}