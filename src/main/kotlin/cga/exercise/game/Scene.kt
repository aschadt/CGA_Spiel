package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.*
import cga.exercise.components.light.PointLight
import cga.exercise.components.light.SpotLight
import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.texture.Texture2D
import cga.exercise.components.shadow.ShadowRenderer
import cga.exercise.components.blackscreen.FadeOverlay          // NEU: Overlay import
import cga.exercise.components.level.Level
import cga.exercise.components.level.LevelLoader
import cga.framework.GLError
import cga.framework.GameWindow
import cga.framework.ModelLoader
import cga.framework.OBJLoader.loadOBJ
import org.joml.Matrix4f
import org.joml.Vector2f
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL11.*
import kotlin.math.abs
import kotlin.math.sin
import kotlin.system.exitProcess                           // NEU: Beenden

class Scene(private val window: GameWindow) {
    // --- Shader ---
    private val staticShader = ShaderProgram("assets/shaders/tron_vert.glsl", "assets/shaders/tron_frag.glsl")

    // --- Shadow Mapping (zwei Shadow-Maps: Test-Spot + Anchor-Spot) ---
    private val shadow1 = ShadowRenderer(1024, 1024)
    private val shadow2 = ShadowRenderer(1024, 1024)
    private val shadowUnit1 = 7
    private val shadowUnit2 = 8 // wird aktuell nicht im Shader genutzt, nur Depth-Pass zum Testen

    private var currentLevel: Level? = null
    private var levelIndex = 0

    // --- Renderables ---

    private var motorrad: Renderable? = null

    // --- Auswahl / Objektsteuerung ---

    private val objMoveModes = mutableMapOf<Renderable, Boolean>()

    private var chooseObj: Int = 0

    // --- Lichter ---
    private val pointLight = PointLight(Vector3f(0f, 1f, 0f), Vector3f(1f, 1f, 1f))
    private var spotLight: SpotLight? = null
    private var testSpot: SpotLight? = null      // vor dem Cone
    private var bikeSpot: SpotLight? = null      // neben dem Anchor

