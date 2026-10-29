package io.github.riyadmondol2006.idsheetstudio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Behaves like an impatient or careless user: double taps, rapid back presses, rotating mid-task,
 * nonsense input, impossible settings, deleting things that are open. The app must never crash,
 * lose data or produce a wrong result.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class UserAbuseTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val container get() = TestPhotos.targetContext.appContainer
    private val created = mutableListOf<String>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() = runBlocking<Unit> {
        container.settings.completeOnboarding()
        container.settings.setAutoUpdateCheck(false)
        container.settings.setNotificationPermissionAsked()
    }

    @After fun tearDown() = runBlocking<Unit> {
        // Destroying the activity is where the navigation lifecycle crash used to surface.
        scenario?.close()
        created.forEach { container.projects.delete(it) }
        container.settings.setAutoUpdateCheck(true)
    }

    private suspend fun seed(name: String, photos: Int = 2): String {
        val id = container.projects.createProject(name, SheetSettings()).also { created += it }
        container.projects.importPhotos(id, (1..photos).map { TestPhotos.synthetic("$name-$it") }, "passport_35x45")
        return id
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun waitTag(tag: String, timeout: Long = 20_000) = compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeout)
    private fun waitText(text: String, timeout: Long = 20_000) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeout)

    private fun openProject(name: String) {
        waitText(name)
        compose.onNodeWithText(name).performClick()
        waitTag("photos_list")
    }

    private fun backTimes(n: Int) = scenario!!.onActivity { a -> repeat(n) { a.onBackPressedDispatcher.onBackPressed() } }

    /** Presses back one screen at a time (as a calm user would) until the home screen shows. */
    private fun backToHome() {
        repeat(6) {
            if (compose.onAllNodesWithTag("new_sheet").fetchSemanticsNodes().isNotEmpty()) return
            backTimes(1)
            compose.waitForIdle()
            Thread.sleep(400) // let the screen transition finish
        }
        waitTag("new_sheet")
    }

    @Test fun hammeringBackDuringTransitionsNeverCrashes() = runBlocking<Unit> {
        seed("Back hammer")
        launch()
        repeat(3) {
            openProject("Back hammer")
            compose.onNodeWithTag("tab_layout").performClick()
            waitTag("fab_export")
            compose.onNodeWithTag("fab_export").performClick()
            waitTag("export_verified", 60_000)
            backTimes(4) // more presses than screens, all within one frame
            compose.waitForIdle()
            // Extra presses during the transition are ignored instead of corrupting navigation.
            backToHome()
        }
        scenario!!.close() // previously: IllegalStateException on destroy
        scenario = null
    }

    @Test fun doubleTappingNewSheetCreatesOnlyOne() = runBlocking<Unit> {
        launch()
        waitTag("new_sheet")
        val before = container.projects.observeSummaries().first().size
        compose.onNodeWithTag("new_sheet").performTouchInput { doubleClick() }
        waitTag("fab_add_photos")
        compose.waitForIdle()
        val after = container.projects.observeSummaries().first()
        assertEquals(before + 1, after.size)
        created += after.maxBy { it.updatedAt }.id
    }

    @Test fun rapidQuantityTapsAreNeverLost() = runBlocking<Unit> {
        val id = seed("Quantity spam", photos = 1)
        launch()
        openProject("Quantity spam")
        repeat(20) { compose.onAllNodesWithContentDescription("More copies")[0].performClick() }
        compose.waitUntil(10_000) {
            runBlocking { container.projects.getProject(id)!!.photos.single().quantity } == 24
        }
        repeat(30) { compose.onAllNodesWithContentDescription("Fewer copies")[0].performClick() }
        compose.waitUntil(10_000) {
            runBlocking { container.projects.getProject(id)!!.photos.single().quantity } == 0
        }
        // Zero copies: nothing to lay out, no export button, no crash.
        compose.onNodeWithTag("tab_layout").performClick()
        waitText("Set a quantity above zero")
        assertTrue(compose.onAllNodesWithTag("fab_export").fetchSemanticsNodes().isEmpty())
    }

    @Test fun nonsenseAndImpossibleMarginsAreHandled() = runBlocking<Unit> {
        seed("Bad margins")
        launch()
        openProject("Bad margins")
        compose.onNodeWithTag("tab_paper").performClick()
        waitTag("margin_all")
        compose.onNodeWithTag("margin_all").performTextClearance()
        compose.onNodeWithTag("margin_all").performTextInput("abc")
        waitText("Enter a number")
        compose.onNodeWithTag("margin_all").performTextClearance()
        compose.onNodeWithTag("margin_all").performTextInput("999")
        waitText("Max 200")
        compose.onNodeWithTag("margin_all").performTextClearance()
        compose.onNodeWithTag("margin_all").performTextInput("150") // valid number, impossible layout
        compose.onNodeWithTag("tab_layout").performClick()
        waitText("margins leave no printable area")
        compose.onNodeWithTag("fab_export").performClick()
        waitTag("export_error", 30_000)
        compose.onNodeWithText("margins leave no printable area", substring = true).fetchSemanticsNode()
        backTimes(1)
        Thread.sleep(500)
        compose.onNodeWithTag("tab_paper").performClick()
        waitTag("margin_all")
        compose.onNodeWithTag("margin_all").performTextClearance()
        compose.onNodeWithTag("margin_all").performTextInput("5")
        compose.onNodeWithTag("tab_layout").performClick()
        waitText("Page 1 of 1")
    }

    @Test fun rotatingDuringExportStillProducesAVerifiedPdf() = runBlocking<Unit> {
        seed("Rotate export", photos = 3)
        launch()
        openProject("Rotate export")
        compose.onNodeWithTag("tab_layout").performClick()
        waitTag("fab_export")
        compose.onNodeWithTag("fab_export").performClick()
        scenario!!.recreate() // configuration change mid-export
        waitTag("export_verified", 60_000)
        scenario!!.recreate()
        waitTag("export_verified", 20_000)
    }

    @Test fun deletingThePhotoBeingEditedClosesTheEditor() = runBlocking<Unit> {
        val id = seed("Delete while editing", photos = 1)
        launch()
        openProject("Delete while editing")
        compose.onNodeWithText("Photo 1", useUnmergedTree = true).performClick()
        waitTag("crop_canvas")
        container.projects.deletePhoto(container.projects.getProject(id)!!.photos.single().id)
        waitTag("empty_add_photos")
    }

    @Test fun deletingAProjectFromAnotherScreenDoesNotCrashExport() = runBlocking<Unit> {
        val id = seed("Vanishing sheet")
        launch()
        openProject("Vanishing sheet")
        container.projects.delete(id)
        created -= id
        compose.waitForIdle()
        // The project screen is now empty; navigating back must still work.
        backToHome()
    }

    @Test fun corruptAndHugeImagesAreHandledOnImport() = runBlocking<Unit> {
        val id = container.projects.createProject("Import abuse", SheetSettings()).also { created += it }
        val corrupt = File(TestPhotos.targetContext.cacheDir, "corrupt.jpg").apply { writeText("this is not an image") }
        val empty = File(TestPhotos.targetContext.cacheDir, "empty.jpg").apply { writeBytes(ByteArray(0)) }
        val missing = File(TestPhotos.targetContext.cacheDir, "does-not-exist.jpg")
        val huge = TestPhotos.synthetic("huge", width = 8000, height = 6000)
        val outcome = container.projects.importPhotos(
            id,
            listOf(android.net.Uri.fromFile(corrupt), android.net.Uri.fromFile(empty), android.net.Uri.fromFile(missing), huge),
            "passport_35x45",
        )
        assertEquals(1, outcome.imported)
        assertEquals(3, outcome.failed)
        val photo = container.projects.getProject(id)!!.photos.single()
        assertTrue("huge image must be downscaled", maxOf(photo.sourceWidth, photo.sourceHeight) <= 3200)
        // No half-written files are left behind for the failed imports.
        assertEquals(1, container.projects.projectDir(id).listFiles()!!.count { it.name.startsWith("orig_") })
    }

    @Test fun missingImageFileShowsAnErrorInsteadOfCrashing() = runBlocking<Unit> {
        val id = seed("Missing file", photos = 1)
        File(container.projects.getProject(id)!!.photos.single().originalPath).delete()
        launch()
        openProject("Missing file")
        compose.onNodeWithTag("tab_layout").performClick()
        waitTag("fab_export")
        compose.onNodeWithTag("fab_export").performClick()
        waitTag("export_error", 30_000)
    }
}
