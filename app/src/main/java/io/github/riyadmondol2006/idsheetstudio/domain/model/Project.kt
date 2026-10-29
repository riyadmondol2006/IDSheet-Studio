package io.github.riyadmondol2006.idsheetstudio.domain.model

data class Project(
    val id: String,
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val settings: SheetSettings,
    val photos: List<PhotoItem>,
) {
    val totalCopies: Int get() = photos.sumOf { it.quantity }
}

data class ProjectSummary(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val photoCount: Int,
    val copyCount: Int,
    val coverPhoto: PhotoItem?,
    val paperName: String,
)
