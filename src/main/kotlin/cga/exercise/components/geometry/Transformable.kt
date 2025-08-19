package cga.exercise.components.geometry

import org.joml.Matrix4f
import org.joml.Vector3f

open class Transformable(private var modelMatrix: Matrix4f = Matrix4f(), var parent: Transformable? = null) {

    /**
     * Returns copy of object model matrix
     * @return modelMatrix
     */
    // Gibt eine Kopie der lokalen Modellmatrix zurück
    fun getModelMatrix(): Matrix4f {
        return Matrix4f(modelMatrix)
    }

    /**
     * Returns multiplication of world and object model matrices.
     * Multiplication has to be recursive for all parents.
     * Hint: scene graph
     * @return world modelMatrix
     */
    // Berechnet die Welt-Modellmatrix, indem die Modellmatrix aller Eltern rekursiv multipliziert wird
    // Das ergibt die absolute Position & Orientierung im Weltkoordinatensystem
    fun getWorldModelMatrix(): Matrix4f {
        return parent?.getWorldModelMatrix()?.mul(Matrix4f(modelMatrix)) ?: Matrix4f(modelMatrix)
    }

    /**
     * Rotates object around its own origin.
     * @param pitch radiant angle around x-axis ccw
     * @param yaw radiant angle around y-axis ccw
     * @param roll radiant angle around z-axis ccw
     */
    // Führt eine Rotation um die lokalen Achsen (x=pitch, y=yaw, z=roll) aus.
    // Rotation wird an die bestehende Modellmatrix rechts angehängt (lokales Koordinatensystem)
    fun rotate(pitch: Float, yaw: Float, roll: Float) {
       modelMatrix.rotateXYZ(pitch, yaw, roll)
    }

    /**
     * Rotates object around given rotation center.
     * @param pitch radiant angle around x-axis ccw
     * @param yaw radiant angle around y-axis ccw
     * @param roll radiant angle around z-axis ccw
     * @param altMidpoint rotation center
     */
    // Rotation um einen beliebigen Punkt im Raum (nicht unbedingt das Objektzentrum).
    // Dazu wird das Objekt erst zum Ursprung verschoben, rotiert, dann zurückverschoben.
    fun rotateAroundPoint(pitch: Float, yaw: Float, roll: Float, altMidpoint: Vector3f) {
        val rotation = Matrix4f().rotateX(pitch).rotateY(yaw).rotateZ(roll)
        val toMid    = Matrix4f().translate(altMidpoint)
        val fromMid  = Matrix4f().translate(Vector3f(altMidpoint).negate()) // <— Kopie statt mutate
        modelMatrix = Matrix4f().mul(toMid).mul(rotation).mul(fromMid).mul(modelMatrix)
    }

    /**
     * Translates object based on its own coordinate system.
     * @param deltaPos delta positions
     */
    // Verschiebt das Objekt relativ zu seinem eigenen lokalen Koordinatensystem
    fun translate(deltaPos: Vector3f) {
        modelMatrix.translate(deltaPos)
    }

    /**
     * Translates object based on its parent coordinate system.
     * Hint: this operation has to be left-multiplied
     * @param deltaPos delta positions (x, y, z)
     */
    // Verschiebt das Objekt im Koordinatensystem des Elternobjekts (Parent)
    // Wichtig: Die Translation wird links an die Modellmatrix angehängt (Matrix-Multiplikation von links)
    fun preTranslate(deltaPos: Vector3f) {
        modelMatrix = Matrix4f().translate(deltaPos).mul(modelMatrix)
    }

    /**
     * Scales object related to its own origin
     * @param scale scale factor (x, y, z)
     */
    // Skaliert das Objekt entlang seiner lokalen Achsen (x, y, z)
    fun scale(scale: Vector3f) {
        modelMatrix = modelMatrix.scale(scale)
    }

    /**
     * Returns position based on aggregated translations.
     * Hint: last column of model matrix
     * @return position
     */
    // Gibt die lokale Position des Objekts zurück.
    // Die Position ist in der letzten Spalte der Modellmatrix gespeichert (m30, m31, m32)
    fun getPosition(): Vector3f {
        return Vector3f(modelMatrix.m30(), modelMatrix.m31(), modelMatrix.m32())
    }

    /**
     * Returns position based on aggregated translations incl. parents.
     * Hint: last column of world model matrix
     * @return position
     */
    // Gibt die globale Position des Objekts zurück,
    // also inkl. aller Transformationen der Elternobjekte (Weltposition)
    fun getWorldPosition(): Vector3f {
        val world = getWorldModelMatrix()
        return Vector3f(world.m30(), world.m31(), world.m32())
    }

    /**
     * Returns x-axis of object coordinate system
     * Hint: first normalized column of model matrix
     * @return x-axis
     */
    // Liefert X-Achse im lokalen Koordinatensystem (erste Spalte, normalisiert)
    fun getXAxis(): Vector3f {
        return Vector3f(modelMatrix.m00(), modelMatrix.m01(), modelMatrix.m02()).normalize()
    }

    /**
     * Returns y-axis of object coordinate system
     * Hint: second normalized column of model matrix
     * @return y-axis
     */
    // Y-Achse im lokalen System (zweite Spalte), normalisiert
    fun getYAxis(): Vector3f {
        return Vector3f(modelMatrix.m10(), modelMatrix.m11(), modelMatrix.m12()).normalize()
    }

    /**
     * Returns z-axis of object coordinate system
     * Hint: third normalized column of model matrix
     * @return z-axis
     */
    // Z-Achse im lokalen System (dritte Spalte), normalisiert
    fun getZAxis(): Vector3f {
        return Vector3f(modelMatrix.m20(), modelMatrix.m21(), modelMatrix.m22()).normalize()
    }

    /**
     * Returns x-axis of world coordinate system
     * Hint: first normalized column of world model matrix
     * @return x-axis
     */
    // X-Achse im Weltkoordinatensystem (rekursive Matrix, erste Spalte), normalisiert
    fun getWorldXAxis(): Vector3f {
        val world = getWorldModelMatrix()
        return Vector3f(world.m00(), world.m01(), world.m02()).normalize()
    }

    /**
     * Returns y-axis of world coordinate system
     * Hint: second normalized column of world model matrix
     * @return y-axis
     */
    // Y-Achse im Weltkoordinatensystem, normalisiert
    fun getWorldYAxis(): Vector3f {
        val world = getWorldModelMatrix()
        return Vector3f(world.m10(), world.m11(), world.m12()).normalize()
    }

    /**
     * Returns z-axis of world coordinate system
     * Hint: third normalized column of world model matrix
     * @return z-axis
     */
    // Z-Achse im Weltkoordinatensystem, normalisiert
    fun getWorldZAxis(): Vector3f {
        val world = getWorldModelMatrix()
        return Vector3f(world.m20(), world.m21(), world.m22()).normalize()
    }
}