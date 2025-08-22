package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.Transformable
import cga.framework.GameWindow
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.abs

class CameraController {

    // --- Zwei Cams + zwei Rigs in Arrays, damit weniger Duplikat-Code nötig ist ---
    val cams = arrayOf(TronCamera(), TronCamera())
    val rigs = arrayOf(Transformable(), Transformable())

    var activeCam: Int = 0
        private set
    val activeCamera: TronCamera get() = cams[activeCam]
    private val activeRig: Transformable get() = rigs[activeCam]

    // Targets (optional mit Free/Anchor vorne)
    private val camTargets = mutableListOf<Transformable>()
    private var camTargetIndex = 0
    var currentTarget: Transformable? = null
        private set

    // „Freie“ Kamera folgt diesem Anchor (z. B. Motorrad). N/M-Höhe nur dann erlaubt.
    private var followAnchor: Transformable? = null

    // Orbit-State pro Kamera und Target
    private data class OrbitState(var yaw: Float = 0f, var pitch: Float = 0f, var dist: Float = 6f)
    private val states = arrayOf(mutableMapOf<Transformable, OrbitState>(),
        mutableMapOf<Transformable, OrbitState>())

    // Aktuelle Orbit-Werte je Kamera
    private val yaw   = floatArrayOf(0f, 0f)
    private val pitch = floatArrayOf(0f, -0.35f)
    private val dist  = floatArrayOf(6.0f, 4.0f)

    // Geschwindigkeiten / Grenzen
    private val yawSpeed = 1.4f
    private val pitchSpeed = 1.0f
    private val pitchMin = -1.2f
    private val pitchMax =  1.2f

    // Zoom
    private val fovMinRad = Math.toRadians(20.0).toFloat()
    private val fovMaxRad = Math.toRadians(100.0).toFloat()
    private val fovZoomSpeedRad = Math.toRadians(60.0).toFloat()
    private val anchorOrthoZoomSpeed = 5.0f

    // Kamera-Y-Offets
    private val camYOffsetDefault = floatArrayOf(1.0f, 0.0f)
    private val camYOffsetAnchorStart = floatArrayOf(4.0f, 0.8f)
    private val camYOffsetAnchor = camYOffsetAnchorStart.copyOf()
    private val camHeightAdjustSpeed = 1.5f
    private val camHeightMinAnchor = 0.2f
    private val camHeightMaxAnchor = 6.0f

    // Maus
    private var firstMouseMove = true
    private var lastMouseX = 0.0
    private val MOUSE_SENSITIVITY = 0.002f

    init {
        // Parent setzen
        cams[0].parent = rigs[0]
        cams[1].parent = rigs[1]

        // Start-Offsets & Distanzen
        cams[0].translate(Vector3f(0f, camYOffsetDefault[0], dist[0]))
        cams[1].translate(Vector3f(0f, camYOffsetDefault[1], dist[1]))

        // gewünschter Anfangswinkel für rig2
        rigs[1].rotate(Math.toRadians(290.0).toFloat(), 0f, 0f)

        if (abs(pitch[0]) > 1e-6f) rigs[0].rotate(pitch[0], 0f, 0f)
        if (abs(pitch[1]) > 1e-6f) rigs[1].rotate(pitch[1], 0f, 0f)

        cams[0].fovRad = Math.toRadians(90.0).toFloat()
        cams[1].fovRad = Math.toRadians(90.0).toFloat()
    }

    // --------------------- Public API ---------------------

    /** Legt fest, welchem Objekt die „freie Kamera“ (F4) folgen soll (z. B. Motorrad). */
    fun setAnchor(anchor: Transformable?) {
        followAnchor = anchor
        // Falls der Anchor bereits als Target drin sein soll: Liste aktualisieren (optional)
        if (anchor != null && !camTargets.contains(anchor)) {
            camTargets.add(0, anchor) // an Position 0 einfügen (Free vorne)
            // Indexe korrigieren: wer gerade Anchor als Ziel hatte, bleibt korrekt;
            // sonst verschiebt sich alles um +1. Unkritisch, da setTargets meist neu gesetzt wird.
        }
    }

