package io.github.riyadmondol2006.idsheetstudio

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgService
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import io.github.riyadmondol2006.idsheetstudio.image.BitmapIO
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Live remove.bg integration. Uses the key the user saved in the app (encrypted in the Keystore)
 * or, when given, the `removeBgApiKey` runner argument. CI excludes this class because it has no
 * key. Uses one "preview" call per run.
 */
@RunWith(AndroidJUnit4::class)
class RemoveBgLiveTest {
    @Test fun accountAndBackgroundRemoval() = runBlocking<Unit> {
        val container = TestPhotos.targetContext.appContainer
        val key = InstrumentationRegistry.getArguments().getString("removeBgApiKey")?.takeIf { it.isNotBlank() }
            ?: container.settings.apiKey()
        assertNotNull("No remove.bg key saved in the app or passed as an argument. CI excludes this class.", key)
        val account = container.removeBg.account(key!!)
        assertTrue(account.totalCredits >= 0)

        val id = container.projects.createProject("Live", SheetSettings())
        container.projects.importPhotos(id, listOf(TestPhotos.realPortrait()), "passport_35x45")
        val photo = container.projects.getProject(id)!!.photos.single()
        val jpeg = BitmapIO.encodeForUpload(photo.originalPath, RemoveBgService.UPLOAD_MAX_DIMENSION)
        val png = container.removeBg.removeBackground(key, jpeg, "preview")
        val decoded = BitmapFactory.decodeByteArray(png, 0, png.size)
        assertTrue(decoded.hasAlpha())
        // Corners of a portrait cut-out are transparent background.
        assertEquals(0, android.graphics.Color.alpha(decoded.getPixel(0, 0)))
        assertTrue(android.graphics.Color.alpha(decoded.getPixel(decoded.width / 2, decoded.height / 2)) > 200)

        container.projects.saveCutout(photo.id, png)
        val updated = container.projects.getProject(id)!!.photos.single()
        assertTrue(updated.useCutout)
        // Rendered over pure white: corner pixel must be exactly #FFFFFF.
        val rendered = container.renderer.render(updated, 350, 450)!!
        assertEquals(android.graphics.Color.WHITE, rendered.getPixel(1, 1))
        container.projects.delete(id)
    }
}
