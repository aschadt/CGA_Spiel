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
import org.lwjgl.opengl.GL11.*
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.abs
import kotlin.math.sin

class Scene(private val window: GameWindow) {
    // --- Shader ---
    private val staticShader = ShaderProgram("assets/shaders/tron_vert.glsl", "assets/shaders/tron_frag.glsl")

    // --- Renderables ---
    private var groundRenderable: Renderable
    private var roomRenderable: Renderable
    private var cubeRenderable: Renderable? = null
    private var coneRenderable: Renderable? = null
    private var motorrad: Renderable? = null

    // --- Auswahl / Objektsteuerung ---
    private var cubeMoveMode = true
    private var coneMoveMode = true
    private var chooseObj: Int = 0

    // --- Lichter ---
    private val pointLight = PointLight(Vector3f(0f, 1f, 0f), Vector3f(1f, 1f, 1f))
    private var spotLight: SpotLight? = null
    private val pointLights = listOf(
        pointLight,
        PointLight(Vector3f(-20f, 1.5f, -20f), Vector3f(1f, 0f, 0f)),
        PointLight(Vector3f( 20f, 1.5f, -20f), Vector3f(0f, 1f, 0f)),
        PointLight(Vector3f( 20f, 1.5f,  20f), Vector3f(0f, 0f, 1f)),
        PointLight(Vector3f(-20f, 1.5f,  20f), Vector3f(1f, 1f, 0f))
    )

    // --- Orbit-Kameras + Rigs ---
    private val cam1 = TronCamera()
    private val cam2 = TronCamera()
    private val rig1 = Transformable()
    private val rig2 = Transformable()

    // Orbit-Parameter
    private var yaw1 = 0f               // Cam1: Yaw erlaubt
    private var pitch1 = 0f             // Cam1: Pitch gesperrt (bleibt 0)
    private var dist1 = 6.0f

    private var yaw2 = 0f               // Cam2: Yaw gesperrt (bleibt 0)
    private var pitch2 = -0.35f         // Cam2: Pitch erlaubt
    private var dist2 = 6.0f

    private val yawSpeed = 1.4f
    private val pitchSpeed = 1.0f
    private val zoomSpeed = 6.0f
    private val pitchMin = -1.2f
    private val pitchMax =  1.2f
    private val distMin  = 2.0f
    private val distMax  = 20.0f

    private var activeCam = 0 // 0 = cam1, 1 = cam2

    // --- Kamera-Ziele & Hard-Snap Defaults ---
    private val camTargets = mutableListOf<Transformable>()
    private var camTargetIndex = 0
    private var currentCamTarget: Transformable? = null

    private val cam1DefaultYaw = 0f
    private val cam1DefaultPitch = 0f
    private val cam1YOffset = 1.0f

    private val cam2DefaultYaw = 0f
    private val cam2DefaultPitch = -0.35f
    private val cam2YOffset = 0.0f

    // --- Maus (optionales Yaw für Cam1) ---
    private var firstMouseMove = true
    private var lastMouseX = 0.0

    init {
        // ---------- Ground ----------
        val groundObj = loadOBJ("assets/models/ground.obj")
        val groundMeshList = groundObj.objects[0].meshes
        val groundAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )

