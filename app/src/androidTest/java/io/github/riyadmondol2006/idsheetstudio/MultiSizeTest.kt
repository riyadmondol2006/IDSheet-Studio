package io.github.riyadmondol2006.idsheetstudio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.pdf.PdfInspector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

/** One portrait printed in two sizes (passport + stamp) on the same page, set up through the UI. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MultiSizeTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val container get() = TestPhotos.targetContext.appContainer
    private lateinit var projectId: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun seed() = runBlocking<Unit> {
        container.settings.completeOnboarding()
        container.settings.setAutoUpdateCheck(false)
        container.settings.setNotificationPermissionAsked()
        projectId = container.projects.createProject("Two sizes sheet", SheetSettings())
        container.projects.importPhotos(projectId, listOf(TestPhotos.synthetic("multi")), "passport_35x45")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() = runBlocking<Unit> {
        scenario?.close()
        container.projects.delete(projectId)
        container.settings.setAutoUpdateCheck(true)
    }

    @Test fun addStampSizeAndExportBothOnOnePage() = runBlocking<Unit> {
        compose.waitUntilAtLeastOneExists(hasText("Two sizes sheet"), 20_000)
        compose.onNodeWithText("Two sizes sheet").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("photo_menu"), 20_000)
        compose.onNodeWithTag("photo_menu").performClick()
        compose.onNodeWithTag("menu_add_size").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("preset_list"), 10_000)
        compose.onNodeWithTag("preset_list").performScrollToNode(hasTestTag("preset_stamp_20x25"))
        compose.onNodeWithTag("preset_stamp_20x25").performClick()

        compose.waitUntil(10_000) { runBlocking { container.projects.getProject(projectId)!!.photos.size } == 2 }
        val photos = container.projects.getProject(projectId)!!.photos
        assertEquals(listOf("passport_35x45", "stamp_20x25"), photos.map { it.presetId })
        assertEquals("Photo 1 · Stamp", photos[1].label)
        compose.waitUntilAtLeastOneExists(hasText("2 sizes", substring = true), 10_000)

        compose.onNodeWithTag("tab_layout").performClick()
        compose.waitUntilAtLeastOneExists(hasText("Page 1 of 1"), 20_000)

        val result = container.exporter.export(container.projects.getProject(projectId)!!, dpi = 300)
        val page = PdfInspector.inspect(result.file.readBytes()).pages.single()
        val sizes = page.images.map { (it.widthMm * 100).roundToInt() to (it.heightMm * 100).roundToInt() }
        assertEquals(4, sizes.count { it == 3500 to 4500 })
        assertEquals(4, sizes.count { it == 2000 to 2500 })
    }
}
