package cga.exercise.game

import cga.exercise.components.camera.TronCamera
import cga.exercise.components.geometry.Transformable
import cga.framework.GameWindow
import org.joml.Vector3f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.abs

class CameraController {

    // --- Kleine Container für Zustand & Rig/Kamera ---
    private data class CamState(
        var yaw: Float = 0f,
        var pitch: Float = 0f,
        var dist: Float = 6f,
        var yOffsetDefault: Float = 1f,
        var yOffsetAnchor: Float = 4f
    )

    private data class CamRig(
        val cam: TronCamera,
        val rig: Transformable,
        val state: CamState
    )

    // --- NEU: Per-Target-Orbit-Speicher (pro Kamera) ---
    private data class Orbit(var yaw: Float, var pitch: Float, var dist: Float)
    private val orbitMem = arrayOf(
        mutableMapOf<Transformable, Orbit>(), // für Kamera 0
        mutableMapOf<Transformable, Orbit>()  // für Kamera 1
    )

    // Zwei Setups
    private val cam0 = CamRig(
        TronCamera(), Transformable(),
        CamState(yaw = 0f, pitch = 0f,    dist = 6f, yOffsetDefault = 1.0f, yOffsetAnchor = 4.0f)
    )
    private val cam1 = CamRig(
        TronCamera(), Transformable(),
        CamState(yaw = 0f, pitch = -0.35f, dist = 4f, yOffsetDefault = 0.0f, yOffsetAnchor = 0.8f)
    )

    private val cams = arrayOf(cam0, cam1)

    var activeCam: Int = 0
        private set
    val activeCamera: TronCamera get() = cams[activeCam].cam
    private val activeRig: Transformable get() = cams[activeCam].rig
    private val activeState: CamState get() = cams[activeCam].state

    // Targets / Auswahl
    private val camTargets = mutableListOf<Transformable>()
    private var camTargetIndex = 0
    var currentTarget: Transformable? = null
        private set

    // „Freie“ Kamera folgt diesem Anchor (z. B. Motorrad). N/M-Höhe nur dann erlaubt.
    private var followAnchor: Transformable? = null

    // Grenzen & Geschwindigkeiten
    private val yawSpeed = 1.4f
    private val pitchSpeed = 1.0f
    private val pitchMin = -1.2f
    private val pitchMax =  1.2f

    private val fovMinRad = Math.toRadians(20.0).toFloat()
    private val fovMaxRad = Math.toRadians(100.0).toFloat()
    private val fovZoomSpeedRad = Math.toRadians(60.0).toFloat()
    private val anchorOrthoZoomSpeed = 5.0f

    private val camHeightAdjustSpeed = 1.5f
    private val camHeightMinAnchor = 0.2f
    private val camHeightMaxAnchor = 6.0f

    // Maus (nur einfache Yaw-Steuerung für Cam0)
    private var firstMouseMove = true
    private var lastMouseX = 0.0
    private val MOUSE_SENSITIVITY = 0.002f

    init {
        // Parents setzen
        cam0.cam.parent = cam0.rig
        cam1.cam.parent = cam1.rig

        // Start-Offsets & Distanzen
        cam0.cam.translate(Vector3f(0f, cam0.state.yOffsetDefault, cam0.state.dist))
        cam1.cam.translate(Vector3f(0f, cam1.state.yOffsetDefault, cam1.state.dist))

        // Anfangsorientierung
        cam1.rig.rotate(Math.toRadians(290.0).toFloat(), 0f, 0f)
        if (abs(cam0.state.pitch) > 1e-6f) cam0.rig.rotate(cam0.state.pitch, 0f, 0f)
        if (abs(cam1.state.pitch) > 1e-6f) cam1.rig.rotate(cam1.state.pitch, 0f, 0f)

        cam0.cam.fovRad = Math.toRadians(90.0).toFloat()
        cam1.cam.fovRad = Math.toRadians(90.0).toFloat()
    }

    // --------------------- Public API ---------------------

