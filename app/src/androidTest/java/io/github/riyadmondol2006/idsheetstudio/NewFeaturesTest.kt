package io.github.riyadmondol2006.idsheetstudio

import android.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.domain.model.HorizontalAlign
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.domain.model.VerticalAlign
import io.github.riyadmondol2006.idsheetstudio.image.FaceAligner
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Auto-align face, rotation fill, custom background colour and page alignment, through the UI. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class NewFeaturesTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val container get() = TestPhotos.targetContext.appContainer
    private lateinit var projectId: String
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun seed() = runBlocking<Unit> {
        container.settings.completeOnboarding()
        container.settings.setAutoUpdateCheck(false)
        container.settings.setNotificationPermissionAsked()
        projectId = container.projects.createProject("New features sheet", SheetSettings())
        // Real public-domain portrait so the platform face detector has a real face.
        container.projects.importPhotos(projectId, listOf(TestPhotos.realPortrait()), "passport_35x45")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() = runBlocking<Unit> {
        scenario?.close()
        container.projects.delete(projectId)
        container.settings.setAutoUpdateCheck(true)
    }

    private fun photo() = runBlocking { container.projects.getProject(projectId)!!.photos.single() }

    private fun openEditor() {
        compose.waitUntilAtLeastOneExists(hasText("New features sheet"), 20_000)
        compose.onNodeWithText("New features sheet").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("photo_card"), 20_000)
        compose.onNodeWithText("Photo 1", useUnmergedTree = true).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("crop_canvas"), 20_000)
    }

    @Test fun platformDetectorFindsTheTestFace() {
        val face = FaceAligner.detect(photo().originalPath)
        assertNotNull("No face detected in the test portrait", face)
        assertTrue(face!!.eyeDistance > 20)
    }

    @Test fun autoAlignFaceMovesTheCropAndPersists() {
        openEditor()
        compose.onNodeWithTag("auto_align_face").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onNodeWithText("No face found", substring = true).let { runCatching { it.fetchSemanticsNode() }.isSuccess }.not() }
        Thread.sleep(2000)
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("photos_list"), 20_000)
        val t = photo().transform
        assertTrue("Auto-align should change the crop: $t", abs(t.zoom - 1.0) > 0.01 || abs(t.offsetX) > 0.005 || abs(t.offsetY) > 0.005)
        assertEquals(0.0, t.rotation, 0.0)
    }

    @Test fun rotatedPhotoStillFillsTheFrame() {
        openEditor()
        compose.onNodeWithTag("rotate_right").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("photos_list"), 20_000)
        val p = photo()
        assertEquals(90.0, p.transform.rotation, 0.0)
        // Background is pure white; a frame-filling crop has no white corners.
        val bmp = container.renderer.render(p, 350, 450)!!
        for ((x, y) in listOf(1 to 1, 348 to 1, 1 to 448, 348 to 448)) {
            assertNotEquals("corner $x,$y is background", Color.WHITE, bmp.getPixel(x, y))
        }
    }

    @Test fun customHexBackgroundColourIsApplied() {
        openEditor()
        compose.onNodeWithTag("custom_color").performScrollTo().performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("color_hex_field"), 10_000)
        compose.onNodeWithTag("color_hex_field").performTextClearance()
        compose.onNodeWithTag("color_hex_field").performTextInput("#336699")
        compose.onNodeWithTag("color_apply").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("photos_list"), 20_000)
        assertEquals(0xFF336699, photo().backgroundColor)
    }

    @Test fun pageAlignmentAndSpreadEvenlyPersist() {
        compose.waitUntilAtLeastOneExists(hasText("New features sheet"), 20_000)
        compose.onNodeWithText("New features sheet").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("tab_layout"), 20_000)
        compose.onNodeWithTag("tab_layout").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("align_v_center"), 20_000)
        compose.onNodeWithTag("align_v_center").performScrollTo().performClick()
        compose.onNodeWithTag("align_h_end").performScrollTo().performClick()
        compose.onNodeWithTag("spread_evenly").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.projects.getProject(projectId)!!.settings }.let {
                it.verticalAlign == VerticalAlign.CENTER && it.horizontalAlign == HorizontalAlign.END && it.spreadEvenly
            }
        }
        // The aligned layout still exports with exact, verified geometry.
        val result = runBlocking { container.exporter.export(container.projects.getProject(projectId)!!, 300) }
        assertEquals(4, result.copies)
        assertTrue(result.verification.maxErrorMm < 0.001)
    }

    @Test fun cuttingLinesAreSelectableAndPrinted() {
        compose.waitUntilAtLeastOneExists(hasText("New features sheet"), 20_000)
        compose.onNodeWithText("New features sheet").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("tab_paper"), 20_000)
        compose.onNodeWithTag("tab_paper").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag("guides_crop_marks"), 20_000)
        compose.onNodeWithTag("guides_crop_marks").performScrollTo().performClick()
        compose.onNodeWithTag("guides_trim_lines").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            runBlocking { container.projects.getProject(projectId)!!.settings.cutGuides } == io.github.riyadmondol2006.idsheetstudio.domain.model.CutGuides.TRIM_LINES
        }
        val result = runBlocking { container.exporter.export(container.projects.getProject(projectId)!!, 300) }
        val page = io.github.riyadmondol2006.idsheetstudio.pdf.PdfInspector.inspect(result.file.readBytes()).pages.single()
        // 4 passport copies in one row: 2 horizontal cuts + 8 vertical edges... at least one line per photo edge.
        assertTrue("expected cutting lines in the PDF", Regex(" l S").findAll(page.content).count() >= 2 + 2 * 4)
        assertTrue(result.verification.maxErrorMm < 0.001)
    }
}
