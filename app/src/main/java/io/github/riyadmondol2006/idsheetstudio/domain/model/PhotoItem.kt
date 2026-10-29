package io.github.riyadmondol2006.idsheetstudio.domain.model

import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import kotlinx.serialization.Serializable

/**
 * Crop transform of a photo inside its target frame. Values are resolution independent:
 * - [zoom] is relative to the "cover" scale that just fills the frame (1 = fill).
 * - [offsetX]/[offsetY] move the image centre in fractions of the frame width/height.
 * - [rotation] is in degrees, clockwise, around the image centre.
 */
@Serializable
data class PhotoTransform(
    val zoom: Double = 1.0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val rotation: Double = 0.0,
) {
    companion object {
        const val MIN_ZOOM = 0.3
        const val MAX_ZOOM = 8.0
        val Identity = PhotoTransform()
    }
}

enum class BackgroundState { ORIGINAL, PROCESSING, REMOVED, FAILED }

data class PhotoItem(
    val id: String,
    val projectId: String,
    val orderIndex: Int,
    val originalPath: String,
    val cutoutPath: String?,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val presetId: String,
    val size: SizeMm,
    val quantity: Int,
    val transform: PhotoTransform,
    /** Whether the background-removed cut-out is used instead of the original. */
    val useCutout: Boolean,
    /** Colour painted behind the photo (visible where the background was removed). */
    val backgroundColor: Long,
    val backgroundState: BackgroundState,
    val backgroundError: String?,
    val label: String,
    /** Incremented on every visual change; used for cache keys. */
    val revision: Long,
) {
    val presetName: String
        get() = PhotoSizePresets.byId(presetId)?.name ?: "Custom"

    val activePath: String get() = if (useCutout && cutoutPath != null) cutoutPath else originalPath

    companion object {
        const val WHITE = 0xFFFFFFFF
        const val MAX_QUANTITY = 500
    }
}