    /** Legt fest, welchem Objekt die „freie Kamera“ (F4) folgen soll (z. B. Motorrad). */
    fun setAnchor(anchor: Transformable?) {
        followAnchor = anchor
        if (anchor != null && !camTargets.contains(anchor)) {
            camTargets.add(0, anchor) // freie Kamera an Index 0
        }
    }

    /** Targets neu setzen. Wenn followAnchor != null, steht er automatisch an Index 0. */
    fun setTargets(list: List<Transformable>, initialIndex: Int = 0, snap: Boolean = true) {
        camTargets.clear()
        followAnchor?.let { camTargets += it }
        camTargets += list
        if (camTargets.isEmpty()) return

        // initialIndex bezieht sich nur auf list; ggf. +1 wegen Anchor vorne
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
        cam0.cam.toggleProjection()
        println("Cam1 Projektion: ${cam0.cam.projectionMode}")
    }

    /** Fokus auf konkretes Target (wird ggf. zur Liste hinzugefügt). */
    fun focusOn(target: Transformable?, snap: Boolean = true) {
        if (target == null) return
        val idx = camTargets.indexOf(target).takeIf { it >= 0 } ?: run {
            camTargets += target; camTargets.lastIndex
        }
        camTargetIndex = idx
        setCameraTarget(target, snap)
    }

    /** Fokus „freie Kamera“ (falls Anchor gesetzt). */
    fun selectFree(snap: Boolean = true) {
        followAnchor?.let { focusOn(it, snap) }
    }

    fun onMouseMove(xpos: Double, ypos: Double) {
        if (firstMouseMove) { lastMouseX = xpos; firstMouseMove = false; return }
        val dx = xpos - lastMouseX
        lastMouseX = xpos
        if (activeCam == 0) { // einfache Yaw-Drehung für Cam0
            val dy = (-dx * MOUSE_SENSITIVITY).toFloat()
            cam0.rig.rotate(0f, dy, 0f)
            cam0.state.yaw += dy
        }
    }

    /** Pro Frame: Folgen, einfache Yaw/Pitch, Zoom, N/M-Höhe. */
    fun update(dt: Float, window: GameWindow) {
        // Rigs folgen der Zielposition (nur Translation)
        currentTarget?.let { tgt ->
            cams.forEach { moveRigToTargetPosition(it.rig, tgt) }
        }

        // Yaw Cam0 (J/L)
        if (activeCam == 0) {
            val dy = axis(window, GLFW_KEY_J, GLFW_KEY_L) * yawSpeed * dt
            if (dy != 0f) {
                cam0.rig.rotate(0f, dy, 0f)
                cam0.state.yaw += dy
            }
        }

        // Pitch Cam1 (I/K)
        if (activeCam == 1) {
            val dp = (if (isDown(window, GLFW_KEY_I)) +pitchSpeed * dt else 0f) +
                    (if (isDown(window, GLFW_KEY_K)) -pitchSpeed * dt else 0f)
            if (dp != 0f) {
                val old = cam1.state.pitch
                val np = (old + dp).coerceIn(pitchMin, pitchMax)
                val apply = np - old
                if (abs(apply) > 1e-6f) {
                    cam1.rig.rotate(apply, 0f, 0f)
                    cam1.state.pitch = np
                }
            }
        }

        // Zoom (U/O)
        handleZoom(dt, window)

        // Nur wenn freies Ziel (Anchor) aktiv ist: Höhe mit N/M verstellbar
        adjustCamHeightIfAnchorTarget(dt, window)
    }

    /** Nur Y-Clamping (kannst du extern mit Raumgrenzen aufrufen). */
    fun clampCameras(roomMin: Vector3f, roomMax: Vector3f, margin: Float) {
        cams.forEach { clampY(it.rig, roomMin.y, roomMax.y, margin) }
    }

    // --------------------- Internals ---------------------