    /**
     * Targets neu setzen.
     * Wenn followAnchor != null, steht er automatisch **an Index 0** in der Liste (freie Kamera).
     * initialIndex bezieht sich auf die Objektliste (0..n-1). Intern +1 wegen Anchor vorne.
     */
    fun setTargets(list: List<Transformable>, initialIndex: Int = 0, snap: Boolean = true) {
        camTargets.clear()
        followAnchor?.let { camTargets += it } // Index 0 = freie Kamera, falls vorhanden
        camTargets.addAll(list)

        if (camTargets.isEmpty()) return

        val shift = if (followAnchor != null) 1 else 0
        camTargetIndex = (initialIndex + shift).coerceIn(0, camTargets.lastIndex)
        setCameraTarget(camTargets[camTargetIndex], snap)
    }

    fun cycleTarget(forward: Boolean = true) {
        if (camTargets.isEmpty()) return
        camTargetIndex = if (forward)
            (camTargetIndex + 1) % camTargets.size
        else
            (camTargetIndex - 1 + camTargets.size) % camTargets.size
        setCameraTarget(camTargets[camTargetIndex], snap = true)
    }

    fun toggleActiveCamera() {
        activeCam = 1 - activeCam
        println("Aktive Kamera: ${if (activeCam == 0) "Cam 1" else "Cam 2"}")
    }

    fun toggleProjectionCam1() {
        cams[0].toggleProjection()
        println("Cam1 Projektion: ${cams[0].projectionMode}")
    }

    /** Fokus auf ein konkretes Target. Fügt es hinzu, falls nicht in der Liste. */
    fun focusOn(target: Transformable?, snap: Boolean = true) {
        if (target == null) return
        val idx = camTargets.indexOf(target).takeIf { it >= 0 } ?: run {
            camTargets += target; camTargets.lastIndex
        }
        camTargetIndex = idx
        setCameraTarget(target, snap)
    }

    /** Fokus „freie Kamera“ (folgt Anchor, wenn vorhanden). */
    fun selectFree(snap: Boolean = true) {
        followAnchor?.let { focusOn(it, snap) }
    }

    fun onMouseMove(xpos: Double, ypos: Double) {
        val sensitivity = 0.002f
        if (firstMouseMove) { lastMouseX = xpos; firstMouseMove = false; return }
        val dx = xpos - lastMouseX
        lastMouseX = xpos
        if (activeCam == 0) {
            val dy = (-dx * sensitivity).toFloat()
            rigs[0].rotate(0f, dy, 0f)
            yaw[0] = yaw[0] + dy
        }
    }

    /** Pro Frame: Follow, Yaw/Pitch, Zoom, N/M-Höhe. */
    fun update(dt: Float, window: GameWindow) {
        // Rigs folgen der Zielposition (nur Translation)
        currentTarget?.let { tgt ->
            moveRigToTargetPosition(rigs[0], tgt)
            moveRigToTargetPosition(rigs[1], tgt)
        }

        // Yaw Cam1 (J/L)
        if (activeCam == 0) {
            val dir = axis(window, GLFW_KEY_J, GLFW_KEY_L)
            if (dir != 0f) {
                val dy = dir * yawSpeed * dt
                rigs[0].rotate(0f, dy, 0f)
                yaw[0] += dy
            }
        }

        // Pitch Cam2 (I/K)
        if (activeCam == 1) {
            val dp = (if (isDown(window, GLFW_KEY_I)) +pitchSpeed * dt else 0f) +
                    (if (isDown(window, GLFW_KEY_K)) -pitchSpeed * dt else 0f)
            val newPitch = (pitch[1] + dp).coerceIn(pitchMin, pitchMax)
            val apply = newPitch - pitch[1]
            if (abs(apply) > 1e-6f) {
                rigs[1].rotate(apply, 0f, 0f)
                pitch[1] = newPitch
            }
        }

        // Zoom (U/O)
        handleZoom(dt, window)

        // Nur wenn freies Ziel (Anchor) aktiv ist: Höhe mit N/M verstellbar
        adjustCamHeightIfAnchorTarget(dt, window)
    }

    /** Nur Y-Clamping (wird von außen mit Raumgrenzen aufgerufen) */
    fun clampCameras(roomMin: Vector3f, roomMax: Vector3f, margin: Float) {

    }

    // --------------------- Internals ---------------------

