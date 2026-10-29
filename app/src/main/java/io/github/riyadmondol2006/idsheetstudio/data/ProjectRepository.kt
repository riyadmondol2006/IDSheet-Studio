package io.github.riyadmondol2006.idsheetstudio.data

import android.content.Context
import android.net.Uri
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm
import io.github.riyadmondol2006.idsheetstudio.data.db.PhotoEntity
import io.github.riyadmondol2006.idsheetstudio.data.db.ProjectDao
import io.github.riyadmondol2006.idsheetstudio.data.db.ProjectEntity
import io.github.riyadmondol2006.idsheetstudio.data.db.ProjectWithPhotos
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoItem
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoTransform
import io.github.riyadmondol2006.idsheetstudio.domain.model.Project
import io.github.riyadmondol2006.idsheetstudio.domain.model.ProjectSummary
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.image.BitmapIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

data class ImportOutcome(val imported: Int, val failed: Int)

/**
 * Persists projects in Room and their image files under `files/projects/<projectId>/`.
 * Everything needed to reopen, edit and export a project is stored locally, so projects work
 * fully offline once backgrounds have been processed.
 */
class ProjectRepository(
    private val context: Context,
    private val dao: ProjectDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val filesRoot: File get() = context.filesDir

    fun observeSummaries(): Flow<List<ProjectSummary>> = dao.observeAll().map { list ->
        list.map { row ->
            val project = row.toDomain()
            ProjectSummary(
                id = project.id,
                name = project.name,
                updatedAt = project.updatedAt,
                photoCount = project.photos.size,
                copyCount = project.totalCopies,
                coverPhoto = project.photos.minByOrNull { it.orderIndex },
                paperName = project.settings.paperName,
            )
        }
    }

    fun observeProject(id: String): Flow<Project?> = dao.observe(id).map { it?.toDomain() }

    suspend fun getProject(id: String): Project? = dao.get(id)?.toDomain()

    suspend fun createProject(name: String, settings: SheetSettings): String {
        val id = UUID.randomUUID().toString()
        val now = clock()
        dao.insertProject(ProjectEntity(id, name, now, now, json.encodeToString(SheetSettings.serializer(), settings)))
        projectDir(id).mkdirs()
        return id
    }

    suspend fun rename(id: String, name: String) = dao.rename(id, name.trim().ifEmpty { "Untitled sheet" }, clock())

    suspend fun updateSettings(id: String, settings: SheetSettings) =
        dao.updateSettings(id, json.encodeToString(SheetSettings.serializer(), settings), clock())

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteProject(id)
        projectDir(id).deleteRecursively()
    }

    /** Creates an independent copy of a project including all image files. */
    suspend fun duplicate(id: String): String? = withContext(Dispatchers.IO) {
        val source = dao.get(id) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val now = clock()
        val newDir = projectDir(newId).apply { mkdirs() }
        dao.insertProject(source.project.copy(id = newId, name = "${source.project.name} (copy)", createdAt = now, updatedAt = now))
        val photos = source.photos.map { photo ->
            val photoId = UUID.randomUUID().toString()
            photo.copy(
                id = photoId,
                projectId = newId,
                originalPath = copyInto(photo.originalPath, newDir, "orig_$photoId.jpg"),
                cutoutPath = photo.cutoutPath?.let { copyInto(it, newDir, "cutout_$photoId.png") },
                backgroundState = if (photo.backgroundState == BackgroundState.PROCESSING.name) BackgroundState.ORIGINAL.name else photo.backgroundState,
            )
        }
        dao.upsertPhotos(photos)
        // Manual positions refer to old photo ids; they are dropped because ids changed.
        val settings = decodeSettings(source.project.settingsJson).copy(manualPositions = emptyMap())
        dao.updateSettings(newId, json.encodeToString(SheetSettings.serializer(), settings), now)
        newId
    }

    /** Imports images from the system photo picker into the project. */
    suspend fun importPhotos(projectId: String, uris: List<Uri>, defaultPresetId: String): ImportOutcome = withContext(Dispatchers.IO) {
        val preset = PhotoSizePresets.byId(defaultPresetId) ?: PhotoSizePresets.default
        var order = dao.maxOrderIndex(projectId) + 1
        var failed = 0
        val dir = projectDir(projectId).apply { mkdirs() }
        val entities = mutableListOf<PhotoEntity>()
        for (uri in uris) {
            val photoId = UUID.randomUUID().toString()
            val dest = File(dir, "orig_$photoId.jpg")
            try {
                val image = BitmapIO.import(context.contentResolver, uri, dest)
                entities += PhotoEntity(
                    id = photoId,
                    projectId = projectId,
                    orderIndex = order++,
                    originalPath = relative(dest),
                    cutoutPath = null,
                    sourceWidth = image.width,
                    sourceHeight = image.height,
                    presetId = preset.id,
                    widthMm = preset.size.width,
                    heightMm = preset.size.height,
                    quantity = 4,
                    zoom = 1.0,
                    offsetX = 0.0,
                    offsetY = 0.0,
                    rotation = 0.0,
                    useCutout = false,
                    backgroundColor = PhotoItem.WHITE,
                    backgroundState = BackgroundState.ORIGINAL.name,
                    backgroundError = null,
                    label = "Photo ${order}",
                    revision = 0,
                )
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                dest.delete()
                failed++
            }
        }
        if (entities.isNotEmpty()) {
            dao.upsertPhotos(entities)
            dao.touch(projectId, clock())
        }
        ImportOutcome(entities.size, failed)
    }

    /** Saves edits to a photo (size, quantity, crop, background options). */
    suspend fun updatePhoto(photo: PhotoItem) {
        dao.getPhoto(photo.id) ?: return
        dao.updateEditable(
            id = photo.id,
            orderIndex = photo.orderIndex,
            presetId = photo.presetId,
            widthMm = photo.size.width,
            heightMm = photo.size.height,
            quantity = photo.quantity.coerceIn(0, PhotoItem.MAX_QUANTITY),
            zoom = photo.transform.zoom,
            offsetX = photo.transform.offsetX,
            offsetY = photo.transform.offsetY,
            rotation = photo.transform.rotation,
            useCutout = photo.useCutout,
            backgroundColor = photo.backgroundColor or 0xFF000000,
            label = photo.label,
        )
        dao.touch(photo.projectId, clock())
    }

    suspend fun adjustQuantity(photoId: String, delta: Int) {
        val existing = dao.getPhoto(photoId) ?: return
        dao.adjustQuantity(photoId, delta, PhotoItem.MAX_QUANTITY)
        dao.touch(existing.projectId, clock())
    }

    suspend fun setBackgroundState(photoId: String, state: BackgroundState, error: String? = null) {
        dao.setBackgroundState(photoId, state.name, error)
    }

    /** Stores a background-removed PNG and switches the photo to use it. */
    suspend fun saveCutout(photoId: String, png: ByteArray) = withContext(Dispatchers.IO) {
        val existing = dao.getPhoto(photoId) ?: return@withContext
        val dir = projectDir(existing.projectId)
        val file = File(dir, "cutout_${photoId}_${clock()}.png")
        file.writeBytes(png)
        dao.setCutout(photoId, relative(file))
        existing.cutoutPath?.let { resolve(it).delete() }
        dao.touch(existing.projectId, clock())
    }

    /**
     * Adds the same photo again in another size (e.g. passport + stamp copies of one portrait on
     * one sheet). Crop, background and colour carry over; the copy gets its own quantity.
     */
    suspend fun addSize(photoId: String, presetId: String, size: SizeMm, quantity: Int = 4): String? {
        val presetName = PhotoSizePresets.byId(presetId)?.name ?: "Custom"
        // Inserted with its new size in one write, so the copy never appears with the old size.
        return duplicatePhoto(photoId) { copy ->
            copy.copy(
                presetId = presetId,
                widthMm = size.width,
                heightMm = size.height,
                quantity = quantity.coerceIn(0, PhotoItem.MAX_QUANTITY),
                label = "${copy.label.substringBefore(" · ")} · $presetName",
            )
        }
    }

    suspend fun duplicatePhoto(
        photoId: String,
        adjust: (PhotoEntity) -> PhotoEntity = { it.copy(label = it.label + " (copy)") },
    ): String? = withContext(Dispatchers.IO) {
        val existing = dao.getPhoto(photoId) ?: return@withContext null
        val newId = UUID.randomUUID().toString()
        val dir = projectDir(existing.projectId)
        val copy = existing.copy(
            id = newId,
            orderIndex = dao.maxOrderIndex(existing.projectId) + 1,
            originalPath = copyInto(existing.originalPath, dir, "orig_$newId.jpg"),
            cutoutPath = existing.cutoutPath?.let { copyInto(it, dir, "cutout_$newId.png") },
            backgroundState = if (existing.backgroundState == BackgroundState.PROCESSING.name) BackgroundState.ORIGINAL.name else existing.backgroundState,
            revision = 0,
        )
        dao.upsertPhotos(listOf(adjust(copy)))
        dao.touch(existing.projectId, clock())
        newId
    }

    suspend fun deletePhoto(photoId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getPhoto(photoId) ?: return@withContext
        dao.deletePhoto(photoId)
        resolve(existing.originalPath).delete()
        existing.cutoutPath?.let { resolve(it).delete() }
        dao.touch(existing.projectId, clock())
    }

    suspend fun resetStaleProcessing() = dao.resetStaleProcessing()

    fun projectDir(id: String) = File(filesRoot, "projects/$id")

    private fun relative(file: File): String = file.relativeTo(filesRoot).path
    private fun resolve(path: String): File = File(filesRoot, path)

    private fun copyInto(relativePath: String, dir: File, name: String): String {
        val dest = File(dir, name)
        resolve(relativePath).copyTo(dest, overwrite = true)
        return relative(dest)
    }

    private fun decodeSettings(text: String): SheetSettings =
        runCatching { json.decodeFromString(SheetSettings.serializer(), text) }.getOrDefault(SheetSettings())

    private fun ProjectWithPhotos.toDomain(): Project = Project(
        id = project.id,
        name = project.name,
        createdAt = project.createdAt,
        updatedAt = project.updatedAt,
        settings = decodeSettings(project.settingsJson),
        photos = photos.sortedBy { it.orderIndex }.map { it.toDomain() },
    )

    private fun PhotoEntity.toDomain(): PhotoItem = PhotoItem(
        id = id,
        projectId = projectId,
        orderIndex = orderIndex,
        originalPath = resolve(originalPath).absolutePath,
        cutoutPath = cutoutPath?.let { resolve(it).absolutePath },
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        presetId = presetId,
        size = SizeMm(widthMm, heightMm),
        quantity = quantity,
        transform = PhotoTransform(zoom, offsetX, offsetY, rotation),
        useCutout = useCutout && cutoutPath != null,
        backgroundColor = backgroundColor,
        backgroundState = runCatching { BackgroundState.valueOf(backgroundState) }.getOrDefault(BackgroundState.ORIGINAL),
        backgroundError = backgroundError,
        label = label,
        revision = revision,
    )
}
