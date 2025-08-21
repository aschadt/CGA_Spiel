package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.*
import cga.exercise.components.light.PointLight
import cga.exercise.components.light.SpotLight
import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.texture.Texture2D
import cga.exercise.components.shadow.ShadowRenderer
import cga.exercise.components.blackscreen.FadeOverlay
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
import kotlin.system.exitProcess

class Scene(private val window: GameWindow) {
    // --- Shader ---
    private val staticShader = ShaderProgram("assets/shaders/tron_vert.glsl", "assets/shaders/tron_frag.glsl")

    // --- Shadow Mapping (zwei Shadow-Maps: Test-Spot + Anchor-Spot) ---
    private val shadow1 = ShadowRenderer(1024, 1024)
    private val shadow2 = ShadowRenderer(1024, 1024)
    private val shadowUnit1 = 7
    private val shadowUnit2 = 8 // aktuell nicht im Shader genutzt, nur Depth-Pass zum Testen

    // --- Renderables ---
    private var groundRenderable: Renderable
    private var roomRenderable: Renderable
    private var obj1Renderable: Renderable? = null
    private var obj2Renderable: Renderable? = null
    private var obj3Renderable: Renderable? = null

    // Unsichtbarer Follow-Anchor (ersetzt Motorrad)
    private var followAnchor: Renderable? = null

    // --- Auswahl / Objektsteuerung ---
    private var obj1MoveMode = true
    private var obj2MoveMode = true
    private var obj3MoveMode = true
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
    private var dist2 = 6.0f

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
    private val cam2YOffsetDefault = 0.0f

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
        // Texturen laden
        val diffuse = Texture2D("assets/textures/ground_diff.png", true)
        val specular = Texture2D("assets/textures/ground_spec.png", true)
        val emissive = Texture2D("assets/textures/ground_emit.png", true)

        val diffuseWall = Texture2D("assets/textures/red_brick_diff_2k.jpg", true)
        val roughWall = Texture2D("assets/textures/red_brick_rough_2k.jpg", true)
        val normalWall = Texture2D("assets/textures/red_brick_nor_gl_2k.jpg", true)

        val diffuseGround = Texture2D("assets/textures/gray_rocks_diff_2k.jpg", true)
        val roughGround = Texture2D("assets/textures/gray_rocks_rough_2k.jpg", true)
        val normalGround = Texture2D("assets/textures/gray_rocks_nor_gl_2k.jpg", true)

        val diffuseBauer = Texture2D("assets/textures/plywood_diff_2k.jpg", true)
        val roughBauer = Texture2D("assets/textures/plywood_rough_2k.jpg", true)
        val normalBauer = Texture2D("assets/textures/plywood_nor_gl_2k.jpg", true)

        val emissiveBlack = Texture2D("assets/textures/schwarz.png", true)

        // Texturparameter
        val wrap = GL_REPEAT
        val filter = GL_LINEAR_MIPMAP_LINEAR
        val mipmap = GL_LINEAR
        diffuse.setTexParams(wrap, wrap, filter, mipmap)
        specular.setTexParams(wrap, wrap, filter, mipmap)
        emissive.setTexParams(wrap, wrap, filter, mipmap)

        // Material
        val oldGroundMaterial = Material(
            diff = diffuse,
            emit = emissive,
            specular = specular,
            shininess = 60.0f,
            tcMultiplier = Vector2f(64.0f, 64.0f)
        )
        val wallMaterial = Material(
            diff = diffuseWall,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughWall,
            normal = normalWall,
            shininess = 60.0f,
            tcMultiplier = Vector2f(8.0f, 8.0f)
        )
        val groundMaterial = Material(
            diff = diffuseGround,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughGround,
            normal = normalGround,
            shininess = 60.0f,
            tcMultiplier = Vector2f(16.0f, 16.0f)
        )
        val bauerMaterial = Material(
            diff = diffuseBauer,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughBauer,
            normal = normalBauer,
            shininess = 60.0f,
            tcMultiplier = Vector2f(2.0f, 2.0f)
        )

        // Ground
        val groundObj = loadOBJ("assets/models/roomGround.obj")
        val groundMeshList = groundObj.objects[0].meshes
        val groundAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val groundMesh = Mesh(groundMeshList[0].vertexData, groundMeshList[0].indexData, groundAttribs, groundMaterial)
        groundRenderable = Renderable(mutableListOf(groundMesh))

        // Room
        val roomObj = loadOBJ("assets/models/roomWall_v2.obj")
        val roomMeshList = roomObj.objects[0].meshes
        val roomAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val roomMesh = Mesh(roomMeshList[0].vertexData, roomMeshList[0].indexData, roomAttribs, wallMaterial)
        roomRenderable = Renderable(mutableListOf(roomMesh))
        roomRenderable.rotate(0f, Math.toRadians(-90.0).toFloat(), 0f)
        roomRenderable.translate(Vector3f(0.0f, 0.0f, 0.0f))

