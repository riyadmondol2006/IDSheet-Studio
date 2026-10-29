package io.github.riyadmondol2006.idsheetstudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateVerificationException
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** In-app update security checks that need no external files (runs in CI). */
@RunWith(AndroidJUnit4::class)
class UpdateVerificationTest {
    private val context get() = TestPhotos.targetContext
    private val installer get() = context.appContainer.updateInstaller

    private fun expectRejected(apk: File, messagePart: String) {
        try {
            installer.verifyArchive(apk)
            fail("Expected ${apk.name} to be rejected")
        } catch (e: UpdateVerificationException) {
            assertTrue("Unexpected message: ${e.message}", e.message!!.contains(messagePart, ignoreCase = true))
        }
    }

    @Test fun sameVersionIsRejected() {
        val self = File(context.applicationInfo.sourceDir)
        val copy = File(context.cacheDir, "self.apk").also { self.copyTo(it, overwrite = true) }
        expectRejected(copy, "not newer")
    }

    @Test fun nonApkIsRejected() {
        val junk = File(context.cacheDir, "junk.apk").apply { writeText("not an apk") }
        expectRejected(junk, "not a valid APK")
    }
}

/**
 * Update checks with real newer APKs, copied into the app cache before the run (not stored in
 * the repository, so CI excludes this class):
 *  - update-newer.apk: same package, higher version, same signing key -> accepted
 *  - update-foreign.apk: same package, higher version, different key -> rejected
 */
@RequiresLocalFixtures
@RunWith(AndroidJUnit4::class)
class UpdateVerificationWithApksTest {
    private val context get() = TestPhotos.targetContext
    private val installer get() = context.appContainer.updateInstaller

    private fun expectRejected(apk: File, messagePart: String) {
        try {
            installer.verifyArchive(apk)
            fail("Expected ${apk.name} to be rejected")
        } catch (e: UpdateVerificationException) {
            assertTrue("Unexpected message: ${e.message}", e.message!!.contains(messagePart, ignoreCase = true))
        }
    }

    private fun fixture(name: String) = File(context.cacheDir, name).also {
        assertTrue("$name must be copied into the app cache before running this test", it.exists())
    }

    @Test fun newerApkWithSameSignatureIsAccepted() {
        installer.verifyArchive(fixture("update-newer.apk")) // must not throw
    }

    @Test fun newerApkWithForeignSignatureIsRejected() {
        expectRejected(fixture("update-foreign.apk"), "Signature mismatch")
    }
}
