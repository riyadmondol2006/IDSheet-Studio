package io.github.riyadmondol2006.idsheetstudio.ui.export

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.riyadmondol2006.idsheetstudio.AppContainer
import io.github.riyadmondol2006.idsheetstudio.pdf.ExportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ExportState(
    val progress: Float = 0f,
    val result: ExportResult? = null,
    val pages: List<Bitmap> = emptyList(),
    val error: String? = null,
    val projectName: String = "",
    val fileSizeBytes: Long = 0,
) {
    val working: Boolean get() = result == null && error == null
}

class ExportViewModel(
    private val container: AppContainer,
    private val projectId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(ExportState())
    val state: StateFlow<ExportState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var job: Job? = null

    init {
        export()
    }

    fun export() {
        job?.cancel()
        _state.value = ExportState()
        job = viewModelScope.launch {
            try {
                val project = container.projects.getProject(projectId) ?: error("This sheet no longer exists.")
                _state.update { it.copy(projectName = project.name) }
                val dpi = container.settings.current().exportDpi
                val result = container.exporter.export(project, dpi) { p -> _state.update { it.copy(progress = p) } }
                val pages = withContext(Dispatchers.IO) { renderPreview(result) }
                _state.update { it.copy(result = result, pages = pages, progress = 1f, fileSizeBytes = result.file.length()) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "Export failed.") }
            }
        }
    }

    /** Renders each page of the generated PDF with the platform renderer, as a final check. */
    private fun renderPreview(result: ExportResult): List<Bitmap> {
        val pfd = ParcelFileDescriptor.open(result.file, ParcelFileDescriptor.MODE_READ_ONLY)
        return PdfRenderer(pfd).use { renderer ->
            // Previews are capped to keep memory bounded; the PDF itself always has every page.
            (0 until minOf(renderer.pageCount, MAX_PREVIEW_PAGES)).map { index ->
                renderer.openPage(index).use { page ->
                    val width = 900
                    val height = (width.toFloat() * page.height / page.width).toInt()
                    val bitmap = createBitmap(width, height)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }

    fun saveTo(uri: Uri) = viewModelScope.launch {
        val file = _state.value.result?.file ?: return@launch
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                container.appContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: error("Cannot open destination")
            }.isSuccess
        }
        _messages.emit(if (ok) "PDF saved" else "Couldn't save the PDF")
    }

    companion object {
        const val MAX_PREVIEW_PAGES = 20
    }
}
