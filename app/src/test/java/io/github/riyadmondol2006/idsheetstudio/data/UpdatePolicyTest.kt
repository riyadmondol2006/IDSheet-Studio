package io.github.riyadmondol2006.idsheetstudio.data

import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubAsset
import io.github.riyadmondol2006.idsheetstudio.data.settings.AppSettings
import io.github.riyadmondol2006.idsheetstudio.data.update.SemanticVersion
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateInfo
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdatePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val now = 1_800_000_000_000L
    private val info = UpdateInfo(
        SemanticVersion.parse("1.3.0")!!, "v1.3.0", "IDSheet Studio v1.3.0", "- New", "https://x", null,
        GitHubAsset("IDSheet-Studio-v1.3.0.apk", "https://x/a.apk"), null,
    )

    @Test fun foregroundChecksAreThrottledHourly() {
        assertTrue(UpdatePolicy.shouldCheckInForeground(AppSettings(lastUpdateCheck = 0), now))
        assertFalse(UpdatePolicy.shouldCheckInForeground(AppSettings(lastUpdateCheck = now - 59 * 60_000), now))
        assertTrue(UpdatePolicy.shouldCheckInForeground(AppSettings(lastUpdateCheck = now - 61 * 60_000), now))
        assertFalse(UpdatePolicy.shouldCheckInForeground(AppSettings(autoUpdateCheck = false), now))
    }

    @Test fun popupRespectsSkipAndSnoozeUnlessUserAsked() {
        assertTrue(UpdatePolicy.shouldShowPopup(info, AppSettings(), now, userInitiated = false))
        val skipped = AppSettings(skippedVersion = "1.3.0")
        assertFalse(UpdatePolicy.shouldShowPopup(info, skipped, now, userInitiated = false))
        assertTrue(UpdatePolicy.shouldShowPopup(info, skipped, now, userInitiated = true))
        val snoozed = AppSettings(updateSnoozedUntil = now + 1000)
        assertFalse(UpdatePolicy.shouldShowPopup(info, snoozed, now, userInitiated = false))
        assertTrue(UpdatePolicy.shouldShowPopup(info, snoozed, now + 1001, userInitiated = false))
        // Skipping 1.2.0 must not hide 1.3.0.
        assertTrue(UpdatePolicy.shouldShowPopup(info, AppSettings(skippedVersion = "1.2.0"), now, userInitiated = false))
    }

    @Test fun notificationsOncePerVersion() {
        assertTrue(UpdatePolicy.shouldNotify(info, AppSettings()))
        assertFalse(UpdatePolicy.shouldNotify(info, AppSettings(lastNotifiedVersion = "1.3.0")))
        assertTrue(UpdatePolicy.shouldNotify(info, AppSettings(lastNotifiedVersion = "1.2.0")))
        assertFalse(UpdatePolicy.shouldNotify(info, AppSettings(skippedVersion = "1.3.0")))
        assertFalse(UpdatePolicy.shouldNotify(info, AppSettings(autoUpdateCheck = false)))
    }
}
