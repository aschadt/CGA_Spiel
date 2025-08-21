package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.Transformable
import cga.framework.GameWindow
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.abs

/**
 * Kapselt die gesamte Kameralogik (zwei Orbit-Kameras mit Rigs, Targets, Zoom, Pitch/Yaw, Maussteuerung)
 * Clamping: Für beide Kameras wird NUR die Höhe (Y) zwischen Boden und Decke begrenzt.
 * Wände (X/Z) sind frei.
 */
class CameraController {

    // Öffentliche Kameras/Rigs
    val cam1 = TronCamera()          // Anchor-Kamera (umschaltbar auf Ortho)
    val cam2 = TronCamera()          // zweite Kamera (immer Perspektive)
    val rig1 = Transformable()
    val rig2 = Transformable()

    // Aktiver Index: 0 = cam1/rig1, 1 = cam2/rig2
    var activeCam: Int = 0
        private set
    val activeCamera: TronCamera get() = if (activeCam == 0) cam1 else cam2
    private val activeRig: Transformable get() = if (activeCam == 0) rig1 else rig2

    // Ziele / Zustand
    private val camTargets = mutableListOf<Transformable>()
    private var camTargetIndex = 0
    var currentTarget: Transformable? = null
        private set
    private var followTarget: Transformable? = null
    private var followAnchor: Transformable? = null

    // Orbit-Zustände pro Target separat für beide Cams
    private data class OrbitState(var yaw: Float = 0f, var pitch: Float = 0f, var dist: Float = 6f)
    private val cam1States = mutableMapOf<Transformable, OrbitState>()
    private val cam2States = mutableMapOf<Transformable, OrbitState>()

    // Orbit-Parameter
    private var yaw1 = 0f
    private var pitch1 = 0f
    private var dist1 = 6.0f

    private var yaw2 = 0f
    private var pitch2 = -0.35f
    private var dist2 = 2.6f

    // Geschwindigkeiten / Grenzen
    private val yawSpeed = 1.4f
    private val pitchSpeed = 1.0f
    private val pitchMin = -1.2f
    private val pitchMax =  1.2f
    private val fovMinRad = Math.toRadians(20.0).toFloat()
    private val fovMaxRad = Math.toRadians(100.0).toFloat()
    private val fovZoomSpeedRad = Math.toRadians(60.0).toFloat()
    private val anchorOrthoZoomSpeed = 5.0f

    // Kamera-Y-Offets
    private val cam1YOffsetDefault = 1.0f
    private val cam2YOffsetDefault = 0.0f

    // Anchor-spezifische Offsets
    private val cam1YOffsetAnchorStart = 4.0f
    private val cam2YOffsetAnchorStart = 0.8f
    private var cam1YOffsetAnchor = cam1YOffsetAnchorStart
    private var cam2YOffsetAnchor = cam2YOffsetAnchorStart
    private val camHeightAdjustSpeed = 1.5f
    private val camHeightMinAnchor   = 0.2f
    private val camHeightMaxAnchor   = 4.8f
    private fun clampAnchorOffset(v: Float) = v.coerceIn(camHeightMinAnchor, camHeightMaxAnchor)
    private fun cam1YOffsetFor(target: Transformable?) =
        if (target != null && followAnchor != null && target === followAnchor) clampAnchorOffset(cam1YOffsetAnchor) else cam1YOffsetDefault
    private fun cam2YOffsetFor(target: Transformable?) =
        if (target != null && followAnchor != null && target === followAnchor) clampAnchorOffset(cam2YOffsetAnchor) else cam2YOffsetDefault

    // Maus
    private var firstMouseMove = true
    private var lastMouseX = 0.0

    init {
        // Kameras in die Rigs hängen (Orbit-Setup)
        cam1.parent = rig1
        cam2.parent = rig2

        cam1.translate(Vector3f(0f, cam1YOffsetDefault, dist1))
        cam2.translate(Vector3f(0f, cam2YOffsetDefault, dist2))

        // (Original) rig2 initial 270° um X
        rig2.rotate(Math.toRadians(270.0).toFloat(), 0f, 0f)

        if (abs(pitch1) > 1e-6f) rig1.rotate(pitch1, 0f, 0f)
        if (abs(pitch2) > 1e-6f) rig2.rotate(pitch2, 0f, 0f)

        cam1.fovRad = Math.toRadians(90.0).toFloat()
        cam2.fovRad = Math.toRadians(90.0).toFloat()
    }

