package io.github.riyadmondol2006.idsheetstudio.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.domain.model.PaperSizes
import io.github.riyadmondol2006.idsheetstudio.domain.model.PhotoSizePresets
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** remove.bg output resolution. Preview is free for most accounts; full uses credits. */
enum class RemoveBgSize(val apiValue: String, val title: String, val description: String) {
    AUTO("auto", "Automatic", "Highest resolution your account allows"),
    PREVIEW("preview", "Preview", "Up to 0.25 megapixels · lowest credit cost"),
    FULL("full", "Full resolution", "Up to 25 megapixels · uses full credits"),
}

data class AppSettings(
    val onboardingDone: Boolean = false,
    val hasApiKey: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultUnit: LengthUnit = LengthUnit.MM,
    val defaultPaperId: String = PaperSizes.A4.id,
    val defaultPresetId: String = PhotoSizePresets.DEFAULT_ID,
    val exportDpi: Int = 600,
    val removeBgSize: RemoveBgSize = RemoveBgSize.AUTO,
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheck: Long = 0,
    val skippedVersion: String? = null,
    /** "Later" hides the update popup until this time. */
    val updateSnoozedUntil: Long = 0,
    /** Last version a background notification was shown for (avoids repeats). */
    val lastNotifiedVersion: String? = null,
    val notificationPermissionAsked: Boolean = false,
)

class SettingsRepository(
    private val store: DataStore<Preferences>,
    private val cipher: SecretCipher,
) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val apiKey = stringPreferencesKey("removebg_api_key_enc")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val unit = stringPreferencesKey("default_unit")
        val paper = stringPreferencesKey("default_paper")
        val preset = stringPreferencesKey("default_preset")
        val dpi = intPreferencesKey("export_dpi")
        val size = stringPreferencesKey("removebg_size")
        val autoUpdate = booleanPreferencesKey("auto_update_check")
        val lastCheck = longPreferencesKey("last_update_check")
        val skipped = stringPreferencesKey("skipped_version")
        val snoozed = longPreferencesKey("update_snoozed_until")
        val notified = stringPreferencesKey("last_notified_version")
        val notifAsked = booleanPreferencesKey("notification_permission_asked")
    }

    val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            onboardingDone = p[Keys.onboarding] ?: false,
            hasApiKey = !p[Keys.apiKey].isNullOrEmpty(),
            themeMode = enumOr(p[Keys.theme], ThemeMode.SYSTEM),
            dynamicColor = p[Keys.dynamic] ?: true,
            defaultUnit = enumOr(p[Keys.unit], LengthUnit.MM),
            defaultPaperId = p[Keys.paper] ?: PaperSizes.A4.id,
            defaultPresetId = p[Keys.preset] ?: PhotoSizePresets.DEFAULT_ID,
            exportDpi = p[Keys.dpi] ?: 600,
            removeBgSize = enumOr(p[Keys.size], RemoveBgSize.AUTO),
            autoUpdateCheck = p[Keys.autoUpdate] ?: true,
            lastUpdateCheck = p[Keys.lastCheck] ?: 0,
            skippedVersion = p[Keys.skipped],
            updateSnoozedUntil = p[Keys.snoozed] ?: 0,
            lastNotifiedVersion = p[Keys.notified],
            notificationPermissionAsked = p[Keys.notifAsked] ?: false,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /** Returns the decrypted remove.bg API key, or null when none is saved or it can't be decrypted. */
    suspend fun apiKey(): String? {
        val encoded = store.data.first()[Keys.apiKey] ?: return null
        return cipher.decrypt(encoded)?.takeIf { it.isNotBlank() }
    }

    suspend fun setApiKey(key: String?) {
        store.edit { p ->
            val trimmed = key?.trim()
            if (trimmed.isNullOrEmpty()) p.remove(Keys.apiKey) else p[Keys.apiKey] = cipher.encrypt(trimmed)
        }
    }

    suspend fun completeOnboarding() = store.edit { it[Keys.onboarding] = true }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamic] = enabled }
    suspend fun setDefaultUnit(unit: LengthUnit) = store.edit { it[Keys.unit] = unit.name }
    suspend fun setDefaultPaper(id: String) = store.edit { it[Keys.paper] = id }
    suspend fun setDefaultPreset(id: String) = store.edit { it[Keys.preset] = id }
    suspend fun setExportDpi(dpi: Int) = store.edit { it[Keys.dpi] = dpi.coerceIn(150, 1200) }
    suspend fun setRemoveBgSize(size: RemoveBgSize) = store.edit { it[Keys.size] = size.name }
    suspend fun setAutoUpdateCheck(enabled: Boolean) = store.edit { it[Keys.autoUpdate] = enabled }
    suspend fun setLastUpdateCheck(time: Long) = store.edit { it[Keys.lastCheck] = time }
    suspend fun setSkippedVersion(version: String?) = store.edit { p ->
        if (version == null) p.remove(Keys.skipped) else p[Keys.skipped] = version
    }

    suspend fun snoozeUpdates(until: Long) = store.edit { it[Keys.snoozed] = until }
    suspend fun setLastNotifiedVersion(version: String) = store.edit { it[Keys.notified] = version }
    suspend fun setNotificationPermissionAsked() = store.edit { it[Keys.notifAsked] = true }

    private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
        value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default
}
