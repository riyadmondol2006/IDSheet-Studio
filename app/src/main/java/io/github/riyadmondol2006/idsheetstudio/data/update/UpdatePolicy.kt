package io.github.riyadmondol2006.idsheetstudio.data.update

import io.github.riyadmondol2006.idsheetstudio.data.settings.AppSettings

/** Pure rules deciding when to check GitHub and when to bother the user about an update. */
object UpdatePolicy {
    /** Foreground checks: at most once an hour (GitHub allows 60 unauthenticated calls/hour). */
    const val FOREGROUND_INTERVAL_MS = 60 * 60 * 1000L

    /** Background checks via WorkManager. */
    const val BACKGROUND_INTERVAL_HOURS = 6L

    /** "Later" hides the popup for a day. */
    const val SNOOZE_MS = 24 * 60 * 60 * 1000L

    fun shouldCheckInForeground(settings: AppSettings, now: Long): Boolean =
        settings.autoUpdateCheck && now - settings.lastUpdateCheck >= FOREGROUND_INTERVAL_MS

    /**
     * Whether to show the popup for [info]. Manual checks and taps on the update notification
     * always show it; automatic checks respect "Skip version" and the "Later" snooze.
     */
    fun shouldShowPopup(info: UpdateInfo, settings: AppSettings, now: Long, userInitiated: Boolean): Boolean = when {
        userInitiated -> true
        info.version.toString() == settings.skippedVersion -> false
        now < settings.updateSnoozedUntil -> false
        else -> true
    }

    /** Background notification: once per version, never for a skipped version. */
    fun shouldNotify(info: UpdateInfo, settings: AppSettings): Boolean =
        settings.autoUpdateCheck &&
            info.version.toString() != settings.skippedVersion &&
            info.version.toString() != settings.lastNotifiedVersion
}
