package cga.exercise.components.level

import LoadedMask
import cga.exercise.components.geometry.Renderable
import cga.exercise.components.light.PointLight
import loadMaskRaw

data class Level(
    val ground: Renderable,
    val room: Renderable,
    val objects: List<Renderable>,
    val targetMaskPath: String?,
){
    val targetMask: LoadedMask = loadMaskRaw(targetMaskPath)
}
