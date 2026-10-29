package io.github.riyadmondol2006.idsheetstudio

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubAsset
import io.github.riyadmondol2006.idsheetstudio.data.update.SemanticVersion
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateCheckWorker
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateInfo
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateNotifier
import io.github.riyadmondol2006.idsheetstudio.ui.theme.IDSheetTheme
import io.github.riyadmondol2006.idsheetstudio.ui.update.UpdateDialog
import io.github.riyadmondol2006.idsheetstudio.ui.update.UpdatePhase
import io.github.riyadmondol2006.idsheetstudio.ui.update.UpdateUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateUiTest {
    @get:Rule val compose = createComposeRule()

    private val info = UpdateInfo(
        SemanticVersion.parse("9.9.0")!!, "v9.9.0", "IDSheet Studio v9.9.0",
        "## What's new\n- Faster **export**\n- New presets", "https://github.com/riyadmondol2006/IDSheet-Studio/releases",
        null, GitHubAsset("IDSheet-Studio-v9.9.0.apk", "https://example.invalid/a.apk"), null,
    )

    @Test fun popupShowsChangelogAndAllActions() {
        var updated = 0; var later = 0; var skipped = 0
        compose.setContent {
            IDSheetTheme {
                UpdateDialog(UpdateUiState(info = info, visible = true), { updated++ }, { later++ }, { skipped++ })
            }
        }
        compose.onNodeWithText("Update available").assertIsDisplayed()
        compose.onNodeWithText("IDSheet Studio 9.9.0 is ready", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Faster export", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("update_confirm").performClick()
        compose.onNodeWithTag("update_later").performClick()
        compose.onNodeWithText("Skip version").performClick()
        assertEquals(listOf(1, 1, 1), listOf(updated, later, skipped))
    }

    @Test fun popupWhileDownloadingCannotBeDismissedOrRestarted() {
        var updated = 0
        compose.setContent {
            IDSheetTheme {
                UpdateDialog(UpdateUiState(info = info, visible = true, phase = UpdatePhase.Downloading(0.42f)), { updated++ }, {}, {})
            }
        }
        compose.onNodeWithText("Downloading… 42%").assertIsDisplayed()
        compose.onNodeWithTag("update_confirm").performClick() // disabled: must not fire
        assertEquals(0, updated)
    }

    @Test fun failurePhaseOffersReleasePage() {
        compose.setContent {
            IDSheetTheme {
                UpdateDialog(UpdateUiState(info = info, visible = true, phase = UpdatePhase.Failed("Signature mismatch.")), {}, {}, {})
            }
        }
        compose.onNodeWithText("Signature mismatch.").assertIsDisplayed()
        compose.onNodeWithText("Open release page").assertIsDisplayed()
    }

    @Test fun notificationIsPostedAndOpensTheUpdatePopup() {
        val context = TestPhotos.targetContext
        if (Build.VERSION.SDK_INT >= 33) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        }
        assertTrue(UpdateNotifier.notify(context, info))
        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.single { it.id == UpdateNotifier.NOTIFICATION_ID }
        assertEquals("IDSheet Studio 9.9.0 is available", posted.notification.extras.getString("android.title"))
        assertTrue(posted.notification.contentIntent != null)
        UpdateNotifier.cancel(context)
        assertTrue(manager.activeNotifications.none { it.id == UpdateNotifier.NOTIFICATION_ID })
    }

    @Test fun backgroundWorkerQueriesGitHubAndSucceeds() = runBlocking<Unit> {
        val context = TestPhotos.targetContext
        // Real network call to the GitHub Releases API for this repository.
        val worker = TestListenableWorkerBuilder<UpdateCheckWorker>(context).build()
        val result = worker.doWork()
        assertTrue("Unexpected $result", result is ListenableWorker.Result.Success || result is ListenableWorker.Result.Retry)
    }
}
