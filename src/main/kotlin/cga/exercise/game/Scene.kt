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


<<<<<<< Updated upstream
=======
    // Orbit-Parameter (aktuelle Inkremente)
    private var yaw1 = 0f               // Cam1: Yaw erlaubt
    private var pitch1 = 0f             // Cam1: Pitch gesperrt (bleibt 0)
    private var dist1 = 6.0f
>>>>>>> Stashed changes


<<<<<<< Updated upstream
=======
    private val yawSpeed = 1.4f
    private val pitchSpeed = 1.0f
    private val pitchMin = -1.2f
    private val pitchMax =  1.2f
    private val distMin  = 2.0f         // Distanz wird für Snap/Start verwendet (nicht zum Zoomen)
    private val distMax  = 20.0f

    // --- NEU: FOV-„Zoom“ statt Dolly ---
    private val fovMinRad = Math.toRadians(20.0).toFloat()
    private val fovMaxRad = Math.toRadians(100.0).toFloat()
    private val fovZoomSpeedRad = Math.toRadians(60.0).toFloat() // 60° pro Sekunde

    private var activeCam = 0 // 0 = cam1, 1 = cam2

    // --- Kamera-Ziele & Defaults/Hard-Snap ---
    private val camTargets = mutableListOf<Transformable>()
    private var camTargetIndex = 0
    private var currentCamTarget: Transformable? = null

    private val cam1DefaultYaw = 0f
    private val cam1DefaultPitch = 0f

    private val cam2DefaultYaw = 0f
    private val cam2DefaultPitch = -0.35f

    // Höhe der Kamera (Y-Offset relativ zum Target): fürs Motorrad anders
    private val cam1YOffsetDefault = 1.0f
    private val cam1YOffsetBike    = 4.0f
    private val cam2YOffsetDefault = 0.0f
    private val cam2YOffsetBike    = 0.8f

    private fun cam1YOffsetFor(target: Transformable?): Float =
        if (target != null && target === motorrad) cam1YOffsetBike else cam1YOffsetDefault
    private fun cam2YOffsetFor(target: Transformable?): Float =
        if (target != null && target === motorrad) cam2YOffsetBike else cam2YOffsetDefault

    // Pro-Target Orbit-States je Kamera
    private data class OrbitState(var yaw: Float = 0f, var pitch: Float = 0f, var dist: Float = 6f)
    private val cam1States = mutableMapOf<Transformable, OrbitState>()
    private val cam2States = mutableMapOf<Transformable, OrbitState>()

    // Maus (optionales Yaw für Cam1)
    private var firstMouseMove = true
    private var lastMouseX = 0.0
>>>>>>> Stashed changes

    //scene setup
    init {


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

<<<<<<< Updated upstream
        // Kamera an Motorrad parenten
        camera.parent = motorrad
        camera.rotate(Math.toRadians(-35.0).toFloat(), 0f, 0f) // Kamera um -35° um X-Achse neigen
        camera.translate(Vector3f(0.0f, 0.0f, 4.0f)) // Kamera um 4 Einheiten zurücksetzen

        //PointLight an Motorrad parenten
=======
        // ---------- Motorrad (optional) ----------
        motorrad = ModelLoader.loadModel(
            "assets/models/Light Cycle/HQ_Movie cycle.obj",
            Math.toRadians(-90.0).toFloat(),
            Math.toRadians(90.0).toFloat(),
            0f
        )?.apply { scale(Vector3f(0.8f)) }

        // ---------- Lichter ----------
>>>>>>> Stashed changes
        pointLight.parent = motorrad
        pointLight.translate(Vector3f(0f, 1.5f, 0f))

        // Spotlight erstellen
        spotLight = SpotLight(
            position = Vector3f(0f, 1.5f, 0f),         // leicht erhöht über dem Motorrad
            color = Vector3f(1f, 1f, 1f),              // weißes Licht
            innerAngle = Math.toRadians(20.0).toFloat(),
            outerAngle = Math.toRadians(25.0).toFloat()
        )

<<<<<<< Updated upstream
        // SpotLight an das Motorrad anhängen (mitbewegen!)
        spotLight?.parent = motorrad

=======
        // ---------- Orbit-Setup ----------
        cam1.parent = rig1
        cam2.parent = rig2
        cam1.translate(Vector3f(0f, cam1YOffsetDefault, dist1))
        cam2.translate(Vector3f(0f, cam2YOffsetDefault, dist2))
        // rig2-Ebene kippen (hier 270° = -90° um X)
        rig2.rotate(Math.toRadians(270.0).toFloat(), 0f, 0f)
        if (abs(pitch1) > 1e-6f) rig1.rotate(pitch1, 0f, 0f)
        if (abs(pitch2) > 1e-6f) rig2.rotate(pitch2, 0f, 0f)

        // Start-FOVs für beide Kameras (für FOV-Zoom wichtig)
        cam1.fovRad = Math.toRadians(90.0).toFloat()
        cam2.fovRad = Math.toRadians(90.0).toFloat()

        // ---------- Kamera-Ziele & Start (Hard-Snap) ----------
        camTargets.clear()
        cubeRenderable?.let { camTargets += it }
        coneRenderable?.let { camTargets += it }
        motorrad?.let     { camTargets += it }
        if (camTargets.isNotEmpty()) setCameraTarget(camTargets[0], snap = true)
>>>>>>> Stashed changes


        //initial opengl state
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f); GLError.checkThrow()
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)


    }

