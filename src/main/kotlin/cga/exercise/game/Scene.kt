package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.*
import cga.exercise.components.light.PointLight
import cga.exercise.components.light.SpotLight
import cga.exercise.components.shader.ShaderProgram
import cga.exercise.components.shadow.ShadowRenderer
import cga.exercise.components.blackscreen.FadeOverlay
import cga.exercise.components.level.Level
import cga.exercise.components.level.LevelLoader
import cga.exercise.components.texture.Texture2D
import cga.framework.GLError
import cga.framework.GameWindow
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

    // --- Shadow Mapping ---
    private val shadow1 = ShadowRenderer(1024, 1024)
    private val shadow2 = ShadowRenderer(1024, 1024)
    private val shadowUnit1 = 7
    private val shadowUnit2 = 8 // aktuell nicht im Shader genutzt

    // --- Level ---
    private var currentLevel: Level? = null
    private var levelIndex = 0

    // --- Renderables ---
    private var leinwandRenderable: Renderable? = null
    private var motorrad: Renderable? = null
    private var followAnchor: Renderable? = null

    // --- Auswahl / Objektsteuerung ---
    private val objMoveModes = mutableMapOf<Renderable, Boolean>()
    private var chooseObj: Int = 0

    // --- Lichter ---
    private val pointLight = PointLight(Vector3f(0f, 1f, 0f), Vector3f(1f, 1f, 1f))
    private var testSpot: SpotLight? = null
    private var bikeSpot: SpotLight? = null

    private val pointLights = listOf(
        pointLight,
        PointLight(Vector3f(-9f, 1.5f, -9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f( 9f, 1.5f, -9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f( 9f, 1.5f,  9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f(-9f, 1.5f,  9f), Vector3f(1f, 1f, 1f))
    )

    // --- Kamera-Controller (separate Klasse) ---
    private val camera = CameraController()

    // --- Raumgrenzen (nur für Y-Klemme der Kamera, XYZ für Objekte) ---
    private val roomMin = Vector3f(-8.8f, 0.0f, -8.8f)
    private val roomMax = Vector3f( 8.8f, 5.0f,  8.8f)
    private val wallMargin = 0.30f

    private companion object { private const val EPS_POS2 = 1e-10f }

    // --- Fade/Auto-Exit ---
    private val fadeOverlay = FadeOverlay()
    private val totalTimeToBlack = 300.0f      // 5 Minuten
    private val finalFadeDuration = 120.0f     // letzte 2 Minuten
    private var nowT = 0f
    private var quitIssued = false
    private var forceBlackout = false
    private var forceBlackoutTimer = 0f
    private val forceBlackoutHold = 1.0f

    init {
        // Level laden
        loadLevel(0)

        // Beispielmaterial (Leinwand)
        val diffuseObj = Texture2D("assets/textures/Porcelain001_2K-JPG_Color.jpg", true)
        val roughObj = Texture2D("assets/textures/Porcelain001_2K-JPG_Roughness.jpg", true)
        val normalObj = Texture2D("assets/textures/Porcelain001_2K-JPG_NormalGL.jpg", true)
        val emissiveBlack = Texture2D("assets/textures/schwarz.png", true)

        val bauerMaterial = Material(
            diff = diffuseObj,
            emit = emissiveBlack,
            specular = emissiveBlack,
            roughness = roughObj,
            normal = normalObj,
            shininess = 60.0f,
            tcMultiplier = Vector2f(2.0f, 2.0f)
        )

        // Leinwand
        val leinwandObj = loadOBJ("assets/models/roomGround.obj")
        val leinwandMeshList = leinwandObj.objects[0].meshes
        val leinwandAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val leinwandMesh = Mesh(leinwandMeshList[0].vertexData, leinwandMeshList[0].indexData, leinwandAttribs, bauerMaterial)
        leinwandRenderable = Renderable(mutableListOf(leinwandMesh)).apply {
            translate(Vector3f(0f, 5f, -8f))
            scale(Vector3f(0.2f, 0.2f, 0.2f))
            rotate(Math.toRadians(90.0).toFloat(), 0f, 0f)
        }

        // Unsichtbarer Follow-Anchor (ersetzt Motorrad)
        val anchorObj = loadOBJ("assets/models/cube.obj")
        val anchorMeshList = anchorObj.objects[0].meshes
        val anchorAttribs = arrayOf(
            VertexAttribute(3, GL_FLOAT, 32, 0),
            VertexAttribute(2, GL_FLOAT, 32, 12),
            VertexAttribute(3, GL_FLOAT, 32, 20)
        )
        val anchorMesh = Mesh(anchorMeshList[0].vertexData, anchorMeshList[0].indexData, anchorAttribs)
        followAnchor = Renderable(mutableListOf(anchorMesh)).apply {
            translate(Vector3f(0f, 1.0f, 0f))
            scale(Vector3f(0.8f))
        }

        // Lichter am Anchor
        pointLight.parent = followAnchor
        pointLight.translate(Vector3f(0f, 1.5f, 0f))

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

        // Kamera-Controller mit Anchor und Zielen versorgen
        camera.setAnchor(followAnchor)
        rebuildCameraTargets()

        // OpenGL State
        glClearColor(0f, 0f, 0f, 1f); GLError.checkThrow()
        glEnable(GL_DEPTH_TEST)
        glEnable(GL_CULL_FACE)
        glCullFace(GL_BACK)
        glFrontFace(GL_CCW)
    }

    // --- Render ---
    fun render(dt: Float, t: Float) {
        val level = currentLevel ?: return

        val vp = IntArray(4)
        glGetIntegerv(GL_VIEWPORT, vp)

        // Light-space 1: Test-Spot (zielt z.B. auf 2. Objekt)
        val ls1: Matrix4f? = testSpot?.let { sp ->
            val pos = sp.getWorldPosition()
            val target = level.objects.getOrNull(1)?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
            shadow1.buildLightSpacePerspective(pos, target, fovRad = Math.toRadians(60.0).toFloat(), near = 0.1f, far = 100f)
        }

        // Light-space 2: Anchor-Spot -> nach vorn vom Anchor
        val ls2: Matrix4f? = bikeSpot?.let { sp ->
            val pos = sp.getWorldPosition()
            val targ = followAnchor?.getWorldPosition()?.add(0f, 0f, -2f) ?: Vector3f(0f, 0f, -2f)
            shadow2.buildLightSpacePerspective(pos, targ, fovRad = Math.toRadians(60.0).toFloat(), near = 0.1f, far = 100f)
        }

        // Depth Pass 1
        if (ls1 != null) {
            shadow1.beginDepthPass(ls1)
            val ds = shadow1.depthShader()
            level.ground.renderDepth(ds)
            level.objects.forEach { it.renderDepth(ds) }
            level.room.renderDepth(ds)
            motorrad?.renderDepth(ds)
            shadow1.endDepthPass()
        }

        // Depth Pass 2
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

        // Scene Pass
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        staticShader.use()

        val cam = camera.activeCamera
        cam.bind(staticShader)
        val view = cam.getCalculateViewMatrix()

        // Punktlichter (Viewspace)
        staticShader.setUniform("numPointLights", pointLights.size)
        for ((index, light) in pointLights.withIndex()) {
            val viewPos = view.transformPosition(light.getWorldPosition(), Vector3f())
            staticShader.setUniform("pointLight_positions[$index]", viewPos)
            staticShader.setUniform("pointLight_colors[$index]", light.color)
        }

        // Test-Spot mit Shadowmap
        testSpot?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)
            val conePos = level.objects.getOrNull(1)?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
            val dirWorld = Vector3f(conePos).sub(sp.getWorldPosition()).normalize()
            val dirView  = view.transformDirection(dirWorld, Vector3f()).normalize()
            staticShader.setUniform("spot_direction_view", dirView)
            ls1?.let { shadow1.bindForScenePass(staticShader, it, unit = shadowUnit1) }
        }

        // Anchor-Spot ohne Shadowmap
        bikeSpot?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)
        }

        // Zeitabhängiger Emission-Tint
        val r = (sin(t * 2.0) * 0.5 + 0.5).toFloat()
        val g = (sin(t * 0.7 + 2.0) * 0.5 + 0.5).toFloat()
        val b = (sin(t * 1.3 + 4.0) * 0.5 + 0.5).toFloat()
        staticShader.setUniform("emission_tint", Vector3f(r, g, b))

        // Render Level & Extras
        level.ground.render(staticShader)
        level.room.render(staticShader)
        level.objects.forEach { it.render(staticShader) }
        motorrad?.render(staticShader)
        leinwandRenderable?.render(staticShader)

        // Fade-Overlay
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

        // Kamera-Controller (Yaw/Pitch/Zoom/Höhe/Follow)
        camera.update(dt, window)

        // Anchor mit WASD relativ zur aktiven Kamera bewegen (strafe)
        moveAnchorWithWASD(dt)

        // Kamera **nur vertikal** zwischen Boden/Decke klemmen
        clampCameraHeightsOnly()

        // Objektsteuerung + Kollisionsvermeidung
        currentLevel?.let { lvl ->
            if (lvl.objects.isNotEmpty() && chooseObj in lvl.objects.indices) {
                val obj = lvl.objects[chooseObj]
                val others = lvl.objects.filter { it != obj }
                val moveMode = objMoveModes[obj] ?: true
                objectControl(dt, moveMode, obj, others)
            }
        }
    }

    // --- Input Hooks ---
    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {
        // Toggle Move/Rotate für selektiertes Objekt
        if (key == GLFW_KEY_TAB && action == GLFW_PRESS) {
            currentLevel?.let { lvl ->
                if (chooseObj in lvl.objects.indices) {
                    val obj = lvl.objects[chooseObj]
                    objMoveModes[obj] = !(objMoveModes[obj] ?: true)
                    println("Objekt ${chooseObj + 1}: ${if (objMoveModes[obj] == true) "Bewegen" else "Rotieren"}")
                }
            }
        }

        // Objektwahl
        if (key == GLFW_KEY_1 && action == GLFW_PRESS) { chooseObj = 0; println("Objekt 1 ausgewählt") }
        if (key == GLFW_KEY_2 && action == GLFW_PRESS) { chooseObj = 1; println("Objekt 2 ausgewählt") }
        if (key == GLFW_KEY_3 && action == GLFW_PRESS) { chooseObj = 2; println("Objekt 3 ausgewählt") }

        // Levelwechsel (Beispiel)
        if (key == GLFW_KEY_4 && action == GLFW_PRESS) { loadLevel(1); rebuildCameraTargets() }
        if (key == GLFW_KEY_5 && action == GLFW_PRESS) { loadLevel(0); rebuildCameraTargets() }

        // Kamera/Targets -> an CameraController delegieren
        if (key == GLFW_KEY_C && action == GLFW_PRESS) camera.toggleActiveCamera()
        if (key == GLFW_KEY_T && action == GLFW_PRESS) camera.cycleTarget(true)
        if (key == GLFW_KEY_R && action == GLFW_PRESS) camera.cycleTarget(false)
        if (key == GLFW_KEY_P && action == GLFW_PRESS) camera.toggleProjectionCam1()

        // Sofort-Blackout
        if (key == GLFW_KEY_B && action == GLFW_PRESS) {
            forceBlackout = true
            forceBlackoutTimer = 0f
            println("SOFORT-SCHWARZ aktiviert (Taste B).")
        }
    }

    fun onMouseMove(xpos: Double, ypos: Double) {
        camera.onMouseMove(xpos, ypos)
    }

    fun cleanup() {
        fadeOverlay.cleanup()
    }

    // ------------------------- Helpers -------------------------

    /** Nur Y (Boden/Decke) für beide Kameras klemmen. */
    private fun clampCameraHeightsOnly() {
        clampTransformableY(camera.cam1, roomMin.y, roomMax.y, wallMargin)
        clampTransformableY(camera.cam2, roomMin.y, roomMax.y, wallMargin)
    }

    /** Anchor per WASD relativ zur aktiven Kamera bewegen (XZ, keine Rotation). */
    private fun moveAnchorWithWASD(dt: Float) {
        val anchor = followAnchor ?: return
        val cam = camera.activeCamera

        // Eingabeachsen
        var axF = 0f
        if (window.getKeyState(GLFW_KEY_W)) axF += 1f
        if (window.getKeyState(GLFW_KEY_S)) axF -= 1f
        var axR = 0f
        if (window.getKeyState(GLFW_KEY_D)) axR += 1f
        if (window.getKeyState(GLFW_KEY_A)) axR -= 1f
        if (axF == 0f && axR == 0f) return

        // Weltbasis aus Kamera (auf XZ)
        val inv = Matrix4f(cam.getCalculateViewMatrix()).invert()
        val forward = inv.transformDirection(Vector3f(0f, 0f, -1f)).apply { y = 0f; if (lengthSquared() > 1e-8f) normalize() }
        val right   = inv.transformDirection(Vector3f(1f, 0f,  0f)).apply { y = 0f; if (lengthSquared() > 1e-8f) normalize() }

        val moveSpeed = 8.0f
        val dir = Vector3f().add(forward.mul(axF, Vector3f())).add(right.mul(axR, Vector3f()))
        if (dir.lengthSquared() > 0f) {
            dir.normalize().mul(moveSpeed * dt)
            anchor.preTranslate(dir)
        }
    }

    /** XYZ-Klemme für beliebige Transformables (Objekte) im Raum. */
    private fun clampTransformableToRoom(t: Transformable, margin: Float = wallMargin) {
        val wp = t.getWorldPosition()
        val clamped = clampInsideRoom(Vector3f(wp), margin)
        if (!wp.equals(clamped, 1e-5f)) {
            val corr = clamped.sub(wp)
            t.preTranslate(corr)
        }
    }

    private fun clampInsideRoom(p: Vector3f, margin: Float = wallMargin): Vector3f {
        return Vector3f(
            p.x.coerceIn(roomMin.x + margin, roomMax.x - margin),
            p.y.coerceIn(roomMin.y + margin, roomMax.y - margin),
            p.z.coerceIn(roomMin.z + margin, roomMax.z - margin)
        )
    }

    /** Nur Y-Klemme (Boden/Decke) mit Margin. */
    private fun clampTransformableY(t: Transformable, minY: Float, maxY: Float, margin: Float) {
        val wp = t.getWorldPosition()
        val targetY = wp.y.coerceIn(minY + margin, maxY - margin)
        val dy = targetY - wp.y
        if (abs(dy) > 1e-5f) t.preTranslate(Vector3f(0f, dy, 0f))
    }

    // Objektsteuerung
    fun objectControl(dt: Float, moveMode: Boolean, renderable: Renderable?, allObjects: List<Renderable>) {
        val speed = Math.toRadians(90.0).toFloat()
        if (renderable == null) return

        if (moveMode) {
            if (window.getKeyState(GLFW_KEY_UP))    tryMove(renderable, Vector3f(0f, 0f, -speed * dt), allObjects)
            if (window.getKeyState(GLFW_KEY_DOWN))  tryMove(renderable, Vector3f(0f, 0f,  speed * dt), allObjects)
            if (window.getKeyState(GLFW_KEY_LEFT))  tryMove(renderable, Vector3f(-speed * dt, 0f, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_RIGHT)) tryMove(renderable, Vector3f( speed * dt, 0f, 0f), allObjects)
        } else {
            if (window.getKeyState(GLFW_KEY_UP))    tryRotate(renderable, Vector3f(-speed * dt, 0f, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_DOWN))  tryRotate(renderable, Vector3f( speed * dt, 0f, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_LEFT))  tryRotate(renderable, Vector3f(0f, -speed * dt, 0f), allObjects)
            if (window.getKeyState(GLFW_KEY_RIGHT)) tryRotate(renderable, Vector3f(0f,  speed * dt, 0f), allObjects)
        }
    }

    private fun tryMove(obj: Renderable, move: Vector3f, others: List<Renderable>) {
        val prev = obj.getWorldPosition()
        obj.translate(move)
        clampTransformableToRoom(obj) // im Raum halten (XYZ für Objekte)

        val currentBox = obj.getKollision()
        val collision = others.any { it != obj && currentBox.intersects(it.getKollision()) }
        if (collision) {
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
            obj.rotate(-rot.x, -rot.y, -rot.z)
            val now = obj.getWorldPosition()
            obj.preTranslate(prevPos.sub(now))
        }
    }

    // Camera targets nach Level-/Anchor-Änderung neu setzen
    private fun rebuildCameraTargets() {
        val targets = mutableListOf<Transformable>()
        currentLevel?.objects?.forEach { targets += it }
        motorrad?.let { targets += it }
        followAnchor?.let { targets += it } // Anchor als zusätzliches Kamera-Ziel
        camera.setTargets(targets, initialIndex = 0, snap = true)
    }

    // Level laden/wechseln
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
        rebuildCameraTargets()
    }

    // Fade/Exit
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
