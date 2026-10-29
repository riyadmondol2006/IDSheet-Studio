package io.github.riyadmondol2006.idsheetstudio.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Transaction
    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<ProjectWithPhotos>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectWithPhotos?>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectWithPhotos?

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhoto(id: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET updated_at = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("UPDATE projects SET name = :name, updated_at = :time WHERE id = :id")
    suspend fun rename(id: String, name: String, time: Long)

    @Query("UPDATE projects SET settings_json = :json, updated_at = :time WHERE id = :id")
    suspend fun updateSettings(id: String, json: String, time: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProject(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPhotos(photos: List<PhotoEntity>)

    @Update
    suspend fun updatePhoto(photo: PhotoEntity)

    /** Writes only user-editable fields, so concurrent background-removal results survive. */
    @Query(
        """UPDATE photos SET order_index = :orderIndex, preset_id = :presetId, width_mm = :widthMm,
        height_mm = :heightMm, quantity = :quantity, zoom = :zoom, offset_x = :offsetX, offset_y = :offsetY,
        rotation = :rotation, use_cutout = (:useCutout AND cutout_path IS NOT NULL), background_color = :backgroundColor,
        label = :label, revision = revision + 1 WHERE id = :id""",
    )
    suspend fun updateEditable(
        id: String, orderIndex: Int, presetId: String, widthMm: Double, heightMm: Double, quantity: Int,
        zoom: Double, offsetX: Double, offsetY: Double, rotation: Double, useCutout: Boolean,
        backgroundColor: Long, label: String,
    )

    @Query("UPDATE photos SET background_state = :state, background_error = :error WHERE id = :id")
    suspend fun setBackgroundState(id: String, state: String, error: String?)

    @Query(
        """UPDATE photos SET cutout_path = :path, use_cutout = 1, background_state = 'REMOVED',
        background_error = NULL, revision = revision + 1 WHERE id = :id""",
    )
    suspend fun setCutout(id: String, path: String)

    /** Atomic, clamped quantity change so rapid taps never lose increments. */
    @Query("UPDATE photos SET quantity = MIN(MAX(quantity + :delta, 0), :max) WHERE id = :id")
    suspend fun adjustQuantity(id: String, delta: Int, max: Int)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: String)

    @Query("SELECT COALESCE(MAX(order_index), -1) FROM photos WHERE project_id = :projectId")
    suspend fun maxOrderIndex(projectId: String): Int

    @Query("UPDATE photos SET background_state = 'ORIGINAL', background_error = NULL WHERE background_state = 'PROCESSING'")
    suspend fun resetStaleProcessing()
}