<<<<<<< Updated upstream
=======
    // --- Aktive Kamera/Rig/Parameter ---
    private fun getActiveCamera(): TronCamera = if (activeCam == 0) cam1 else cam2
    private fun getActiveRig(): Transformable = if (activeCam == 0) rig1 else rig2
    private fun getPitchRef(): Float = if (activeCam == 0) pitch1 else pitch2
    private fun setPitchRef(v: Float) { if (activeCam == 0) pitch1 = v else pitch2 = v }
    private fun getDistRef(): Float = if (activeCam == 0) dist1 else dist2
    private fun setDistRef(v: Float) { if (activeCam == 0) dist1 = v else dist2 = v }

    // --- Ziel wählen (speichert Pose pro altem Target, lädt Pose des neuen Targets; Y-Offset je Ziel) ---
    private fun setCameraTarget(target: Transformable, snap: Boolean = true) {
        val eps = 1e-6f

        // 1) Bisheriges Target speichern
        currentCamTarget?.let { prev ->
            cam1States[prev] = OrbitState(yaw = yaw1, pitch = pitch1, dist = dist1)
            cam2States[prev] = OrbitState(yaw = yaw2, pitch = pitch2, dist = dist2)
        }

        // 2) Neues Target aktivieren
        currentCamTarget = target
        rig1.parent = target
        rig2.parent = target
        spotLight?.parent = target
        pointLight.parent = target

        if (!snap) return

        // 3) States laden (oder Defaults)
        val s1 = cam1States[target] ?: OrbitState(
            yaw = cam1DefaultYaw, pitch = cam1DefaultPitch, dist = dist1
        )
        val s2 = cam2States[target] ?: OrbitState(
            yaw = cam2DefaultYaw, pitch = cam2DefaultPitch, dist = dist2
        )

        // 4) Rigs zurückdrehen und Zielpose anwenden
        // Cam1
        if (abs(yaw1)   > eps) rig1.rotate(0f, -yaw1, 0f)
        if (abs(pitch1) > eps) rig1.rotate(-pitch1, 0f, 0f)
        yaw1 = s1.yaw; pitch1 = s1.pitch
        if (abs(pitch1) > eps) rig1.rotate(pitch1, 0f, 0f)
        if (abs(yaw1)   > eps) rig1.rotate(0f, yaw1, 0f)

        // Cam2
        if (abs(yaw2)   > eps) rig2.rotate(0f, -yaw2, 0f)
        if (abs(pitch2) > eps) rig2.rotate(-pitch2, 0f, 0f)
        yaw2 = s2.yaw; pitch2 = s2.pitch
        if (abs(pitch2) > eps) rig2.rotate(pitch2, 0f, 0f)
        if (abs(yaw2)   > eps) rig2.rotate(0f, yaw2, 0f)

        // 5) Distanz & zielabhängigen Y-Offset setzen (Distanz nur für Start/Snap)
        // Cam1
        val p1 = cam1.getPosition()
        dist1 = s1.dist
        val y1 = cam1YOffsetFor(target)
        val dY1 = y1    - p1.y
        val dZ1 = dist1 - p1.z
        if (abs(dY1) > eps || abs(dZ1) > eps) cam1.translate(Vector3f(0f, dY1, dZ1))

        // Cam2
        val p2 = cam2.getPosition()
        dist2 = s2.dist
        val y2 = cam2YOffsetFor(target)
        val dY2 = y2    - p2.y
        val dZ2 = dist2 - p2.z
        if (abs(dY2) > eps || abs(dZ2) > eps) cam2.translate(Vector3f(0f, dY2, dZ2))
    }

    private fun cycleCameraTarget(forward: Boolean = true) {
        if (camTargets.isEmpty()) return
        camTargetIndex = if (forward)
            (camTargetIndex + 1) % camTargets.size
        else
            (camTargetIndex - 1 + camTargets.size) % camTargets.size
        setCameraTarget(camTargets[camTargetIndex], snap = true)
        println("Kamera-Ziel: $camTargetIndex / ${camTargets.size}")
    }

    // --- Render ---