    private fun setCameraTarget(target: Transformable, snap: Boolean) {
        // NEU: alten Orbit je Kamera sichern
        currentTarget?.let { prev ->
            for (i in 0..1) {
                val s = cams[i].state
                orbitMem[i][prev] = Orbit(s.yaw, s.pitch, s.dist)
            }
        }

        currentTarget = target
        if (!snap) return

        // Rigs zu Target schieben + gespeicherten Orbit anwenden
        for (i in 0..1) {
            val c = cams[i]
            moveRigToTargetPosition(c.rig, target)

            // NEU: gespeicherten Orbit laden (sonst Default)
            val o = orbitMem[i][target] ?: defaultOrbit(i)

            // NEU: Differenzrotation anwenden statt "reset & neu"
            applyYawPitch(i, o.yaw, o.pitch)

            // Distanz & Y-Offset anwenden
            applyDistanceAndYOffset(i, target, o.dist)
        }
    }

    // NEU: kompaktes Setzen von Yaw/Pitch mittels Differenzrotation
    private fun applyYawPitch(i: Int, newYaw: Float, newPitch: Float) {
        val c = cams[i]
        val s = c.state
        val dYaw = newYaw - s.yaw
        val dPitch = newPitch - s.pitch
        val eps = 1e-6f
        if (abs(dPitch) > eps) c.rig.rotate(dPitch, 0f, 0f)
        if (abs(dYaw)   > eps) c.rig.rotate(0f, dYaw, 0f)
        s.yaw = newYaw
        s.pitch = newPitch
    }

    private fun defaultOrbit(i: Int): Orbit {
        val s = cams[i].state
        val defPitch = if (i == 0) 0f else -0.35f
        return Orbit(yaw = 0f, pitch = defPitch, dist = s.dist)
    }

    private fun applyDistanceAndYOffset(i: Int, target: Transformable, newDist: Float) {
        val c = cams[i]
        c.state.dist = newDist
        val pos = c.cam.getPosition()
        val wantY = yOffsetFor(i, target)
        val dY = wantY - pos.y
        val dZ = c.state.dist - pos.z
        if (abs(dY) > 1e-6f || abs(dZ) > 1e-6f) c.cam.translate(Vector3f(0f, dY, dZ))
    }

    private fun yOffsetFor(i: Int, target: Transformable?): Float {
        val anchor = followAnchor
        return if (anchor != null && target === anchor) {
            cams[i].state.yOffsetAnchor.coerceIn(camHeightMinAnchor, camHeightMaxAnchor)
        } else cams[i].state.yOffsetDefault
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

        if (activeCam == 0 && cam0.cam.projectionMode == TronCamera.ProjectionMode.Orthographic) {
            var dh = 0f
            if (zoomIn)  dh -= anchorOrthoZoomSpeed * dt
            if (zoomOut) dh += anchorOrthoZoomSpeed * dt
            if (dh != 0f) cam0.cam.addOrthoHeight(dh)
        } else {
            var df = 0f
            if (zoomIn)  df -= fovZoomSpeedRad * dt
            if (zoomOut) df += fovZoomSpeedRad * dt
            if (df != 0f) activeCamera.fovRad =
                (activeCamera.fovRad + df).coerceIn(fovMinRad, fovMaxRad)
        }
    }

    private fun adjustCamHeightIfAnchorTarget(dt: Float, window: GameWindow) {
        val anchor = followAnchor ?: return
        if (currentTarget !== anchor) return

        var dh = 0f
        if (isDown(window, GLFW_KEY_M)) dh += camHeightAdjustSpeed * dt
        if (isDown(window, GLFW_KEY_N)) dh -= camHeightAdjustSpeed * dt
        if (dh == 0f) return

        val st = activeState
        val newOffset = (st.yOffsetAnchor + dh).coerceIn(camHeightMinAnchor, camHeightMaxAnchor)
        val apply = newOffset - st.yOffsetAnchor
        if (abs(apply) > 1e-6f) {
            activeCamera.translate(Vector3f(0f, apply, 0f))
            st.yOffsetAnchor = newOffset
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