        val diffuse = Texture2D("assets/textures/ground_diff.png", true)
        val specular = Texture2D("assets/textures/ground_spec.png", true)
        val emissive = Texture2D("assets/textures/ground_emit.png", true)
        diffuse.setTexParams(GL_REPEAT, GL_REPEAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
        specular.setTexParams(GL_REPEAT, GL_REPEAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
        emissive.setTexParams(GL_REPEAT, GL_REPEAT, GL_LINEAR_MIPMAP_LINEAR, GL_LINEAR)
        val groundMaterial = Material(diffuse, emissive, specular, shininess = 60f, tcMultiplier = Vector2f(64f, 64f))

        val groundMesh = Mesh(groundMeshList[0].vertexData, groundMeshList[0].indexData, groundAttribs, groundMaterial)
        groundRenderable = Renderable(mutableListOf(groundMesh))

        // ---------- Room ----------
        val roomObj = loadOBJ("assets/models/room.obj")
        val roomMeshList = roomObj.objects[0].meshes
        val roomAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val roomMesh = Mesh(roomMeshList[0].vertexData, roomMeshList[0].indexData, roomAttribs, groundMaterial)
        roomRenderable = Renderable(mutableListOf(roomMesh)).apply {
            scale(Vector3f(23.0f, 5.0f, 23.0f))
            rotate(0f, Math.toRadians(-90.0).toFloat(), 0f)
            translate(Vector3f(0.0f, 0.0f, 0.0f))
        }

        // ---------- Cube ----------
        val cubeObj = loadOBJ("assets/models/cube.obj")
        val cubeMeshList = cubeObj.objects[0].meshes
        val cubeAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val cubeMesh = Mesh(cubeMeshList[0].vertexData, cubeMeshList[0].indexData, cubeAttribs, groundMaterial)
        cubeRenderable = Renderable(mutableListOf(cubeMesh)).apply {
            translate(Vector3f(0.0f, 2.0f, -4.0f))
        }

        // ---------- Cone ----------
        val coneObj = loadOBJ("assets/models/cone.obj")
        val coneMeshList = coneObj.objects[0].meshes
        val coneAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val coneMesh = Mesh(coneMeshList[0].vertexData, coneMeshList[0].indexData, coneAttribs, groundMaterial)
        coneRenderable = Renderable(mutableListOf(coneMesh)).apply {
            translate(Vector3f(0.0f, 2.0f, -2.0f))
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // ---------- Motorrad (optional) ----------
        motorrad = ModelLoader.loadModel(
            "assets/models/Light Cycle/HQ_Movie cycle.obj",
            Math.toRadians(-90.0).toFloat(),
            Math.toRadians(90.0).toFloat(),
            0f
        )?.apply {
            scale(Vector3f(0.8f))
        }

        // ---------- Lichter ----------
        pointLight.parent = motorrad   // initial – wird im setCameraTarget ggf. neu geparentet
        pointLight.translate(Vector3f(0f, 1.5f, 0f))
        spotLight = SpotLight(
            position = Vector3f(0f, 1.5f, 0f),
            color = Vector3f(1f, 1f, 1f),
            innerAngle = Math.toRadians(20.0).toFloat(),
            outerAngle = Math.toRadians(25.0).toFloat()
        ).also { it.parent = motorrad }

        // ---------- Orbit-Setup nach dem Erzeugen der Objekte ----------
        // Cams an Rigs hängen + Start-Offsets
        cam1.parent = rig1
        cam2.parent = rig2
        cam1.translate(Vector3f(0f, cam1YOffset, dist1))
        cam2.translate(Vector3f(0f, cam2YOffset, dist2))
        // Rig2: andere Ebene (90° um X vorrotiert)
        rig2.rotate(Math.toRadians(270.0).toFloat(), 0f, 0f)
        // Start-Pitchs
        if (abs(pitch1) > 1e-6f) rig1.rotate(pitch1, 0f, 0f)
        if (abs(pitch2) > 1e-6f) rig2.rotate(pitch2, 0f, 0f)

        // ---------- Kamera-Ziele festlegen & erstes Ziel (Hard-Snap) ----------
        camTargets.clear()
        cubeRenderable?.let { camTargets += it }
        coneRenderable?.let { camTargets += it }
        motorrad?.let     { camTargets += it }
        if (camTargets.isNotEmpty()) setCameraTarget(camTargets[0], snap = true)

        // ---------- OpenGL State ----------
        glClearColor(0f, 0f, 0f, 1f); GLError.checkThrow()
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)
    }

    // --- Aktive Kamera/Rig/Parameter ---
    private fun getActiveCamera(): TronCamera = if (activeCam == 0) cam1 else cam2
    private fun getActiveRig(): Transformable = if (activeCam == 0) rig1 else rig2
    private fun getPitchRef(): Float = if (activeCam == 0) pitch1 else pitch2
    private fun setPitchRef(v: Float) { if (activeCam == 0) pitch1 = v else pitch2 = v }
    private fun getDistRef(): Float = if (activeCam == 0) dist1 else dist2
    private fun setDistRef(v: Float) { if (activeCam == 0) dist1 = v else dist2 = v }

    // --- Hard-Snap auf Standardpose beim Zielwechsel ---
    private fun setCameraTarget(target: Transformable, snap: Boolean = true) {
        currentCamTarget = target
        // Rigs an neues Ziel hängen
        rig1.parent = target
        rig2.parent = target
        // (Optional) Lichter mitslaven
        spotLight?.parent = target
        pointLight.parent = target

        if (!snap) return
        val eps = 1e-6f

        // ---- Cam 1: Yaw erlaubt, Pitch gesperrt ----
        if (abs(yaw1)   > eps) rig1.rotate(0f, -yaw1, 0f)
        if (abs(pitch1) > eps) rig1.rotate(-pitch1, 0f, 0f)
        yaw1   = cam1DefaultYaw
        pitch1 = cam1DefaultPitch
        if (abs(pitch1) > eps) rig1.rotate(pitch1, 0f, 0f)
        if (abs(yaw1)   > eps) rig1.rotate(0f, yaw1, 0f)
        val p1 = cam1.getPosition()
        val dY1 = cam1YOffset - p1.y
        val dZ1 = dist1       - p1.z
        if (abs(dY1) > eps || abs(dZ1) > eps) cam1.translate(Vector3f(0f, dY1, dZ1))

        // ---- Cam 2: Pitch erlaubt, Yaw gesperrt ----
        if (abs(yaw2)   > eps) rig2.rotate(0f, -yaw2, 0f)
        if (abs(pitch2) > eps) rig2.rotate(-pitch2, 0f, 0f)
        yaw2   = cam2DefaultYaw
        pitch2 = cam2DefaultPitch
        if (abs(pitch2) > eps) rig2.rotate(pitch2, 0f, 0f)
        if (abs(yaw2)   > eps) rig2.rotate(0f, yaw2, 0f)
        val p2 = cam2.getPosition()
        val dY2 = cam2YOffset - p2.y
        val dZ2 = dist2       - p2.z
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
    fun render(dt: Float, t: Float) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        staticShader.use()

        // Nur aktive Orbit-Kamera binden
        val cam = getActiveCamera()
        cam.bind(staticShader)
        val view = cam.getCalculateViewMatrix()

        // Punktlichter (Viewspace)
        staticShader.setUniform("numPointLights", pointLights.size)
        for ((index, light) in pointLights.withIndex()) {
            val viewPos = view.transformPosition(light.getWorldPosition(), Vector3f())
            staticShader.setUniform("pointLight_positions[$index]", viewPos)
            staticShader.setUniform("pointLight_colors[$index]", light.color)
        }

        // Einzelnes PointLight (für vertex shader 'light_position')
        pointLight.bind(staticShader)

        // Spotlight
        spotLight?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view) // setzt u.a. direction (Basis) + cutoffs

            // explizit zum aktuellen Target ausrichten (falls vorhanden)
            val target = currentCamTarget ?: cubeRenderable
            target?.let {
                val dirWorld = Vector3f(it.getWorldPosition()).sub(sp.getWorldPosition()).normalize()
                val dirView  = view.transformDirection(dirWorld, Vector3f()).normalize()
                staticShader.setUniform("spot_direction_view", dirView)
            }
        }