>>>>>>> Stashed changes
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

<<<<<<< Updated upstream
        // View-Matrix abrufen
        val viewMatrix = camera.getCalculateViewMatrix()

        staticShader.setUniform("pointLight_color", pointLight.color)
        spotLight?.let { staticShader.setUniform("spotLight_color", it.color) }

=======
        // Einzelnes PointLight (vertex shader 'light_position')
        pointLight.bind(staticShader)

        // Spotlight (auf aktuelles Target ausrichten)
        spotLight?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)
            val target = currentCamTarget ?: cubeRenderable
            target?.let {
                val dirWorld = Vector3f(it.getWorldPosition()).sub(sp.getWorldPosition()).normalize()
                val dirView  = view.transformDirection(dirWorld, Vector3f()).normalize()
                staticShader.setUniform("spot_direction_view", dirView)
            }
        }
>>>>>>> Stashed changes

        spotLight?.bind(staticShader, camera.getCalculateViewMatrix())

        staticShader.setUniform("emission_tint", Vector3f(0f, 1f, 0f)) // Boden auf grün
        groundRenderable.render(staticShader)

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

<<<<<<< Updated upstream
=======
        // Motorrad-Controls (optional)
>>>>>>> Stashed changes
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
<<<<<<< Updated upstream
            motorrad?.translate(Vector3f(0f, 0f, moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) {
                motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
            }
            if (window.getKeyState(GLFW_KEY_D)) {
                motorrad?.rotate(0f, rotateSpeed * dt, 0f)
                //motorrad?.translate(Vector3f(0f, 0f, -moveSpeed * dt))
=======
            motorrad?.translate(Vector3f(0f, 0f,  moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
            if (window.getKeyState(GLFW_KEY_D)) motorrad?.rotate(0f,  rotateSpeed * dt, 0f)
        }

        val rig = getActiveRig()
        val cam = getActiveCamera()

        // Yaw (J/L): nur Cam1
        if (activeCam == 0) {
            val yawDir = (if (window.getKeyState(GLFW_KEY_J)) +1f else 0f) +
                    (if (window.getKeyState(GLFW_KEY_L)) -1f else 0f)
            if (yawDir != 0f) {
                val dy = yawDir * yawSpeed * dt
                rig.rotate(0f, dy, 0f)
                yaw1 += dy
>>>>>>> Stashed changes
            }
        }


<<<<<<< Updated upstream
        // Kamera-Matrizen aktualisieren
        camera.getCalculateViewMatrix()
        camera.getCalculateProjectionMatrix()
    }

=======
        // --- NEU: Zoom (U/O) über FOV, NICHT über Distanz ---
        var fovDelta = 0f
        if (window.getKeyState(GLFW_KEY_U)) fovDelta += -fovZoomSpeedRad * dt   // reinzoomen -> kleineres FOV
        if (window.getKeyState(GLFW_KEY_O)) fovDelta +=  +fovZoomSpeedRad * dt  // rauszoomen -> größeres FOV
        if (fovDelta != 0f) {
            cam.fovRad = (cam.fovRad + fovDelta).coerceIn(fovMinRad, fovMaxRad)
        }

        // Objektsteuerung
        if (chooseObj == 1) objectControl(dt, cubeMoveMode, cubeRenderable)
        else if (chooseObj == 2) objectControl(dt, coneMoveMode, coneRenderable)
    }

    // --- Objektsteuerung ---
    fun objectControl(dt: Float, moveMode: Boolean, renderable: Renderable?) {
        val speed = Math.toRadians(90.0).toFloat()
        if (moveMode) {
            if (window.getKeyState(GLFW_KEY_UP))    renderable?.translate(Vector3f(0f, 0f, -speed * dt))
            if (window.getKeyState(GLFW_KEY_DOWN))  renderable?.translate(Vector3f(0f, 0f,  speed * dt))
            if (window.getKeyState(GLFW_KEY_LEFT))  renderable?.translate(Vector3f(-speed * dt, 0f, 0f))
            if (window.getKeyState(GLFW_KEY_RIGHT)) renderable?.translate(Vector3f( speed * dt, 0f, 0f))
        } else {
            if (window.getKeyState(GLFW_KEY_UP))    renderable?.rotate(-speed * dt, 0f, 0f)
            if (window.getKeyState(GLFW_KEY_DOWN))  renderable?.rotate( speed * dt, 0f, 0f)
            if (window.getKeyState(GLFW_KEY_LEFT))  renderable?.rotate(0f, -speed * dt, 0f)
            if (window.getKeyState(GLFW_KEY_RIGHT)) renderable?.rotate(0f,  speed * dt, 0f)
        }
    }
>>>>>>> Stashed changes


    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {}

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