    // --------------------- Public API ---------------------

    fun setAnchor(anchor: Transformable?) {
        followAnchor = anchor
    }

    fun setTargets(list: List<Transformable>, initialIndex: Int = 0, snap: Boolean = true) {
        camTargets.clear()
        camTargets.addAll(list)
        if (camTargets.isNotEmpty()) {
            camTargetIndex = initialIndex.coerceIn(0, camTargets.lastIndex)
            setCameraTarget(camTargets[camTargetIndex], snap)
        }
    }

    fun cycleTarget(forward: Boolean = true) {
        if (camTargets.isEmpty()) return
        camTargetIndex = if (forward)
            (camTargetIndex + 1) % camTargets.size
        else
            (camTargetIndex - 1 + camTargets.size) % camTargets.size
        setCameraTarget(camTargets[camTargetIndex], snap = true)
        println("Kamera-Ziel: $camTargetIndex / ${camTargets.size}")
    }

    fun toggleActiveCamera() {
        activeCam = 1 - activeCam
        println("Aktive Kamera: ${if (activeCam == 0) "Cam 1 (Anchor)" else "Cam 2"}")
    }

    fun toggleProjectionCam1() {
        cam1.toggleProjection()
        println("Anchor-Kamera Projektion: ${cam1.projectionMode}")
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

    /**
     * Pro-Frame Update: Yaw/Pitch, Zoom, Kamera-Höhe (M/N), Rigs zur Target-Position schieben.
     * Die Scene ruft diese Methode einfach in ihrem update() auf.
     */
    fun update(dt: Float, window: GameWindow) {
        // Rigs folgen nur der Zielposition translational (kein Parent)
        followTarget?.let { tgt ->
            moveRigToTargetPosition(rig1, tgt)
            moveRigToTargetPosition(rig2, tgt)
        }

        // Yaw (J/L) für cam1
        if (activeCam == 0) {
            val yawDir = axis(window, GLFW_KEY_J, GLFW_KEY_L)
            if (yawDir != 0f) {
                val dy = yawDir * yawSpeed * dt
                rig1.rotate(0f, dy, 0f)
                yaw1 += dy
            }
        }

        // Pitch (I/K) für cam2
        if (activeCam == 1) {
            val dp = (if (isDown(window, GLFW_KEY_I)) +pitchSpeed * dt else 0f) +
                    (if (isDown(window, GLFW_KEY_K)) -pitchSpeed * dt else 0f)
            val newPitch = (getPitchRef() + dp).coerceIn(pitchMin, pitchMax)
            val apply = newPitch - getPitchRef()
            if (abs(apply) > 1e-6f) {
                rig2.rotate(apply, 0f, 0f)
                setPitchRef(newPitch)
            }
        }

        // Zoom (U/O): cam1 ggf. Orthografisch, sonst Perspektive
        handleZoom(dt, window)

        // Kamera-Höhe (M/N) nur wenn Target == Anchor
        adjustActiveCamHeightIfAnchorTarget(dt, window)
    }

    /**
     * Kameras pro Frame nur vertikal clampen (Boden/Decke).
     */
    fun clampCameras(roomMin: Vector3f, roomMax: Vector3f, margin: Float) {
        clampTransformableY(cam1, roomMin.y, roomMax.y, margin)
        clampTransformableY(cam2, roomMin.y, roomMax.y, margin)
    }

    // --------------------- Internals ---------------------

    private fun setCameraTarget(target: Transformable, snap: Boolean = true) {
        val eps = 1e-6f

        // alten State sichern
        currentTarget?.let { prev ->
            cam1States[prev] = OrbitState(yaw1, pitch1, dist1)
            cam2States[prev] = OrbitState(yaw2, pitch2, dist2)
        }

        currentTarget = target
        followTarget = target

        // Falls Anchor: Offsets clampen
        if (followAnchor != null && target === followAnchor) {
            cam1YOffsetAnchor = clampAnchorOffset(cam1YOffsetAnchor)
            cam2YOffsetAnchor = clampAnchorOffset(cam2YOffsetAnchor)
        }

        if (!snap) return

        // Rigs zur Zielposition (nur Translation)
        moveRigToTargetPosition(rig1, target)
        moveRigToTargetPosition(rig2, target)

        // Zustand laden (oder Defaults)
        val s1 = cam1States[target] ?: OrbitState(0f, 0f, dist1)
        val s2 = cam2States[target] ?: OrbitState(0f, -0.35f, dist2)

        // Cam1: reset & anwenden
        if (abs(yaw1)   > eps) rig1.rotate(0f, -yaw1, 0f)
        if (abs(pitch1) > eps) rig1.rotate(-pitch1, 0f, 0f)
        yaw1 = s1.yaw; pitch1 = s1.pitch
        if (abs(pitch1) > eps) rig1.rotate(pitch1, 0f, 0f)
        if (abs(yaw1)   > eps) rig1.rotate(0f, yaw1, 0f)

        // Cam2: reset & anwenden
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

    private fun moveRigToTargetPosition(rig: Transformable, target: Transformable) {
        val delta = Vector3f(target.getWorldPosition()).sub(rig.getWorldPosition())
        if (delta.lengthSquared() > 1e-10f) rig.preTranslate(delta)
    }

    // ---- Eingabe/Zoom/Höhe ----

    private fun isDown(window: GameWindow, key: Int) = window.getKeyState(key)
    private fun axis(window: GameWindow, plusKey: Int, minusKey: Int): Float {
        var a = 0f
        if (isDown(window, plusKey)) a += 1f
        if (isDown(window, minusKey)) a -= 1f
        return a
    }

    private fun getPitchRef(): Float = if (activeCam == 0) pitch1 else pitch2
    private fun setPitchRef(v: Float) { if (activeCam == 0) pitch1 = v else pitch2 = v }

    private fun handleZoom(dt: Float, window: GameWindow) {
        val zoomIn = isDown(window, GLFW_KEY_U)
        val zoomOut = isDown(window, GLFW_KEY_O)
        if (activeCam == 0 && cam1.projectionMode == TronCamera.ProjectionMode.Orthographic) {
            var dh = 0f
            if (zoomIn)  dh += -anchorOrthoZoomSpeed * dt
            if (zoomOut) dh +=  +anchorOrthoZoomSpeed * dt
            if (dh != 0f) cam1.addOrthoHeight(dh)
        } else {
            var df = 0f
            if (zoomIn)  df += -fovZoomSpeedRad * dt
            if (zoomOut) df +=  +fovZoomSpeedRad * dt
            if (df != 0f) activeCamera.fovRad = (activeCamera.fovRad + df).coerceIn(fovMinRad, fovMaxRad)
        }
    }

    private fun adjustActiveCamHeightIfAnchorTarget(dt: Float, window: GameWindow) {
        if (currentTarget != followAnchor || followAnchor == null) return
        var dh = 0f
        if (isDown(window, GLFW_KEY_M)) dh += camHeightAdjustSpeed * dt
        if (isDown(window, GLFW_KEY_N)) dh -= camHeightAdjustSpeed * dt
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

    // ---- Nur-Y-Clamping gegen Boden/Decke ----

    /** Klemmt NUR die Höhe (Y) zwischen minY und maxY (mit Margin). X/Z bleiben unverändert. */
    private fun clampTransformableY(t: Transformable, minY: Float, maxY: Float, margin: Float) {
        val wp = t.getWorldPosition()
        val targetY = wp.y.coerceIn(minY + margin, maxY - margin)
        val dy = targetY - wp.y
        if (kotlin.math.abs(dy) > 1e-5f) {
            t.preTranslate(Vector3f(0f, dy, 0f))
        }
    }
}