    private val pointLights = listOf(
        pointLight,
        PointLight(Vector3f(-9f, 1.5f, -9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f( 9f, 1.5f, -9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f( 9f, 1.5f,  9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f(-9f, 1.5f,  9f), Vector3f(1f, 1f, 1f))
    )

    // --- Orbit-Kameras + Rigs ---
    private val cam1 = TronCamera()   // Anchor-Kamera (umschaltbar)
    private val cam2 = TronCamera()   // zweite Kamera (immer perspektivisch)
    private val rig1 = Transformable()
    private val rig2 = Transformable()

    private var yaw1 = 0f
    private var pitch1 = 0f
    private var dist1 = 6.0f

    private var yaw2 = 0f
    private var pitch2 = -0.35f
    private var dist2 = 2.6f

    private val yawSpeed = 1.4f
    private val pitchSpeed = 1.0f
    private val pitchMin = -1.2f
    private val pitchMax =  1.2f
    private val distMin  = 2.0f
    private val distMax  = 20.0f

    // Zoom
    private val fovMinRad = Math.toRadians(20.0).toFloat()
    private val fovMaxRad = Math.toRadians(100.0).toFloat()
    private val fovZoomSpeedRad = Math.toRadians(60.0).toFloat()
    private val anchorOrthoZoomSpeed = 5.0f  // Änderung der Ortho-Höhe pro Sekunde

    private var activeCam = 0 // 0 = cam1, 1 = cam2

    // --- Kamera-Ziele ---
    private val camTargets = mutableListOf<Transformable>()
    private var camTargetIndex = 0
    private var currentCamTarget: Transformable? = null

    // Kamera-Y-Offets (Default für normale Objekte)
    private val cam1YOffsetDefault = 1.0f
    private val cam1YOffsetBike    = 4.0f
    private val cam2YOffsetDefault = 0.0f
    private val cam2YOffsetBike    = 0.8f

    // Anchor-spezifische Offsets + Grenzen (Boden ↔ Decke)
    private val cam1YOffsetAnchorStart = 4.0f
    private val cam2YOffsetAnchorStart = 0.8f
    private var cam1YOffsetAnchor = cam1YOffsetAnchorStart
    private var cam2YOffsetAnchor = cam2YOffsetAnchorStart
    private val camHeightAdjustSpeed = 1.5f
    private val camHeightMinAnchor   = 0.2f
    private val camHeightMaxAnchor   = 4.8f
    private fun clampAnchorOffset(v: Float) = v.coerceIn(camHeightMinAnchor, camHeightMaxAnchor)

    private fun cam1YOffsetFor(target: Transformable?): Float =
        if (target != null && followAnchor != null && target === followAnchor)
            clampAnchorOffset(cam1YOffsetAnchor) else cam1YOffsetDefault
    private fun cam2YOffsetFor(target: Transformable?): Float =
        if (target != null && followAnchor != null && target === followAnchor)
            clampAnchorOffset(cam2YOffsetAnchor) else cam2YOffsetDefault

    private data class OrbitState(var yaw: Float = 0f, var pitch: Float = 0f, var dist: Float = 6f)
    private val cam1States = mutableMapOf<Transformable, OrbitState>()
    private val cam2States = mutableMapOf<Transformable, OrbitState>()

    // Ziel, dem die Rigs nur translational folgen (kein Parent)
    private var followTarget: Transformable? = null

    private var firstMouseMove = true
    private var lastMouseX = 0.0

    private companion object { private const val EPS_POS2 = 1e-10f }

    // --- Raum-Kollision (Innenraum-AABB) ---
    private val roomMin = Vector3f(-8.8f, 0.0f, -8.8f)
    private val roomMax = Vector3f( 8.8f, 5.0f,  8.8f)
    private val wallMargin = 0.30f

    private fun clampInsideRoom(p: Vector3f, margin: Float = wallMargin): Vector3f {
        return Vector3f(
            p.x.coerceIn(roomMin.x + margin, roomMax.x - margin),
            p.y.coerceIn(roomMin.y + margin, roomMax.y - margin),
            p.z.coerceIn(roomMin.z + margin, roomMax.z - margin)
        )
    }

    /** Korrigiert die Weltposition eines Transformable in die Raum-AABB hinein. */
    private fun clampTransformableToRoom(t: Transformable, margin: Float = wallMargin) {
        val wp = t.getWorldPosition()
        val clamped = clampInsideRoom(Vector3f(wp), margin)
        if (!wp.equals(clamped, 1e-5f)) {
            val corr = clamped.sub(wp)
            t.preTranslate(corr)
        }
    }

    // Prüft, ob es die freie Anchor-Kamera ist (cam1 + Target = followAnchor)
    private fun isFreeAnchorCam(cam: TronCamera): Boolean {
        return (cam === cam1) && (currentCamTarget != null) && (currentCamTarget === followAnchor)
    }

    // Klemmt Kameras nur, wenn es NICHT die freie Anchor-Kamera ist
    private fun clampCameraIfNeeded(cam: TronCamera, margin: Float = wallMargin + 0.05f) {
        if (isFreeAnchorCam(cam)) return
        clampTransformableToRoom(cam, margin)
    }

    // ---------- Hilfsfunktionen (Eingaben/Bewegung/Zoom) ----------
    private fun isDown(key: Int) = window.getKeyState(key)

    private fun axis(plusKey: Int, minusKey: Int): Float {
        var a = 0f
        if (isDown(plusKey)) a += 1f
        if (isDown(minusKey)) a -= 1f
        return a
    }

    private data class CamBasis(val fwdXZ: Vector3f, val rightXZ: Vector3f)
    private fun camBasisXZ(cam: TronCamera): CamBasis {
        val inv = Matrix4f(cam.getCalculateViewMatrix()).invert()
        val fwd   = inv.transformDirection(Vector3f(0f, 0f, -1f)).apply { y = 0f; if (lengthSquared() > 1e-8f) normalize() }
        val right = inv.transformDirection(Vector3f(1f, 0f,  0f)).apply { y = 0f; if (lengthSquared() > 1e-8f) normalize() }
        return CamBasis(fwd, right)
    }

    private fun moveTransformAlongCamXZ(
        t: Transformable,
        cam: TronCamera,
        speed: Float,
        dt: Float,
        forwardAxis: Float,
        rightAxis: Float
    ) {
        if (forwardAxis == 0f && rightAxis == 0f) return
        val (fwd, right) = camBasisXZ(cam)
        val dir = Vector3f()
            .add(fwd.mul(forwardAxis, Vector3f()))
            .add(right.mul(rightAxis, Vector3f()))
        if (dir.lengthSquared() > 0f) {
            dir.normalize().mul(speed * dt)
            t.preTranslate(dir)
        }
    }

    private fun handleZoomPerspective(cam: TronCamera, zoomIn: Boolean, zoomOut: Boolean, dt: Float) {
        var df = 0f
        if (zoomIn)  df += -fovZoomSpeedRad * dt
        if (zoomOut) df +=  +fovZoomSpeedRad * dt
        if (df != 0f) cam.fovRad = (cam.fovRad + df).coerceIn(fovMinRad, fovMaxRad)
    }

    private fun handleZoomOrtho(cam: TronCamera, zoomIn: Boolean, zoomOut: Boolean, dt: Float) {
        var dh = 0f
        if (zoomIn)  dh += -anchorOrthoZoomSpeed * dt
        if (zoomOut) dh +=  +anchorOrthoZoomSpeed * dt
        if (dh != 0f) cam.addOrthoHeight(dh)
    }

    private fun handleZoom(dt: Float) {
        val zoomIn = isDown(GLFW_KEY_U)
        val zoomOut = isDown(GLFW_KEY_O)
        if (activeCam == 0 && cam1.projectionMode == TronCamera.ProjectionMode.Orthographic) {
            handleZoomOrtho(cam1, zoomIn, zoomOut, dt)
        } else {
            handleZoomPerspective(getActiveCamera(), zoomIn, zoomOut, dt)
        }
    }

    private fun applyYawToRigIfCam1(dt: Float) {
        if (activeCam != 0) return
        val yawDir = axis(GLFW_KEY_J, GLFW_KEY_L) // J = +, L = -
        if (yawDir != 0f) {
            val dy = yawDir * yawSpeed * dt
            rig1.rotate(0f, dy, 0f)
            yaw1 += dy
        }
    }

    private fun applyPitchToRigIfCam2(dt: Float) {
        if (activeCam != 1) return
        val dp = (if (isDown(GLFW_KEY_I)) +pitchSpeed * dt else 0f) +
                (if (isDown(GLFW_KEY_K)) -pitchSpeed * dt else 0f)
        val newPitch = (getPitchRef() + dp).coerceIn(pitchMin, pitchMax)
        val apply = newPitch - getPitchRef()
        if (abs(apply) > 1e-6f) {
            rig2.rotate(apply, 0f, 0f)
            setPitchRef(newPitch)
        }
    }

    private val anchorMoveSpeed = 8.0f
    private fun moveAnchorWithWASD(dt: Float) {
        followAnchor?.let { anchor ->
            val cam = getActiveCamera()
            val axF = axis(GLFW_KEY_W, GLFW_KEY_S)
            val axR = axis(GLFW_KEY_D, GLFW_KEY_A)
            moveTransformAlongCamXZ(anchor, cam, anchorMoveSpeed, dt, axF, axR)
        }
    }

    private fun adjustActiveCamHeightIfAnchorTarget(dt: Float) {
        if (currentCamTarget != followAnchor || followAnchor == null) return
        var dh = 0f
        if (isDown(GLFW_KEY_M)) dh += camHeightAdjustSpeed * dt
        if (isDown(GLFW_KEY_N)) dh -= camHeightAdjustSpeed * dt
        if (dh == 0f) return

        if (activeCam == 0) {
            val newOffset = clampAnchorOffset(cam1YOffsetAnchor + dh)
            val apply = newOffset - cam1YOffsetAnchor
            if (abs(apply) > 1e-6f) {
                cam1.translate(Vector3f(0f, apply, 0f))
                cam1YOffsetAnchor = newOffset
            }
        } else {
            val newOffset = clampAnchorOffset(cam2YOffsetAnchor + dh)
            val apply = newOffset - cam2YOffsetAnchor
            if (abs(apply) > 1e-6f) {
                cam2.translate(Vector3f(0f, apply, 0f))
                cam2YOffsetAnchor = newOffset
            }
        }
    }
    // ---------- Ende Hilfsfunktionen ----------

    // --- Zeitbasiertes Abdunkeln & Auto-Beenden ---
    private val fadeOverlay = FadeOverlay()
    private val totalTimeToBlack = 300.0f      // 5 Minuten
    private val finalFadeDuration = 120.0f     // letzte 2 Minuten abdunkeln
    private var nowT = 0f
    private var quitIssued = false

    // --- Sofort-Schwarz per Taste ---
    private var forceBlackout = false
    private var forceBlackoutTimer = 0f
    private val forceBlackoutHold = 1.0f   // 1s halten, dann beenden

    init {

        loadLevel(0)

        // --- UNSICHTBARER FOLLOW-ANCHOR (ersetzt Motorrad) ---
        val anchorObj = loadOBJ("assets/models/cube.obj")
        val anchorMeshList = anchorObj.objects[0].meshes
        val anchorAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val anchorMesh = Mesh(anchorMeshList[0].vertexData, anchorMeshList[0].indexData, anchorAttribs, oldGroundMaterial)
        followAnchor = Renderable(mutableListOf(anchorMesh)).apply {
            translate(Vector3f(0f, 1.0f, 0f))
            scale(Vector3f(0.8f))
        }
        // Anchor wird NICHT gerendert

        // Lichter (startweise) am Anchor
        pointLight.parent = followAnchor
        pointLight.translate(Vector3f(0f, 1.5f, 0f))

        spotLight = SpotLight(
            position = Vector3f(0f, 1.5f, 0f),
            color = Vector3f(1f, 1f, 1f),
            innerAngle = Math.toRadians(20.0).toFloat(),
            outerAngle = Math.toRadians(25.0).toFloat()
        ).also { it.parent = followAnchor }

        testSpot = SpotLight(
            position = Vector3f(0f, 3f, 0f),
            color = Vector3f(1f, 1f, 1f),
            innerAngle = Math.toRadians(18.0).toFloat(),
            outerAngle = Math.toRadians(24.0).toFloat()
        )

        bikeSpot = SpotLight(
            position = Vector3f(0.8f, 3.0f, 0.0f),
            color = Vector3f(1f, 0.95f, 0.9f),
            innerAngle = Math.toRadians(18.0).toFloat(),
            outerAngle = Math.toRadians(26.0).toFloat()
        ).also { it.parent = followAnchor }

        // Orbit-Setup
        cam1.parent = rig1
        cam2.parent = rig2
        cam1.translate(Vector3f(0f, cam1YOffsetDefault, dist1))
        cam2.translate(Vector3f(0f, cam2YOffsetDefault, dist2))
        rig2.rotate(Math.toRadians(270.0).toFloat(), 0f, 0f)
        if (abs(pitch1) > 1e-6f) rig1.rotate(pitch1, 0f, 0f)
        if (abs(pitch2) > 1e-6f) rig2.rotate(pitch2, 0f, 0f)
        cam1.fovRad = Math.toRadians(90.0).toFloat()
        cam2.fovRad = Math.toRadians(90.0).toFloat()

        // Kamera-Ziele (Anchor als letztes Ziel hinzufügen)
        camTargets.clear()
        currentLevel?.objects?.forEach { camTargets += it }
        motorrad?.let     { camTargets += it }
        if (camTargets.isNotEmpty()) setCameraTarget(camTargets[0], snap = true)

        // OpenGL State
        glClearColor(0f, 0f, 0f, 1f); GLError.checkThrow()
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)
    }

    // nur Translation zum Ziel (kein Parent -> keine Rotation/Scale erben)
    private fun moveRigToTargetPosition(rig: Transformable, target: Transformable) {
        val delta = Vector3f(target.getWorldPosition()).sub(rig.getWorldPosition())
        if (delta.lengthSquared() > EPS_POS2) rig.preTranslate(delta)
    }

    // Aktive Kamera/Rig/Parameter
    private fun getActiveCamera(): TronCamera = if (activeCam == 0) cam1 else cam2
    private fun getActiveRig(): Transformable = if (activeCam == 0) rig1 else rig2
    private fun getPitchRef(): Float = if (activeCam == 0) pitch1 else pitch2
    private fun setPitchRef(v: Float) { if (activeCam == 0) pitch1 = v else pitch2 = v }
    private fun getDistRef(): Float = if (activeCam == 0) dist1 else dist2
    private fun setDistRef(v: Float) { if (activeCam == 0) dist1 = v else dist2 = v }

    // Hard-Snap & Zielwechsel; Rigs folgen nur translational
    private fun setCameraTarget(target: Transformable, snap: Boolean = true) {
        val eps = 1e-6f

        // alten State sichern
        currentCamTarget?.let { prev ->
            cam1States[prev] = OrbitState(yaw1, pitch1, dist1)
            cam2States[prev] = OrbitState(yaw2, pitch2, dist2)
        }

        currentCamTarget = target
        followTarget = target
        spotLight?.parent = target
        pointLight.parent = target

        // Falls Anchor: Offsets clampen (Sicherheit)
        if (followAnchor != null && target === followAnchor) {
            cam1YOffsetAnchor = clampAnchorOffset(cam1YOffsetAnchor)
            cam2YOffsetAnchor = clampAnchorOffset(cam2YOffsetAnchor)
        }

        if (!snap) return

        // Rigs zur Zielposition schieben (nur Translation)
        moveRigToTargetPosition(rig1, target)
        moveRigToTargetPosition(rig2, target)

        // gewünschten Zustand laden (oder Defaults)
        val s1 = cam1States[target] ?: OrbitState(0f, 0f, dist1)
        val s2 = cam2States[target] ?: OrbitState(0f, -0.35f, dist2)

        // Cam1: Yaw/Pitch reset -> anwenden
        if (abs(yaw1)   > eps) rig1.rotate(0f, -yaw1, 0f)
        if (abs(pitch1) > eps) rig1.rotate(-pitch1, 0f, 0f)
        yaw1 = s1.yaw; pitch1 = s1.pitch
        if (abs(pitch1) > eps) rig1.rotate(pitch1, 0f, 0f)
        if (abs(yaw1)   > eps) rig1.rotate(0f, yaw1, 0f)

        // Cam2: Yaw/Pitch reset -> anwenden
        if (abs(yaw2)   > eps) rig2.rotate(0f, -yaw2, 0f)
        if (abs(pitch2) > eps) rig2.rotate(-pitch2, 0f, 0f)
        yaw2 = s2.yaw; pitch2 = s2.pitch
        if (abs(pitch2) > eps) rig2.rotate(pitch2, 0f, 0f)
        if (abs(yaw2)   > eps) rig2.rotate(0f, yaw2, 0f)

        // Kamera-Offsets (Y+Dist) setzen
        val p1 = cam1.getPosition()
        dist1 = s1.dist
        val y1 = cam1YOffsetFor(target)
        val dY1 = y1    - p1.y
        val dZ1 = dist1 - p1.z
        if (abs(dY1) > eps || abs(dZ1) > eps) cam1.translate(Vector3f(0f, dY1, dZ1))

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

    // --- Render: 2-Pass ---
    fun render(dt: Float, t: Float) {
        val level = currentLevel ?: return

        val vp = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, vp)

        // Light-space 1: Test-Spot -> zielt auf Cone
        val ls1: Matrix4f? = testSpot?.let { sp ->
            val pos = sp.getWorldPosition()
            val target = currentLevel?.objects?.getOrNull(1)?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
            shadow1.buildLightSpacePerspective(pos, target, fovRad = Math.toRadians(60.0).toFloat(), near = 0.1f, far = 100f)
        }

        // Light-space 2: Anchor-Spot -> zielt in Fahrtrichtung des Anchors
        val ls2: Matrix4f? = bikeSpot?.let { sp ->
            val pos = sp.getWorldPosition()
            val targ = followAnchor?.getWorldPosition()?.add(0f, 0f, -2f) ?: Vector3f(0f, 0f, -2f)
            shadow2.buildLightSpacePerspective(pos, targ, fovRad = Math.toRadians(60.0).toFloat(), near = 0.1f, far = 100f)
        }

        // Depth-Pass 1
        if (ls1 != null) {
            shadow1.beginDepthPass(ls1)
            val ds = shadow1.depthShader()
            level.ground.renderDepth(ds)
            level.objects.forEach { it.renderDepth(ds) }
            level.room.renderDepth(ds)
            motorrad?.renderDepth(ds)
            shadow1.endDepthPass()
        }

        // Depth-Pass 2
        if (ls2 != null) {
            shadow2.beginDepthPass(ls2)
            val ds2 = shadow2.depthShader()
            level.ground.renderDepth(ds2)
            level.objects.forEach { it.renderDepth(ds2) }
            level.room.renderDepth(ds2)
            motorrad?.renderDepth(ds2)
            shadow2.endDepthPass()
        }

        glViewport(vp[0], vp[1], vp[2], vp[3])

        // Scene-Pass
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        staticShader.use()

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

        // Einzelnes PointLight
        pointLight.bind(staticShader)

        // Test-Spot als aktives Shadow-Licht
        testSpot?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)
            val conePos = currentLevel?.objects?.getOrNull(1)?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
            val dirWorld = Vector3f(conePos).sub(sp.getWorldPosition()).normalize()
            val dirView  = view.transformDirection(dirWorld, Vector3f()).normalize()
            staticShader.setUniform("spot_direction_view", dirView)
            ls1?.let { shadow1.bindForScenePass(staticShader, it, unit = shadowUnit1) }
        }

        // Anchor-Spot leuchtet mit
        bikeSpot?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)
        }

        // Boden + Objekte
        val r = (sin(t * 2.0) * 0.5 + 0.5).toFloat()
        val g = (sin(t * 0.7 + 2.0) * 0.5 + 0.5).toFloat()
        val b = (sin(t * 1.3 + 4.0) * 0.5 + 0.5).toFloat()
        staticShader.setUniform("emission_tint", Vector3f(r, g, b))


        level.ground.render(staticShader)
        level.room.render(staticShader)
        level.objects.forEach { it.render(staticShader) }

        motorrad?.render(staticShader)

        // Abdunkel-Overlay
        val alpha = if (forceBlackout) 1f else fadeAlpha()
        fadeOverlay.draw(alpha)
    }

    // --- Update ---
    fun update(dt: Float, t: Float) {
        // Zeit/Beenden
        nowT = t
        if (forceBlackout) {
            forceBlackoutTimer += dt
            if (forceBlackoutTimer >= forceBlackoutHold) { requestClose(); return }
        }
        if (nowT >= totalTimeToBlack) { requestClose(); return }

        // Rigs folgen nur der Zielposition (keine Rotation erben)
        followTarget?.let { tgt ->
            moveRigToTargetPosition(rig1, tgt)
            moveRigToTargetPosition(rig2, tgt)
        }

        // Anchor mit WASD relativ zur aktiven Kamera bewegen
        moveAnchorWithWASD(dt)

        // Kameraeingaben
        applyYawToRigIfCam1(dt)
        applyPitchToRigIfCam2(dt)

        // Zoom
        handleZoom(dt)

        // Kamerahöhe (nur wenn Target = Anchor)
        adjustActiveCamHeightIfAnchorTarget(dt)

        // Kameras im Raum halten (freie Anchor-Kamera wird NICHT geklemmt)
        clampCameraIfNeeded(cam1)
        clampCameraIfNeeded(cam2)

        // Objektsteuerung + einfache Kollisionsvermeidung
        currentLevel?.let { lvl ->
            if (lvl.objects.isNotEmpty() && chooseObj in lvl.objects.indices) {
                val obj = lvl.objects[chooseObj]
                val others = lvl.objects.filter { it != obj }
                val moveMode = objMoveModes[obj] ?: true
                objectControl(dt, moveMode, obj, others)
            }
        }

    }

    private fun fadeAlpha(): Float {
        val fadeStart = totalTimeToBlack - finalFadeDuration
        val x = (nowT - fadeStart) / finalFadeDuration
        return x.coerceIn(0f, 1f)
    }

    private fun requestClose() {
        if (quitIssued) return
        quitIssued = true
        exitProcess(0)
    }

    fun objectControl (dt: Float, moveMode: Boolean, renderable: Renderable?, allObjects: List<Renderable>) {
        val speed = Math.toRadians(90.0).toFloat()

        if (moveMode && renderable != null) {
            if (window.getKeyState(GLFW_KEY_UP))    tryMove(renderable, Vector3f(0f, 0f, -speed * dt), allObjects)
            if (window.getKeyState(GLFW_KEY_DOWN))  tryMove(renderable, Vector3f(0f, 0f,  speed * dt), allObjects)
            if (window.getKeyState(GLFW_KEY_LEFT))  tryMove(renderable, Vector3f(-speed * dt, 0f, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_RIGHT)) tryMove(renderable, Vector3f( speed * dt, 0f, 0f), allObjects)
        } else if (renderable != null) {
            if (window.getKeyState(GLFW_KEY_UP))    tryRotate(renderable, Vector3f(-speed * dt, 0f, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_DOWN))  tryRotate(renderable, Vector3f( speed * dt, 0f, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_LEFT))  tryRotate(renderable, Vector3f(0f, -speed * dt, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_RIGHT)) tryRotate(renderable, Vector3f(0f,  speed * dt, 0f), allObjects)
        }
    }

    // --- NEU: Bewegungs-/Rotationsversuche mit Raum- und Objektkollision ---
    private fun tryMove(obj: Renderable, move: Vector3f, others: List<Renderable>) {
        val prev = obj.getWorldPosition()
        obj.translate(move)
        clampTransformableToRoom(obj) // im Raum halten

        val currentBox = obj.getKollision()
        val collision = others.any { it != obj && currentBox.intersects(it.getKollision()) }
        if (collision) {
            // Rückgängig auf alte exakte Weltposition
            val now = obj.getWorldPosition()
            obj.preTranslate(prev.sub(now))
        }
    }

    private fun tryRotate(obj: Renderable, rot: Vector3f, others: List<Renderable>) {
        val prevPos = obj.getWorldPosition()
        obj.rotate(rot.x, rot.y, rot.z)

        val posAfter = obj.getWorldPosition()
        val clamped  = clampInsideRoom(Vector3f(posAfter))
        val outside  = !posAfter.equals(clamped, 1e-5f)

        val currentBox = obj.getKollision()
        val hitOther = others.any { it != obj && currentBox.intersects(it.getKollision()) }

        if (outside || hitOther) {
            // Rotation rückgängig machen
            obj.rotate(-rot.x, -rot.y, -rot.z)
            // minimale Drift korrigieren
            val now = obj.getWorldPosition()
            obj.preTranslate(prevPos.sub(now))
        }
    }

    fun loadLevel(index: Int) {
        currentLevel = when (index) {
            0 -> LevelLoader.loadLevel1()
            1 -> LevelLoader.loadLevel2()
            else -> null
        }
        levelIndex = index
    }

    fun nextLevel() {
        loadLevel(levelIndex + 1)
    }

    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {
        if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
            currentLevel?.let { lvl ->
                if (chooseObj in lvl.objects.indices) {
                    val obj = lvl.objects[chooseObj]
                    val moveMode = objMoveModes[obj] ?: true
                    objMoveModes[obj] = !moveMode
                    println("Objekt ${chooseObj + 1}: ${if (objMoveModes[obj] == true) "Bewegen" else "Rotieren"}")
                }
            }
        }

        // Objektwahl
        if (key == GLFW_KEY_1 && action == GLFW_PRESS) { chooseObj = 0; println("Objekt 1 ausgewählt") }
        if (key == GLFW_KEY_2 && action == GLFW_PRESS) { chooseObj = 1; println("Objekt 2 ausgewählt") }
        if (key == GLFW_KEY_3 && action == GLFW_PRESS) { chooseObj = 2; println("Objekt 3 ausgewählt") }

        if (key == GLFW_KEY_4 && action == GLFW_PRESS) {loadLevel(1)}
        if (key == GLFW_KEY_5 && action == GLFW_PRESS) {loadLevel(0)}


        // Kamerawechsel
        if (key == GLFW_KEY_C && action == GLFW_PRESS) {
            activeCam = 1 - activeCam
            println("Aktive Kamera: ${if (activeCam == 0) "Cam 1 (Anchor)" else "Cam 2"}")
        }

        // Zielwechsel
        if (key == GLFW_KEY_T && action == GLFW_PRESS) cycleCameraTarget(true)
        if (key == GLFW_KEY_R && action == GLFW_PRESS) cycleCameraTarget(false)

        // Sofort-Blackout
        if (key == GLFW_KEY_B && action == GLFW_PRESS) {
            forceBlackout = true
            forceBlackoutTimer = 0f
            println("SOFORT-SCHWARZ aktiviert (Taste B).")
        }

        // Anchor-Kamera (cam1) Perspektive <-> Orthografisch
        if (key == GLFW_KEY_P && action == GLFW_PRESS) {
            cam1.toggleProjection()
            println("Anchor-Kamera Projektion: ${cam1.projectionMode}")
        }
    }

    fun onMouseMove(xpos: Double, ypos: Double) {
        if (firstMouseMove) { lastMouseX = xpos; firstMouseMove = false; return }
        val dx = xpos - lastMouseX
        lastMouseX = xpos
        val sensitivity = 0.002f
        val dy = (-dx * sensitivity).toFloat()
        if (activeCam == 0) {
            rig1.rotate(0f, dy, 0f)
            yaw1 += dy
        }
    }

    fun cleanup() {
        fadeOverlay.cleanup()
    }
}

/** Hilfsobjekt: holt ein Material von einem Renderable (erster Mesh). */
private object MaterialProvider {
    fun grabFrom(r: Renderable): Material? {
        val f = Renderable::class.java.getDeclaredField("meshes")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val meshes = f.get(r) as MutableList<Mesh>
        return meshes.firstOrNull()?.material
    }
}