    private fun setCameraTarget(target: Transformable, snap: Boolean) {
        val eps = 1e-6f

        // alten Orbit-Status je Kamera sichern
        currentTarget?.let { prev ->
            for (i in 0..1) states[i][prev] = OrbitState(yaw[i], pitch[i], dist[i])
        }

        currentTarget = target

        if (!snap) return

        // Rigs zu Target schieben
        for (i in 0..1) moveRigToTargetPosition(rigs[i], target)

        // Gespeicherte Zustände laden oder Default
        val s0 = states[0][target] ?: OrbitState(0f, 0f, dist[0])
        val s1 = states[1][target] ?: OrbitState(0f, -0.35f, dist[1])

        // Cam0 reset/apply
        if (abs(yaw[0])   > eps) rigs[0].rotate(0f, -yaw[0], 0f)
        if (abs(pitch[0]) > eps) rigs[0].rotate(-pitch[0], 0f, 0f)
        yaw[0] = s0.yaw; pitch[0] = s0.pitch
        if (abs(pitch[0]) > eps) rigs[0].rotate(pitch[0], 0f, 0f)
        if (abs(yaw[0])   > eps) rigs[0].rotate(0f, yaw[0], 0f)

        // Cam1 reset/apply
        if (abs(yaw[1])   > eps) rigs[1].rotate(0f, -yaw[1], 0f)
        if (abs(pitch[1]) > eps) rigs[1].rotate(-pitch[1], 0f, 0f)
        yaw[1] = s1.yaw; pitch[1] = s1.pitch
        if (abs(pitch[1]) > eps) rigs[1].rotate(pitch[1], 0f, 0f)
        if (abs(yaw[1])   > eps) rigs[1].rotate(0f, yaw[1], 0f)

        // Distanzen + Y-Offsets setzen
        applyDistanceAndYOffset(0, target, s0.dist)
        applyDistanceAndYOffset(1, target, s1.dist)
    }

    private fun applyDistanceAndYOffset(i: Int, target: Transformable, newDist: Float) {
        dist[i] = newDist
        val pos = cams[i].getPosition()
        val wantY = yOffsetFor(i, target)
        val dY = wantY - pos.y
        val dZ = dist[i] - pos.z
        if (abs(dY) > 1e-6f || abs(dZ) > 1e-6f) cams[i].translate(Vector3f(0f, dY, dZ))
    }

    private fun yOffsetFor(i: Int, target: Transformable?): Float {
        val anchor = followAnchor
        return if (anchor != null && target === anchor) {
            camYOffsetAnchor[i].coerceIn(camHeightMinAnchor, camHeightMaxAnchor)
        } else camYOffsetDefault[i]
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

    private fun handleZoom(dt: Float, window: GameWindow) {
        val zoomIn = isDown(window, GLFW_KEY_U)
        val zoomOut = isDown(window, GLFW_KEY_O)
        if (!zoomIn && !zoomOut) return

        if (activeCam == 0 && cams[0].projectionMode == TronCamera.ProjectionMode.Orthographic) {
            var dh = 0f
            if (zoomIn)  dh -= anchorOrthoZoomSpeed * dt
            if (zoomOut) dh += anchorOrthoZoomSpeed * dt
            if (dh != 0f) cams[0].addOrthoHeight(dh)
        } else {
            var df = 0f
            if (zoomIn)  df -= fovZoomSpeedRad * dt
            if (zoomOut) df += fovZoomSpeedRad * dt
            if (df != 0f) activeCamera.fovRad = (activeCamera.fovRad + df).coerceIn(fovMinRad, fovMaxRad)
        }
    }

    private fun adjustCamHeightIfAnchorTarget(dt: Float, window: GameWindow) {
        val anchor = followAnchor ?: return
        if (currentTarget !== anchor) return

        var dh = 0f
        if (isDown(window, GLFW_KEY_M)) dh += camHeightAdjustSpeed * dt
        if (isDown(window, GLFW_KEY_N)) dh -= camHeightAdjustSpeed * dt
        if (dh == 0f) return

        val i = activeCam
        val newOffset = (camYOffsetAnchor[i] + dh).coerceIn(camHeightMinAnchor, camHeightMaxAnchor)
        val apply = newOffset - camYOffsetAnchor[i]
        if (abs(apply) > 1e-6f) {
            cams[i].translate(Vector3f(0f, apply, 0f))
            camYOffsetAnchor[i] = newOffset
        }
    }

    /** Nur Y klemmen. */
    private fun clampY(t: Transformable, minY: Float, maxY: Float, margin: Float) {
        val wp = t.getWorldPosition()
        val targetY = wp.y.coerceIn(minY + margin, maxY - margin)
        val dy = targetY - wp.y
        if (abs(dy) > 1e-5f) t.preTranslate(Vector3f(0f, dy, 0f))
    }
}
