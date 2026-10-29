package io.github.riyadmondol2006.idsheetstudio.domain.model

import io.github.riyadmondol2006.idsheetstudio.core.MM_PER_INCH
import io.github.riyadmondol2006.idsheetstudio.core.SizeMm

enum class PresetCategory(val title: String) {
    PASSPORT("Passport"),
    VISA("Visa"),
    ID("ID & documents"),
    STAMP("Stamp"),
    COUNTRY("Country specific"),
}

/**
 * A photo size preset. Sizes are the commonly published values; official requirements change,
 * so the UI reminds users to confirm them with the issuing authority.
 */
data class PhotoSizePreset(
    val id: String,
    val name: String,
    val category: PresetCategory,
    val size: SizeMm,
    val note: String? = null,
)

object PhotoSizePresets {
    private fun mm(w: Double, h: Double) = SizeMm(w, h)
    private fun inch(w: Double, h: Double) = SizeMm(w * MM_PER_INCH, h * MM_PER_INCH)

    val all: List<PhotoSizePreset> = listOf(
        PhotoSizePreset("passport_35x45", "Passport (ICAO)", PresetCategory.PASSPORT, mm(35.0, 45.0), "Most countries"),
        PhotoSizePreset("passport_us", "US Passport", PresetCategory.PASSPORT, inch(2.0, 2.0), "2 × 2 in"),
        PhotoSizePreset("passport_ca", "Canada Passport", PresetCategory.PASSPORT, mm(50.0, 70.0)),
        PhotoSizePreset("passport_cn", "China Passport", PresetCategory.PASSPORT, mm(33.0, 48.0)),
        PhotoSizePreset("visa_us", "US Visa", PresetCategory.VISA, inch(2.0, 2.0), "2 × 2 in"),
        PhotoSizePreset("visa_schengen", "Schengen Visa", PresetCategory.VISA, mm(35.0, 45.0)),
        PhotoSizePreset("visa_in", "India Visa", PresetCategory.VISA, inch(2.0, 2.0), "2 × 2 in"),
        PhotoSizePreset("visa_cn", "China Visa", PresetCategory.VISA, mm(33.0, 48.0)),
        PhotoSizePreset("id_25x35", "ID 1 inch", PresetCategory.ID, mm(25.0, 35.0)),
        PhotoSizePreset("id_35x49", "ID 2 inch", PresetCategory.ID, mm(35.0, 49.0)),
        PhotoSizePreset("id_30x40", "ID card", PresetCategory.ID, mm(30.0, 40.0)),
        PhotoSizePreset("id_40x60", "Document 4 × 6 cm", PresetCategory.ID, mm(40.0, 60.0)),
        PhotoSizePreset("stamp_20x25", "Stamp", PresetCategory.STAMP, mm(20.0, 25.0)),
        PhotoSizePreset("stamp_15x20", "Small stamp", PresetCategory.STAMP, mm(15.0, 20.0)),
        PhotoSizePreset("stamp_25x30", "Large stamp", PresetCategory.STAMP, mm(25.0, 30.0)),
        PhotoSizePreset("country_uk", "United Kingdom", PresetCategory.COUNTRY, mm(35.0, 45.0), "Passport"),
        PhotoSizePreset("country_eu", "European Union", PresetCategory.COUNTRY, mm(35.0, 45.0), "Passport / ID"),
        PhotoSizePreset("country_in", "India", PresetCategory.COUNTRY, mm(35.0, 45.0), "Passport"),
        PhotoSizePreset("country_bd", "Bangladesh", PresetCategory.COUNTRY, mm(45.0, 55.0), "Passport"),
        PhotoSizePreset("country_pk", "Pakistan", PresetCategory.COUNTRY, mm(35.0, 45.0), "Passport"),
        PhotoSizePreset("country_au", "Australia", PresetCategory.COUNTRY, mm(35.0, 45.0), "Passport"),
        PhotoSizePreset("country_jp", "Japan", PresetCategory.COUNTRY, mm(35.0, 45.0), "Passport"),
        PhotoSizePreset("country_my", "Malaysia", PresetCategory.COUNTRY, mm(35.0, 50.0), "Passport"),
        PhotoSizePreset("country_br", "Brazil", PresetCategory.COUNTRY, mm(50.0, 70.0), "Passport"),
        PhotoSizePreset("country_id", "Indonesia", PresetCategory.COUNTRY, mm(40.0, 60.0), "Passport / visa"),
        PhotoSizePreset("country_ca", "Canada", PresetCategory.COUNTRY, mm(50.0, 70.0), "Passport"),
        PhotoSizePreset("country_us", "United States", PresetCategory.COUNTRY, inch(2.0, 2.0), "Passport / visa"),
        PhotoSizePreset("country_cn", "China", PresetCategory.COUNTRY, mm(33.0, 48.0), "Passport / visa"),
    )

    const val DEFAULT_ID = "passport_35x45"
    const val CUSTOM_ID = "custom"

    val default: PhotoSizePreset get() = byId(DEFAULT_ID)!!

    fun byId(id: String?): PhotoSizePreset? = all.firstOrNull { it.id == id }

    fun byCategory(): Map<PresetCategory, List<PhotoSizePreset>> = all.groupBy { it.category }
}
