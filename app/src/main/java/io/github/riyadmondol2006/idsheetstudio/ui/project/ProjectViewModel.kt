package io.github.riyadmondol2006.idsheetstudio.ui.project

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.riyadmondol2006.idsheetstudio.AppContainer
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutEngine
import io.github.riyadmondol2006.idsheetstudio.domain.layout.LayoutResult
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetComposer
import io.github.riyadmondol2006.idsheetstudio.domain.layout.SheetPage
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.ManualPosition
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets
import io.github.riyadmondol2006.idsheetstudio.domain.model.Project
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.ui.sheet.MoveRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SheetPreview(val layout: LayoutResult, val pages: List<SheetPage>)

class ProjectViewModel(
    private val container: AppContainer,
    val projectId: String,
) : ViewModel() {

    private val repo = container.projects

    /** Photos and name come straight from the database. */
    val project: StateFlow<Project?> = repo.observeProject(projectId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * Sheet settings are edited locally for instant feedback and written back with a debounce,
     * so typing a margin does not hit the database on every keystroke.
     */
    private val _settings = MutableStateFlow<SheetSettings?>(null)
    val settings: StateFlow<SheetSettings?> = _settings

    @OptIn(FlowPreview::class)
    val preview: StateFlow<SheetPreview?> = combine(project.filterNotNull().map { it.photos }, _settings.filterNotNull()) { photos, settings ->
        val layout = LayoutEngine.layout(photos, settings)
        SheetPreview(layout, SheetComposer.compose(layout, settings))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing

    private var saveJob: Job? = null

    /** Settings changed locally but not yet written; survives cancellation of the save job. */
    @Volatile private var pendingSettings: SheetSettings? = null

    init {
        viewModelScope.launch {
            _settings.value = project.filterNotNull().first().settings
        }
        viewModelScope.launch {
            container.backgroundRemover.errors.collect { _messages.emit(it) }
        }
    }

    fun updateSettings(transform: (SheetSettings) -> SheetSettings) {
        val next = _settings.value?.let(transform) ?: return
        _settings.value = next
        pendingSettings = next
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(350)
            repo.updateSettings(projectId, next)
            if (pendingSettings === next) pendingSettings = null
        }
    }

    /** Flushes pending settings immediately (e.g. before exporting). */
    suspend fun flush() {
        saveJob?.cancel()
        _settings.value?.let { repo.updateSettings(projectId, it) }
        pendingSettings = null
    }

    fun rename(name: String) = viewModelScope.launch { repo.rename(projectId, name) }

    fun importPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _importing.value = true
            val preset = container.settings.current().defaultPresetId
            val outcome = repo.importPhotos(projectId, uris, preset)
            _importing.value = false
            when {
                outcome.failed > 0 && outcome.imported == 0 -> _messages.emit("Couldn't import the selected images.")
                outcome.failed > 0 -> _messages.emit("Imported ${outcome.imported}; ${outcome.failed} couldn't be read.")
                else -> _messages.emit(if (outcome.imported == 1) "Photo added" else "${outcome.imported} photos added")
            }
        }
    }

    /**
     * The stepper reports an absolute value computed from what is on screen; applying it as a
     * delta in the database keeps every tap even when taps outpace the database round-trip.
     */
    fun setQuantity(photo: PhotoItem, quantity: Int) = viewModelScope.launch {
        repo.adjustQuantity(photo.id, quantity - photo.quantity)
    }

    fun duplicate(photo: PhotoItem) = viewModelScope.launch {
        repo.duplicatePhoto(photo.id)
        _messages.emit("Photo duplicated")
    }

    /** Same photo, another size: returns the new photo's id through [onAdded] (e.g. to open it). */
    fun addSize(photo: PhotoItem, presetId: String, onAdded: (String) -> Unit = {}) = viewModelScope.launch {
        val preset = PhotoSizePresets.byId(presetId)
        val id = repo.addSize(photo.id, presetId, preset?.size ?: photo.size) ?: return@launch
        _messages.emit(if (preset != null) "Added ${preset.name} copies of ${photo.label.substringBefore(" · ")}" else "Added a custom size — set it in the editor")
        onAdded(id)
    }

    fun delete(photo: PhotoItem) = viewModelScope.launch {
        repo.deletePhoto(photo.id)
        // Forget manual positions of the removed photo.
        updateSettings { s -> s.copy(manualPositions = s.manualPositions.filterKeys { !it.startsWith(photo.id + "#") }) }
        _messages.emit("Photo removed")
    }

    fun removeBackground(photos: List<PhotoItem>) = viewModelScope.launch {
        val targets = photos.filter { it.backgroundState != BackgroundState.PROCESSING }
        if (targets.isEmpty()) return@launch
        if (!container.backgroundRemover.hasApiKey()) {
            _messages.emit("Add your remove.bg API key in Settings to remove backgrounds.")
            return@launch
        }
        container.backgroundRemover.enqueue(targets)
    }

    /** Applies a manual drag if the new position is free; otherwise explains why not. */
    fun move(request: MoveRequest) {
        val current = preview.value ?: return
        val placement = current.layout.placements.firstOrNull { it.key == request.key } ?: return
        val target = placement.rect.copy(x = request.x, y = request.y)
        val others = current.layout.placementsOn(request.page).filter { it.key != request.key }
        if (!LayoutEngine.isFree(target, others, current.layout.printableArea)) {
            _messages.tryEmit(
                if (!current.layout.printableArea.containsRect(target)) "Photos must stay inside the margins."
                else "That spot overlaps another photo.",
            )
            return
        }
        updateSettings { s ->
            s.copy(manualPositions = s.manualPositions + (request.key to ManualPosition(request.page, request.x, request.y)))
        }
    }

    fun resetManualPositions() = updateSettings { it.copy(manualPositions = emptyMap()) }

    override fun onCleared() {
        // viewModelScope is already cancelled here (lifecycle 2.8+), so persist any edit that the
        // debounced save didn't get to on the application scope.
        pendingSettings?.let { pending -> container.applicationScope.launch { repo.updateSettings(projectId, pending) } }
    }
}

