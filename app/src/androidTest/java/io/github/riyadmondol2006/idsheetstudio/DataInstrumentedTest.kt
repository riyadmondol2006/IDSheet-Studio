package io.github.riyadmondol2006.idsheetstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.data.settings.KeystoreSecretCipher
import io.github.riyadmondol2006.idsheetstudio.domain.model.BackgroundState
import io.github.riyadmondol2006.idsheetstudio.domain.model.SheetSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DataInstrumentedTest {
    private val container get() = TestPhotos.targetContext.appContainer

    @Test fun keystoreCipherRoundTrip() {
        val cipher = KeystoreSecretCipher("idsheet_test_alias")
        val secret = "sk_test_ABC123xyz"
        val encoded = cipher.encrypt(secret)
        assertFalse(encoded.contains(secret))
        assertNotEquals(encoded, cipher.encrypt(secret)) // random IV each time
        assertEquals(secret, cipher.decrypt(encoded))
        assertNull(cipher.decrypt("garbage:data"))
    }

    @Test fun projectLifecycleWithFiles() = runBlocking<Unit> {
        val repo = container.projects
        val id = repo.createProject("Lifecycle", SheetSettings())
        val outcome = repo.importPhotos(id, listOf(TestPhotos.synthetic("a"), TestPhotos.synthetic("b", 900, 900)), "passport_35x45")
        assertEquals(2, outcome.imported)
        var project = repo.getProject(id)!!
        assertEquals(2, project.photos.size)
        project.photos.forEach { assertTrue(File(it.originalPath).exists()) }

        // Edits persist and bump the revision.
        val first = project.photos.first()
        repo.updatePhoto(first.copy(quantity = 9, label = "Edited"))
        project = repo.getProject(id)!!
        assertEquals(9, project.photos.first().quantity)
        assertEquals("Edited", project.photos.first().label)
        assertTrue(project.photos.first().revision > first.revision)

        // A fake cut-out switches the photo to the processed image.
        repo.saveCutout(first.id, byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))
        project = repo.getProject(id)!!
        assertEquals(BackgroundState.REMOVED, project.photos.first().backgroundState)
        assertTrue(project.photos.first().useCutout)

        // Duplicate copies every file into an independent project.
        val copyId = repo.duplicate(id)
        assertNotNull(copyId)
        val copy = repo.getProject(copyId!!)!!
        assertEquals("Lifecycle (copy)", copy.name)
        assertEquals(2, copy.photos.size)
        copy.photos.zip(project.photos).forEach { (c, o) ->
            assertNotEquals(c.originalPath, o.originalPath)
            assertTrue(File(c.originalPath).exists())
        }

        // Deleting the original leaves the copy intact.
        repo.delete(id)
        assertNull(repo.getProject(id))
        assertFalse(repo.projectDir(id).exists())
        assertTrue(File(repo.getProject(copyId)!!.photos.first().originalPath).exists())
        repo.delete(copyId)
    }

    @Test fun exifOrientationIsApplied() = runBlocking<Unit> {
        val repo = container.projects
        val id = repo.createProject("Exif", SheetSettings())
        // A landscape-stored JPEG tagged "rotate 90°" must be imported as portrait.
        val file = File(TestPhotos.targetContext.cacheDir, "exif.jpg")
        val bmp = android.graphics.Bitmap.createBitmap(400, 300, android.graphics.Bitmap.Config.ARGB_8888)
        file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        androidx.exifinterface.media.ExifInterface(file.absolutePath).apply {
            setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION, androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        repo.importPhotos(id, listOf(android.net.Uri.fromFile(file)), "passport_35x45")
        val photo = repo.getProject(id)!!.photos.single()
        assertEquals(300, photo.sourceWidth)
        assertEquals(400, photo.sourceHeight)
        repo.delete(id)
    }

    @Test fun apiKeyIsStoredEncrypted() = runBlocking<Unit> {
        val settings = container.settings
        val previous = settings.apiKey()
        settings.setApiKey("  test-key-value  ")
        assertEquals("test-key-value", settings.apiKey())
        assertTrue(settings.current().hasApiKey)
        settings.setApiKey(previous)
    }
}
