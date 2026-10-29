package io.github.riyadmondol2006.idsheetstudio.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * Material 3 Expressive motion tokens (spatial springs bounce slightly, effects springs don't).
 * The stable material3 artifact keeps its MotionScheme internal, so the published token values
 * are reproduced here.
 */
object ExpressiveMotion {
    fun <T> spatialFast(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
    fun <T> spatial(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
    fun <T> spatialSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
    fun <T> effectsSlow(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)

    val offset: FiniteAnimationSpec<IntOffset> = spring(dampingRatio = 0.8f, stiffness = 380f)
    val size: FiniteAnimationSpec<IntSize> = spring(dampingRatio = 0.8f, stiffness = 380f)
}
