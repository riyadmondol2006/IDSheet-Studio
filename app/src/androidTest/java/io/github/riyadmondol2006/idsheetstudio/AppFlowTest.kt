package io.github.riyadmondol2006.idsheetstudio

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks through home → project tabs → editor → export → settings on a real device. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val container get() = TestPhotos.targetContext.appContainer
    private lateinit var projectId: String
    private lateinit var scenario: ActivityScenario<MainActivity>
    private val name = "UI flow test sheet"

    @Before fun seed() = runBlocking<Unit> {
        container.settings.completeOnboarding()
        container.settings.setAutoUpdateCheck(false)
        projectId = container.projects.createProject(name, SheetSettings())
        container.projects.importPhotos(projectId, listOf(TestPhotos.synthetic("ui1"), TestPhotos.synthetic("ui2", color = android.graphics.Color.rgb(200, 80, 80))), "passport_35x45")
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() = runBlocking<Unit> {
        scenario.close()
        container.projects.delete(projectId)
        container.settings.setAutoUpdateCheck(true)
    }

    private fun waitForTag(tag: String, timeout: Long = 20_000) =
        compose.waitUntilAtLeastOneExists(hasTestTag(tag), timeout)

    @Test fun fullFlow() {
        compose.waitUntilAtLeastOneExists(hasText(name), 20_000)
        compose.onNodeWithText(name).performClick()

        // Photos tab: both imported photos with their default 4 copies each.
        waitForTag("photo_card")
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("photo_card").fetchSemanticsNodes().size == 2 }
        compose.onNodeWithText("2 photos · 8 copies").assertIsDisplayed()

        // Editor: rotate and save.
        // Tap the label: the card centre can land on the background chip or quantity stepper.
        compose.onNodeWithText("Photo 1", useUnmergedTree = true).performClick()
        waitForTag("crop_canvas")
        compose.onNodeWithTag("rotate_right").performClick()
        compose.onNodeWithTag("editor_save").performClick()
        waitForTag("photos_list")

        // Paper tab: switch to US Letter.
        compose.onNodeWithTag("tab_paper").performClick()
        waitForTag("paper_letter")
        compose.onNodeWithTag("paper_letter").performClick()
        compose.onNodeWithTag("page_border_switch").performClick()

        // Layout tab shows a single page for 8 passport photos.
        compose.onNodeWithTag("tab_layout").performClick()
        waitForTag("layout_page_label")
        compose.onNodeWithText("Page 1 of 1").assertIsDisplayed()

        // Export: PDF is generated, read back and verified on the device.
        compose.onNodeWithTag("fab_export").performClick()
        waitForTag("export_verified", 60_000)
        compose.onNodeWithTag("export_page_size").assertIsDisplayed()
        compose.onNodeWithText("215.9 × 279.4 mm", substring = true).assertIsDisplayed()

        // Settings screen.
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag("tab_layout")
        scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        waitForTag("open_settings")
        compose.onNodeWithTag("open_settings").performClick()
        waitForTag("settings_list")
        compose.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("check_updates"))
        compose.onNodeWithTag("check_updates").assertIsDisplayed()
    }
}
