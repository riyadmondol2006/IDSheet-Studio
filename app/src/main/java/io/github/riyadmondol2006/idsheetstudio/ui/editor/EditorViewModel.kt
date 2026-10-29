package io.github.riyadmondol2006.idsheetstudio.ui.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.riyadmondol2006.idsheetstudio.AppContainer
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import io.github.riyadmondol2006.idsheetstudio.image.BitmapIO
import io.github.riyadmondol2006.idsheetstudio.image.CropMath
import io.github.riyadmondol2006.idsheetstudio.image.FaceAlignMath
import io.github.riyadmondol2006.idsheetstudio.image.FaceAligner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class EditorState(
    val saved: PhotoItem? = null,
    val draft: PhotoItem? = null,
    val source: Bitmap? = null,
    val unit: LengthUnit = LengthUnit.MM,
    val loadingSource: Boolean = true,
    val aligningFace: Boolean = false,
) {
    val dirty: Boolean get() = saved != null && draft != null && editable(saved) != editable(draft)

    private fun editable(p: PhotoItem) = listOf(p.presetId, p.size, p.quantity, p.transform, p.useCutout, p.backgroundColor, p.label)
}

class EditorViewModel(
    private val container: AppContainer,
    private val projectId: String,
    private val photoId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closed: SharedFlow<Unit> = _closed

    init {
        viewModelScope.launch {
            container.projects.observeProject(projectId)
                .map { project -> project?.let { it.settings.displayUnit to it.photos.firstOrNull { p -> p.id == photoId } } }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { (unit, photo) ->
                    if (photo == null) {
                        _closed.tryEmit(Unit)
                        return@collect
                    }
                    _state.update { s ->
                        // Keep the user's unsaved edits, but adopt server-side changes such as a
                        // freshly removed background.
                        val draft = s.draft?.copy(
                            cutoutPath = photo.cutoutPath,
                            backgroundState = photo.backgroundState,
                            backgroundError = photo.backgroundError,
                            useCutout = if (photo.cutoutPath != null && s.draft.cutoutPath == null) true else s.draft.useCutout && photo.cutoutPath != null,
                        ) ?: photo
                        s.copy(saved = photo, draft = draft, unit = unit)
                    }
                }
        }
        viewModelScope.launch {
            _state.map { it.draft?.activePath }.filterNotNull().distinctUntilChanged().collect { path ->
                _state.update { it.copy(loadingSource = true) }
                val bitmap = withContext(Dispatchers.IO) { BitmapIO.decodeFile(path, 1600) }
                _state.update { it.copy(source = bitmap, loadingSource = false) }
            }
        }
        viewModelScope.launch {
            container.backgroundRemover.errors.collect { _messages.emit(it) }
        }
    }

    private fun edit(block: (PhotoItem) -> PhotoItem) = _state.update { s -> s.copy(draft = s.draft?.let(block)) }

    fun gesture(frameW: Float, frameH: Float, cx: Float, cy: Float, panX: Float, panY: Float, zoom: Float, rotation: Float) {
        // Only the aspect ratio matters, so the preview bitmap stands in for the full source.
        val source = _state.value.source?.takeUnless { it.isRecycled }
        edit {
            it.copy(
                transform = CropMath.applyGesture(
                    it.transform, frameW.toDouble(), frameH.toDouble(), cx.toDouble(), cy.toDouble(),
                    panX.toDouble(), panY.toDouble(), zoom.toDouble(), rotation.toDouble(),
                    srcW = (source?.width ?: it.sourceWidth).toDouble(),
                    srcH = (source?.height ?: it.sourceHeight).toDouble(),
                ),
            )
        }
    }

    fun rotateQuarter(clockwise: Boolean) = edit {
        it.copy(transform = it.transform.copy(rotation = CropMath.normalizeDegrees(it.transform.rotation + if (clockwise) 90.0 else -90.0)))
    }

    /** Fine "straighten" angle in −45…45° relative to the nearest quarter turn. */
    fun setStraighten(angle: Double) = edit {
        val base = (it.transform.rotation / 90.0).roundToInt() * 90.0
        it.copy(transform = it.transform.copy(rotation = CropMath.normalizeDegrees(base + angle)))
    }

    fun setZoom(zoom: Double) = edit {
        it.copy(transform = it.transform.copy(zoom = zoom.coerceIn(PhotoTransform.MIN_ZOOM, PhotoTransform.MAX_ZOOM)))
    }

    fun resetTransform() = edit { it.copy(transform = PhotoTransform.Identity) }

    /** Detects the face in the source the editor shows and frames it; rotation is reset to 0. */
    fun autoAlignFace() = viewModelScope.launch {
        val s = _state.value
        val path = s.draft?.activePath ?: return@launch
        if (s.aligningFace) return@launch
        _state.update { it.copy(aligningFace = true) }
        val face = try {
            withContext(Dispatchers.Default) { FaceAligner.detect(path) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        } finally {
            _state.update { it.copy(aligningFace = false) }
        }
        var aligned = false
        if (face != null) {
            edit { p ->
                val t = FaceAlignMath.align(
                    face.sourceWidth.toDouble(), face.sourceHeight.toDouble(), p.size.width, p.size.height,
                    face.eyeX, face.eyeY, face.eyeDistance,
                ) ?: return@edit p
                aligned = true
                p.copy(transform = t)
            }
        }
        if (!aligned) _messages.emit("No face found — position the photo by hand.")
    }

    fun selectPreset(id: String) = edit { p ->
        val preset = PhotoSizePresets.byId(id) ?: return@edit p.copy(presetId = PhotoSizePresets.CUSTOM_ID)
        p.copy(presetId = preset.id, size = preset.size)
    }

    fun setCustomSize(width: Double? = null, height: Double? = null) = edit { p ->
        p.copy(presetId = PhotoSizePresets.CUSTOM_ID, size = SizeMm(width ?: p.size.width, height ?: p.size.height))
    }

    fun swapOrientation() = edit { p -> p.copy(presetId = PhotoSizePresets.CUSTOM_ID, size = p.size.rotated()) }

    fun setQuantity(q: Int) = edit { it.copy(quantity = q.coerceIn(0, PhotoItem.MAX_QUANTITY)) }
    fun setUseCutout(use: Boolean) = edit { it.copy(useCutout = use && it.cutoutPath != null) }
    fun setBackgroundColor(color: Long) = edit { it.copy(backgroundColor = color) }
    fun setLabel(label: String) = edit { it.copy(label = label.take(60)) }

    fun setUnit(unit: LengthUnit) {
        _state.update { it.copy(unit = unit) }
    }

    fun removeBackground() = viewModelScope.launch {
        val photo = _state.value.draft ?: return@launch
        if (photo.backgroundState == BackgroundState.PROCESSING) return@launch
        if (!container.backgroundRemover.hasApiKey()) {
            _messages.emit("Add your remove.bg API key in Settings to remove backgrounds.")
            return@launch
        }
        container.backgroundRemover.enqueue(listOf(photo))
    }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        val draft = _state.value.draft ?: return@launch
        container.projects.updatePhoto(draft)
        onDone()
    }
}
