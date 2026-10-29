package io.github.riyadmondol2006.idsheetstudio.data.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    /** [io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings] as JSON. */
    @ColumnInfo(name = "settings_json") val settingsJson: String,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("project_id")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "project_id") val projectId: String,
    @ColumnInfo(name = "order_index") val orderIndex: Int,
    /** Paths are stored relative to the app's files directory. */
    @ColumnInfo(name = "original_path") val originalPath: String,
    @ColumnInfo(name = "cutout_path") val cutoutPath: String?,
    @ColumnInfo(name = "source_width") val sourceWidth: Int,
    @ColumnInfo(name = "source_height") val sourceHeight: Int,
    @ColumnInfo(name = "preset_id") val presetId: String,
    @ColumnInfo(name = "width_mm") val widthMm: Double,
    @ColumnInfo(name = "height_mm") val heightMm: Double,
    val quantity: Int,
    val zoom: Double,
    @ColumnInfo(name = "offset_x") val offsetX: Double,
    @ColumnInfo(name = "offset_y") val offsetY: Double,
    val rotation: Double,
    @ColumnInfo(name = "use_cutout") val useCutout: Boolean,
    @ColumnInfo(name = "background_color") val backgroundColor: Long,
    @ColumnInfo(name = "background_state") val backgroundState: String,
    @ColumnInfo(name = "background_error") val backgroundError: String?,
    val label: String,
    val revision: Long,
)

data class ProjectWithPhotos(
    @Embedded val project: ProjectEntity,
    @Relation(parentColumn = "id", entityColumn = "project_id")
    val photos: List<PhotoEntity>,
)
