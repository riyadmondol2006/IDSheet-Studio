package io.github.riyadmondol2006.idsheetstudio.data

import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgException
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgService
import io.github.riyadmondol2006.idsheetstudio.data.settings.SettingsRepository
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.image.BitmapIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs remove.bg requests in the application scope so they finish even if the user leaves the
 * screen. Requests are serialised to respect the API's rate limits.
 */
class BackgroundRemover(
    private val scope: CoroutineScope,
    private val projects: ProjectRepository,
    private val settings: SettingsRepository,
    private val service: RemoveBgService,
) {
    private val mutex = Mutex()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** User-facing failure messages. */
    val errors: SharedFlow<String> = _errors

    suspend fun hasApiKey(): Boolean = settings.apiKey() != null

    fun enqueue(photos: List<PhotoItem>) {
        scope.launch {
            // Marked in order inside the same coroutine, so a quick failure can't be overwritten
            // by a late "processing" write.
            photos.forEach { projects.setBackgroundState(it.id, BackgroundState.PROCESSING) }
            for ((index, photo) in photos.withIndex()) {
                val fatal = process(photo)
                if (fatal) {
                    // Errors such as an invalid key or no credits would repeat for every photo.
                    photos.drop(index + 1).forEach { projects.setBackgroundState(it.id, BackgroundState.ORIGINAL) }
                    break
                }
            }
        }
    }

    /** Processes one photo; returns true when the failure makes the remaining batch pointless. */
    private suspend fun process(photo: PhotoItem): Boolean = mutex.withLock {
        val key = settings.apiKey()
        if (key == null) {
            projects.setBackgroundState(photo.id, BackgroundState.FAILED, "No remove.bg API key")
            _errors.tryEmit("Add your remove.bg API key in Settings to remove backgrounds.")
            return true
        }
        try {
            val jpeg = withContext(Dispatchers.IO) { BitmapIO.encodeForUpload(photo.originalPath, RemoveBgService.UPLOAD_MAX_DIMENSION) }
            val png = service.removeBackground(key, jpeg, settings.current().removeBgSize.apiValue)
            projects.saveCutout(photo.id, png)
            false
        } catch (e: CancellationException) {
            projects.setBackgroundState(photo.id, BackgroundState.ORIGINAL)
            throw e
        } catch (e: RemoveBgException) {
            projects.setBackgroundState(photo.id, BackgroundState.FAILED, e.message)
            _errors.tryEmit(e.message ?: "Background removal failed.")
            e.kind == RemoveBgException.Kind.INVALID_KEY || e.kind == RemoveBgException.Kind.NO_CREDITS
        } catch (e: Exception) {
            projects.setBackgroundState(photo.id, BackgroundState.FAILED, e.message)
            _errors.tryEmit("Background removal failed: ${e.message}")
            false
        }
    }
}
