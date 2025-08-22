package cga.exercise.game

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
    private val shadow = ShadowRenderer(1024, 1024)
    private val shadowUnit = 7


    // --- Level ---
    private var currentLevel: Level? = null
    private var levelIndex = 0

    // --- Renderables ---
    private var leinwandRenderable: Renderable? = null
    private var motorrad: Renderable? = null
    private var followAnchor: Renderable? = null   // Fallback-Anchor (Würfel)

    // --- Auswahl-Stati ---
    private var focusIndex: Int = 0
    private var controlIndex: Int = 0
    private val objMoveModes = mutableMapOf<Renderable, Boolean>()

    // --- Lichter ---
    //private val pointLight = PointLight(Vector3f(0f, 1f, 0f), Vector3f(1f, 1f, 1f))
    private var bikeSpot: SpotLight? = null

    private val pointLights = listOf(
        PointLight(Vector3f(-9f, 1.5f, -9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f( 9f, 1.5f, -9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f( 9f, 1.5f,  9f), Vector3f(1f, 1f, 1f)),
        PointLight(Vector3f(-9f, 1.5f,  9f), Vector3f(1f, 1f, 1f))
    )

    // --- Kamera-Controller ---
    private val camera = CameraController()

    // --- Raumgrenzen ---
    private val roomMin = Vector3f(-9.9f, 0.0f, -9.9f)
    private val roomMax = Vector3f( 9.9f, 7.0f,  9.9f)
    private val wallMargin = 0.30f

    private companion object { private const val EPS_POS2 = 1e-10f }

    // --- Fade/Auto-Exit ---
    private val fadeOverlay = FadeOverlay()
    private val totalTimeToBlack = 300.0f
    private val finalFadeDuration = 120.0f
    private var nowT = 0f
    private var quitIssued = false
    private var forceBlackout = false
    private var forceBlackoutTimer = 0f
    private val forceBlackoutHold = 1.0f

    /** Liefert das Anchor-Objekt für die freie Kamera (bevorzugt Motorrad). */
    private fun anchorTarget(): Renderable? = motorrad ?: followAnchor

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

        // Fallback-Anchor (unsichtbarer Würfel), falls kein Motorrad existiert
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

        // <<< WICHTIG >>> freie Kamera an Motorrad parenten (Fallback: Würfel)
        camera.setAnchor(anchorTarget())

        // Lichter am Anchor (Motorrad bevorzugt)
        //pointLight.parent = anchorTarget()
        //pointLight.translate(Vector3f(0f, 1.5f, 0f))


        bikeSpot = SpotLight(
            position = Vector3f(0.0f, 5.0f, 3.0f),
            color = Vector3f(1f, 1f, 1f),
            innerAngle = Math.toRadians(18.0).toFloat(),
            outerAngle = Math.toRadians(26.0).toFloat()
        )

        // Kamera-Targets (Level-Objekte) einmalig setzen
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

        // Light-space 1
        val ls_BikeSpot: Matrix4f? = bikeSpot?.let { sp ->
            val pos = sp.getWorldPosition()
            val target = level.objects.getOrNull(1)?.getWorldPosition() ?: Vector3f(0f, 2f, -2f)
            shadow.buildLightSpacePerspective(pos, target, fovRad = Math.toRadians(90.0).toFloat(), near = 1.0f, far = 10000f)
        }

        // Depth Pass 1
        if (ls_BikeSpot != null) {
            shadow.beginDepthPass(ls_BikeSpot)
            val ds = shadow.depthShader()
            level.ground.renderDepth(ds)
            level.objects.forEach { it.renderDepth(ds) }
            level.room.renderDepth(ds)
            motorrad?.renderDepth(ds)
            shadow.endDepthPass()
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
        // Bike-Spot mit Shadowmap
        bikeSpot?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)

            // Richtung: zielt auf einen Punkt vor dem Bike/Anchor
            val targetWS = anchorTarget()?.getWorldPosition()?.add(0f, 0f, -2f) ?: Vector3f(0f, 0f, -2f)
            val dirWorld = Vector3f(targetWS).sub(sp.getWorldPosition()).normalize()
            val dirView  = view.transformDirection(dirWorld, Vector3f()).normalize()
            staticShader.setUniform("spot_direction_view", dirView)

            // Shadow-Map binden
            ls_BikeSpot?.let { shadow.bindForScenePass(staticShader, it, unit = shadowUnit) }
        }

        // Anchor-Spot ohne Shadowmap
        bikeSpot?.let { sp ->
            staticShader.setUniform("spotLight_color", sp.color)
            sp.bind(staticShader, view)
        }

        // Emission-Tint
        val r = (sin(t * 2.0) * 0.5 + 0.5).toFloat()
        val g = (sin(t * 0.7 + 2.0) * 0.5 + 0.5).toFloat()
        val b = (sin(t * 1.3 + 4.0) * 0.5 + 0.5).toFloat()
        staticShader.setUniform("emission_tint", Vector3f(r, g, b))

        // Render
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
        nowT = t
        if (forceBlackout) {
            forceBlackoutTimer += dt
            if (forceBlackoutTimer >= forceBlackoutHold) { requestClose(); return }
        }
        if (nowT >= totalTimeToBlack) { requestClose(); return }

        camera.update(dt, window)

        // Anchor per WASD (bewegt Motorrad, falls vorhanden)
        moveAnchorWithWASD(dt)

        // Kamera nur vertikal klemmen
        clampCameraHeightsOnly()

        // Objektsteuerung (unabhängig vom Fokus)
        currentLevel?.let { lvl ->
            if (lvl.objects.isNotEmpty() && controlIndex in lvl.objects.indices) {
                val obj = lvl.objects[controlIndex]
                val others = lvl.objects.filter { it != obj }
                val moveMode = objMoveModes[obj] ?: true
                objectControl(dt, moveMode, obj, others)
            }
        }
    }

    // --- Input Hooks ---
    fun onKey(key: Int, scancode: Int, action: Int, mode: Int) {
        if (action != GLFW_PRESS) return

        // Kamera-FOKUS + STEUERUNG (1/2/3)
        if (key == GLFW_KEY_1) { focusSelection(0, snap = true); setControl(0) }
        if (key == GLFW_KEY_2) { focusSelection(1, snap = true); setControl(1) }
        if (key == GLFW_KEY_3) { focusSelection(2, snap = true); setControl(2) }

        // Nur KAMERA-FOKUS (F1–F4)
        if (key == GLFW_KEY_F1) { focusSelection(0, snap = true) }
        if (key == GLFW_KEY_F2) { focusSelection(1, snap = true) }
        if (key == GLFW_KEY_F3) { focusSelection(2, snap = true) }
        if (key == GLFW_KEY_F4) {
            camera.selectFree(snap = true)
            val what = if (motorrad != null) "Motorrad" else "Fallback-Anchor"
            println("Kamera-Fokus: Freie Kamera (parented → $what)")
        }

        // Fokus zyklisch (R/T)
        if (key == GLFW_KEY_T) cycleFocus(forward = true)
        if (key == GLFW_KEY_R) cycleFocus(forward = false)

        // Move/Rotate Toggle
        if (key == GLFW_KEY_TAB) {
            currentLevel?.let { lvl ->
                if (controlIndex in lvl.objects.indices) {
                    val obj = lvl.objects[controlIndex]
                    objMoveModes[obj] = !(objMoveModes[obj] ?: true)
                    println("Objekt ${controlIndex + 1}: ${if (objMoveModes[obj] == true) "Bewegen" else "Rotieren"}")
                }
            }
        }

        // Levelwechsel
        if (key == GLFW_KEY_5) { loadLevel(1); rebuildCameraTargets(); focusSelection(0, true); setControl(0) }
        if (key == GLFW_KEY_4) { loadLevel(0); rebuildCameraTargets(); focusSelection(0, true); setControl(0) }

        // Kamera toggles
        if (key == GLFW_KEY_C) camera.toggleActiveCamera()
        if (key == GLFW_KEY_P) camera.toggleProjectionCam1()

        // Sofort-Blackout
        if (key == GLFW_KEY_B) {
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

    private fun buildTargetsList(): MutableList<Transformable> {
        val targets = mutableListOf<Transformable>()
        currentLevel?.objects?.forEach { targets += it }
        return targets
    }

    private fun focusSelection(idx: Int, snap: Boolean = true) {
        val lvl = currentLevel ?: return
        if (idx !in lvl.objects.indices) return
        focusIndex = idx
        camera.setTargets(buildTargetsList(), initialIndex = idx, snap = snap)
        println("Kamera-Fokus: Objekt ${idx + 1}")
    }

    private fun cycleFocus(forward: Boolean) {
        val lvl = currentLevel ?: return
        if (lvl.objects.isEmpty()) return
        focusIndex = if (forward)
            (focusIndex + 1) % lvl.objects.size
        else
            (focusIndex - 1 + lvl.objects.size) % lvl.objects.size
        camera.setTargets(buildTargetsList(), initialIndex = focusIndex, snap = true)
        println("Kamera-Fokus gewechselt → Objekt ${focusIndex + 1}")
    }

    private fun setControl(idx: Int) {
        val lvl = currentLevel ?: return
        if (idx !in lvl.objects.indices) return
        controlIndex = idx
        println("Steuerung: Objekt ${idx + 1}")
    }

    private fun clampCameraHeightsOnly() {
        camera.clampCameras(roomMin, roomMax, wallMargin)
    }

    /** Bewegt das Anchor-Objekt (Motorrad bevorzugt) relativ zur aktiven Kamera. */
    private fun moveAnchorWithWASD(dt: Float) {
        val anchor = anchorTarget() ?: return
        val cam = camera.activeCamera

        var axF = 0f
        if (window.getKeyState(GLFW_KEY_W)) axF += 1f
        if (window.getKeyState(GLFW_KEY_S)) axF -= 1f
        var axR = 0f
        if (window.getKeyState(GLFW_KEY_D)) axR += 1f
        if (window.getKeyState(GLFW_KEY_A)) axR -= 1f
        if (axF == 0f && axR == 0f) return

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

    private fun clampTransformableY(t: Transformable, minY: Float, maxY: Float, margin: Float) {
        val wp = t.getWorldPosition()
        val targetY = wp.y.coerceIn(minY + margin, maxY - margin)
        val dy = targetY - wp.y
        if (abs(dy) > 1e-5f) t.preTranslate(Vector3f(0f, dy, 0f))
    }

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
        clampTransformableToRoom(obj)

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

    private fun rebuildCameraTargets() {
        camera.setTargets(buildTargetsList(), initialIndex = focusIndex.coerceAtMost((currentLevel?.objects?.lastIndex ?: 0)), snap = true)
    }

    fun loadLevel(index: Int) {
        currentLevel = when (index) {
            0 -> LevelLoader.loadLevel1()
            1 -> LevelLoader.loadLevel2()
            else -> null
        }
        levelIndex = index
        focusIndex = 0
        controlIndex = 0
    }

    fun nextLevel() {
        loadLevel(levelIndex + 1)
        rebuildCameraTargets()
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
}

private object MaterialProvider {
    fun grabFrom(r: Renderable): Material? {
        val f = Renderable::class.java.getDeclaredField("meshes")
        f.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val meshes = f.get(r) as MutableList<Mesh>
        return meshes.firstOrNull()?.material
    }
}