        // Boden
        staticShader.setUniform("emission_tint", Vector3f(0f, 1f, 0f))
        groundRenderable.render(staticShader)

        // Animierte Emission
        val r = (sin(t * 2.0) * 0.5 + 0.5).toFloat()
        val g = (sin(t * 0.7 + 2.0) * 0.5 + 0.5).toFloat()
        val b = (sin(t * 1.3 + 4.0) * 0.5 + 0.5).toFloat()
        staticShader.setUniform("emission_tint", Vector3f(r, g, b))

        cubeRenderable?.render(staticShader)
        coneRenderable?.render(staticShader)
        roomRenderable.render(staticShader)
        motorrad?.render(staticShader)
    }

    // --- Update ---
    fun update(dt: Float, t: Float) {
        val moveSpeed = 8.0f
        val rotateSpeed = Math.toRadians(90.0).toFloat()

        // Motorrad bewegen/rotieren (dein Code)
        if (window.getKeyState(GLFW_KEY_W)) {
            motorrad?.translate(Vector3f(0f, 0f, -moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) motorrad?.rotate(0f,  rotateSpeed * dt, 0f)
            if (window.getKeyState(GLFW_KEY_D)) motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
        }
        if (window.getKeyState(GLFW_KEY_S)) {
            motorrad?.translate(Vector3f(0f, 0f,  moveSpeed * dt))
            if (window.getKeyState(GLFW_KEY_A)) motorrad?.rotate(0f, -rotateSpeed * dt, 0f)
            if (window.getKeyState(GLFW_KEY_D)) motorrad?.rotate(0f,  rotateSpeed * dt, 0f)
        }

        val rig = getActiveRig()
        val cam = getActiveCamera()

        // Yaw (J/L): nur Cam1
        if (activeCam == 0) {
            val yawDir =
                (if (window.getKeyState(GLFW_KEY_J)) +1f else 0f) +
                        (if (window.getKeyState(GLFW_KEY_L)) -1f else 0f)
            if (yawDir != 0f) {
                val dy = yawDir * yawSpeed * dt
                rig.rotate(0f, dy, 0f)
                yaw1 += dy
            }
        }

        // Pitch (I/K): nur Cam2
        if (activeCam == 1) {
            var dp = 0f
            if (window.getKeyState(GLFW_KEY_I)) dp += +pitchSpeed * dt
            if (window.getKeyState(GLFW_KEY_K)) dp += -pitchSpeed * dt
            val newPitch = (getPitchRef() + dp).coerceIn(pitchMin, pitchMax)
            val apply = newPitch - getPitchRef()
            if (abs(apply) > 1e-6f) {
                rig.rotate(apply, 0f, 0f)
                setPitchRef(newPitch)
            }
        }

        // Zoom (U/O): beide Cams
        var zDelta = 0f
        if (window.getKeyState(GLFW_KEY_U)) zDelta += -zoomSpeed * dt
        if (window.getKeyState(GLFW_KEY_O)) zDelta += +zoomSpeed * dt
        if (zDelta != 0f) {
            val cur = getDistRef()
            val next = (cur + zDelta).coerceIn(distMin, distMax)
            val dz = next - cur
            if (abs(dz) > 1e-6f) {
                cam.translate(Vector3f(0f, 0f, dz))
                setDistRef(next)
            }
        }

        // Objektsteuerung
        if (chooseObj == 1) objectControl(dt, cubeMoveMode, cubeRenderable)
        else if (chooseObj == 2) objectControl(dt, coneMoveMode, coneRenderable)
    }

    // --- Objektsteuerung: Bewegen/Rotieren mit Pfeiltasten + TAB ---
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

    // --- Tastatur ---
    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {
        if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
            when (chooseObj) {
                1 -> { cubeMoveMode = !cubeMoveMode; println("Cube: ${if (cubeMoveMode) "Bewegen" else "Rotieren"}") }
                2 -> { coneMoveMode = !coneMoveMode; println("Cone: ${if (coneMoveMode) "Bewegen" else "Rotieren"}") }
            }
        }
        if (key == GLFW_KEY_C && action == GLFW_PRESS) {
            activeCam = 1 - activeCam
            println("Aktive Kamera: ${if (activeCam == 0) "Cam 1 (Yaw, kein Pitch)" else "Cam 2 (Pitch, kein Yaw)"}")
        }
        if (key == GLFW_KEY_T && action == GLFW_PRESS) cycleCameraTarget(true)   // nächstes Ziel
        if (key == GLFW_KEY_R && action == GLFW_PRESS) cycleCameraTarget(false)  // vorheriges Ziel

        if (key == GLFW_KEY_1 && action == GLFW_PRESS) { chooseObj = 1; println("Objekt $chooseObj ausgewählt (Cube)") }
        if (key == GLFW_KEY_2 && action == GLFW_PRESS) { chooseObj = 2; println("Objekt $chooseObj ausgewählt (Cone)") }
    }

    // --- Maus: optional Yaw nur für Cam1 ---
    fun onMouseMove(xpos: Double, ypos: Double) {
        if (firstMouseMove) { lastMouseX = xpos; firstMouseMove = false; return }
        val dx = xpos - lastMouseX
        lastMouseX = xpos
        val sensitivity = 0.002f
        val dy = (-dx * sensitivity).toFloat()
        if (activeCam == 0) { // nur Cam1 darf yaw
            rig1.rotate(0f, dy, 0f)
            yaw1 += dy
        }
    }

    fun cleanup() {}
}
