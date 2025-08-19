package cga.exercise.components.geometry

import org.joml.Vector3f

data class Kollision(val min: Vector3f, val max: Vector3f) {
    fun intersects(other: Kollision): Boolean {
        return (min.x <= other.max.x && max.x >= other.min.x) &&
                (min.y <= other.max.y && max.y >= other.min.y) &&
                (min.z <= other.max.z && max.z >= other.min.z)
    }
}