        // Obj 1 (Cube)
        val obj1 = loadOBJ("assets/models/Bauer_T1.obj")
        val obj1MeshList = obj1.objects[0].meshes
        val obj1Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj1Mesh = Mesh(obj1MeshList[0].vertexData, obj1MeshList[0].indexData, obj1Attribs, bauerMaterial)
        obj1Renderable = Renderable(mutableListOf(obj1Mesh)).apply {
            translate(Vector3f(2.0f, 2.0f, -2.0f))
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // Obj 2 (Cone)
        val obj2 = loadOBJ("assets/models/Bauer_T2.obj")
        val obj2MeshList = obj2.objects[0].meshes
        val obj2Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj2Mesh = Mesh(obj2MeshList[0].vertexData, obj2MeshList[0].indexData, obj2Attribs, bauerMaterial)
        obj2Renderable = Renderable(mutableListOf(obj2Mesh)).apply {
            translate(Vector3f(0.0f, 2.0f, -2.0f))
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

        // Obj 3 (Zylinder)
        val obj3 = loadOBJ("assets/models/Bauer_T3.obj")
        val obj3MeshList = obj3.objects[0].meshes
        val obj3Attribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val obj3Mesh = Mesh(obj3MeshList[0].vertexData, obj3MeshList[0].indexData, obj3Attribs, bauerMaterial)
        obj3Renderable = Renderable(mutableListOf(obj3Mesh)).apply {
            translate(Vector3f(-2.0f, 2.0f, -2.0f))
            scale(Vector3f(0.5f, 0.5f, 0.5f))
        }

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
        // Anchor wird NICHT gerendert (wir rufen später keinen renderDepth/render dafür auf)

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
        obj1Renderable?.let { camTargets += it }
        obj2Renderable?.let { camTargets += it }
        obj3Renderable?.let { camTargets += it }
        followAnchor?.let   { camTargets += it }
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
        val vp = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, vp)

        // Light-space 1: Test-Spot -> zielt auf Cone
        val ls1: Matrix4f? = testSpot?.let { sp ->
            val pos = sp.getWorldPosition()
            val target = obj2Renderable?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
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
            groundRenderable.renderDepth(ds)
            obj1Renderable?.renderDepth(ds)
            obj2Renderable?.renderDepth(ds)
            roomRenderable.renderDepth(ds)
            // Anchor NICHT rendern (unsichtbar)
            shadow1.endDepthPass()
        }

        // Depth-Pass 2
        if (ls2 != null) {
            shadow2.beginDepthPass(ls2)
            val ds2 = shadow2.depthShader()
            groundRenderable.renderDepth(ds2)
            obj1Renderable?.renderDepth(ds2)
            obj2Renderable?.renderDepth(ds2)
            roomRenderable.renderDepth(ds2)
            // Anchor NICHT rendern
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
            val conePos = obj2Renderable?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
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

        obj1Renderable?.render(staticShader)
        obj2Renderable?.render(staticShader)
        obj3Renderable?.render(staticShader)
        roomRenderable.render(staticShader)
        groundRenderable.render(staticShader)
        // Anchor NICHT rendern (unsichtbar)

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

        val moveSpeed = 8.0f
        val rotateSpeed = Math.toRadians(90.0).toFloat()

        // Rigs folgen nur der Zielposition (keine Rotation erben)
        followTarget?.let { tgt ->
            moveRigToTargetPosition(rig1, tgt)
            moveRigToTargetPosition(rig2, tgt)
        }
        
        // Anchor in Kamerarichtung mit WASD bewegen (strafe, keine Rotation des Anchors)
        followAnchor?.let { anchor ->
            val cam = getActiveCamera()
            val view = cam.getCalculateViewMatrix()
            val inv  = Matrix4f(view).invert()

            // Welt-Forward/-Right aus Kamerapose ableiten, auf XZ projizieren
            val forward = inv.transformDirection(Vector3f(0f, 0f, -1f)).apply {
                y = 0f; if (lengthSquared() > 1e-8f) normalize()
            }
            val right = inv.transformDirection(Vector3f(1f, 0f, 0f)).apply {
                y = 0f; if (lengthSquared() > 1e-8f) normalize()
            }

            // Eingaben sammeln
            var axF = 0f
            if (window.getKeyState(GLFW_KEY_W)) axF += 1f
            if (window.getKeyState(GLFW_KEY_S)) axF -= 1f

            var axR = 0f
            if (window.getKeyState(GLFW_KEY_D)) axR += 1f
            if (window.getKeyState(GLFW_KEY_A)) axR -= 1f

            // Bewegungsvektor bilden und anwenden (in Welt-/Parent-Raum)
            val dir = Vector3f()
                .add(forward.mul(axF, Vector3f()))
                .add(right.mul(axR, Vector3f()))

            if (dir.lengthSquared() > 0f) {
                dir.normalize().mul(moveSpeed * dt)
                anchor.preTranslate(dir) // preTranslate = Welt-/Elternraum
            }
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

        // Zoom
        if (activeCam == 0) {
            val zoomIn = window.getKeyState(GLFW_KEY_U)
            val zoomOut = window.getKeyState(GLFW_KEY_O)
            if (cam1.projectionMode == TronCamera.ProjectionMode.Perspective) {
                var fovDelta = 0f
                if (zoomIn)  fovDelta += -fovZoomSpeedRad * dt
                if (zoomOut) fovDelta +=  +fovZoomSpeedRad * dt
                if (fovDelta != 0f) cam1.fovRad = (cam1.fovRad + fovDelta).coerceIn(fovMinRad, fovMaxRad)
            } else {
                var orthoDelta = 0f
                if (zoomIn)  orthoDelta += -anchorOrthoZoomSpeed * dt // kleiner = näher
                if (zoomOut) orthoDelta +=  +anchorOrthoZoomSpeed * dt
                if (orthoDelta != 0f) cam1.addOrthoHeight(orthoDelta)
            }
        } else {
            var fovDelta = 0f
            if (window.getKeyState(GLFW_KEY_U)) fovDelta += -fovZoomSpeedRad * dt
            if (window.getKeyState(GLFW_KEY_O)) fovDelta +=  +fovZoomSpeedRad * dt
            if (fovDelta != 0f) cam2.fovRad = (cam2.fovRad + fovDelta).coerceIn(fovMinRad, fovMaxRad)
        }

        // Kamerahöhe verstellen (nur wenn Ziel = Anchor) — N runter / M hoch
        if (currentCamTarget != null && followAnchor != null && currentCamTarget === followAnchor) {
            var dh = 0f
            if (window.getKeyState(GLFW_KEY_M)) dh += camHeightAdjustSpeed * dt
            if (window.getKeyState(GLFW_KEY_N)) dh -= camHeightAdjustSpeed * dt

            if (dh != 0f) {
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
        }

        // Objektsteuerung + einfache Kollisionsvermeidung
        if (chooseObj == 1 && obj1Renderable != null) {
            objectControl(dt, obj1MoveMode, obj1Renderable, listOfNotNull(obj2Renderable, obj3Renderable))
        } else if (chooseObj == 2 && obj2Renderable != null) {
            objectControl(dt, obj2MoveMode, obj2Renderable, listOfNotNull(obj1Renderable, obj3Renderable))
        } else if (chooseObj == 3 && obj3Renderable != null) {
            objectControl(dt, obj3MoveMode, obj3Renderable, listOfNotNull(obj1Renderable, obj2Renderable))
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

    private fun tryMove(obj: Renderable, move: Vector3f, others: List<Renderable>) {
        obj.translate(move)
        val currentBox = obj.getKollision()
        val collision = others.any { it != obj && currentBox.intersects(it.getKollision()) }
        if (collision) obj.translate(move.negate(Vector3f()))
    }

    private fun tryRotate(obj: Renderable, rot: Vector3f, others: List<Renderable>) {
        obj.rotate(rot.x, rot.y, rot.z)
        val currentBox = obj.getKollision()
        val collision = others.any { it != obj && currentBox.intersects(it.getKollision()) }
        if (collision) obj.rotate(-rot.x, -rot.y, -rot.z)
    }

    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {
        if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
            when (chooseObj) {
                1 -> { obj1MoveMode = !obj1MoveMode; println("Cube: ${if (obj1MoveMode) "Bewegen" else "Rotieren"}") }
                2 -> { obj2MoveMode = !obj2MoveMode; println("Cone: ${if (obj2MoveMode) "Bewegen" else "Rotieren"}") }
                3 -> { obj3MoveMode = !obj3MoveMode; println("Zylinder: ${if (obj3MoveMode) "Bewegen" else "Rotieren"}") }
            }
        }

        // Objektwahl
        if (key == GLFW_KEY_1 && action == GLFW_PRESS) { chooseObj = 1; println("Objekt 1 ausgewählt (Cube)") }
        if (key == GLFW_KEY_2 && action == GLFW_PRESS) { chooseObj = 2; println("Objekt 2 ausgewählt (Cone)") }
        if (key == GLFW_KEY_3 && action == GLFW_PRESS) { chooseObj = 3; println("Objekt 3 ausgewählt (Zylinder)") }

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
