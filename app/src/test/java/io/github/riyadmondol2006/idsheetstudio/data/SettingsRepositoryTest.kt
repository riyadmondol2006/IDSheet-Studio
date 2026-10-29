package io.github.riyadmondol2006.idsheetstudio.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.riyadmondol2006.idsheetstudio.core.LengthUnit
import io.github.riyadmondol2006.idsheetstudio.data.settings.RemoveBgSize
import io.github.riyadmondol2006.idsheetstudio.data.settings.SecretCipher
import io.github.riyadmondol2006.idsheetstudio.data.settings.SettingsRepository
import io.github.riyadmondol2006.idsheetstudio.data.settings.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val folder = TemporaryFolder()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Reversible fake; "BROKEN" ciphertext simulates a key invalidated by a Keystore reset. */
    private class FakeCipher : SecretCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(encoded: String) = if (encoded.startsWith("enc:")) encoded.removePrefix("enc:").reversed() else null
    }

    private val store by lazy { PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("s.preferences_pb").also { it.delete() } } }
    private val repo by lazy { SettingsRepository(store, FakeCipher()) }

    @After fun tearDown() = scope.cancel()

    @Test fun defaultsOnFirstLaunch() = runTest {
        val s = repo.current()
        assertFalse(s.onboardingDone)
        assertFalse(s.hasApiKey)
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(600, s.exportDpi)
        assertNull(repo.apiKey())
    }

    @Test fun apiKeyIsTrimmedEncryptedAndRemovable() = runTest {
        repo.setApiKey("  my-key \n")
        assertEquals("my-key", repo.apiKey())
        val raw = store.data.first()[stringPreferencesKey("removebg_api_key_enc")]!!
        assertFalse("stored value must not contain the key", raw.contains("my-key"))
        repo.setApiKey("   ")
        assertNull(repo.apiKey())
        assertFalse(repo.current().hasApiKey)
    }

    @Test fun undecryptableKeyBehavesAsMissing() = runTest {
        store.edit { it[stringPreferencesKey("removebg_api_key_enc")] = "BROKEN" }
        assertNull(repo.apiKey())
    }

    @Test fun unknownOrCorruptValuesFallBackToDefaults() = runTest {
        store.edit {
            it[stringPreferencesKey("theme_mode")] = "PURPLE"
            it[stringPreferencesKey("default_unit")] = "furlong"
            it[stringPreferencesKey("removebg_size")] = ""
        }
        val s = repo.current()
        assertEquals(ThemeMode.SYSTEM, s.themeMode)
        assertEquals(LengthUnit.MM, s.defaultUnit)
        assertEquals(RemoveBgSize.AUTO, s.removeBgSize)
    }

    @Test fun dpiIsClamped() = runTest {
        repo.setExportDpi(10_000)
        assertEquals(1200, repo.current().exportDpi)
        repo.setExportDpi(-5)
        assertEquals(150, repo.current().exportDpi)
        store.edit { it[intPreferencesKey("export_dpi")] = 300 }
        assertEquals(300, repo.current().exportDpi)
    }

    @Test fun skippedVersionRoundTrip() = runTest {
        repo.setSkippedVersion("1.2.0")
        assertEquals("1.2.0", repo.current().skippedVersion)
        repo.setSkippedVersion(null)
        assertNull(repo.current().skippedVersion)
        repo.completeOnboarding()
        assertTrue(repo.current().onboardingDone)
    }
}
