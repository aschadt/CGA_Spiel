package cga.exercise.components.level

import cga.exercise.components.geometry.Renderable
import cga.exercise.components.light.PointLight

data class Level(
    val ground: Renderable,
    val room: Renderable,
    val objects: List<Renderable>,
    val targetMaskPath: String? = null
)
