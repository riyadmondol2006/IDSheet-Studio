package io.github.riyadmondol2006.idsheetstudio

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import io.github.riyadmondol2006.idsheetstudio.data.BackgroundRemover
import io.github.riyadmondol2006.idsheetstudio.data.ProjectRepository
import io.github.riyadmondol2006.idsheetstudio.data.db.AppDatabase
import io.github.riyadmondol2006.idsheetstudio.data.remote.GitHubApi
import io.github.riyadmondol2006.idsheetstudio.data.remote.Network
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgApi
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgService
import io.github.riyadmondol2006.idsheetstudio.data.settings.KeystoreSecretCipher
import io.github.riyadmondol2006.idsheetstudio.data.settings.SettingsRepository
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateCheckWorker
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateChecker
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateInstaller
import io.github.riyadmondol2006.idsheetstudio.image.PhotoRenderer
import io.github.riyadmondol2006.idsheetstudio.pdf.SheetPdfExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** Manual dependency container; one instance per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val applicationScope = CoroutineScope(SupervisorJob())

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }
    val settings: SettingsRepository by lazy { SettingsRepository(appContext.settingsStore, KeystoreSecretCipher()) }
    val projects: ProjectRepository by lazy { ProjectRepository(appContext, database.projectDao()) }
    val renderer: PhotoRenderer by lazy { PhotoRenderer() }
    val exporter: SheetPdfExporter by lazy { SheetPdfExporter(appContext, renderer) }

    private val httpClient by lazy { Network.client() }

    val removeBg: RemoveBgService by lazy {
        RemoveBgService(Network.retrofit(RemoveBgService.BASE_URL, httpClient).create(RemoveBgApi::class.java))
    }

    val updateChecker: UpdateChecker by lazy {
        UpdateChecker(
            Network.retrofit(GitHubApi.BASE_URL, httpClient).create(GitHubApi::class.java),
            BuildConfig.GITHUB_OWNER,
            BuildConfig.GITHUB_REPO,
        )
    }

    val updateInstaller: UpdateInstaller by lazy { UpdateInstaller(appContext, httpClient) }

    val backgroundRemover: BackgroundRemover by lazy { BackgroundRemover(applicationScope, projects, settings, removeBg) }
}

class IDSheetApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // A process death during background removal must not leave photos stuck as "processing".
        container.applicationScope.launch { container.projects.resetStaleProcessing() }
        // Keep the background update check in sync with the "Check automatically" setting.
        container.applicationScope.launch {
            container.settings.settings.map { it.autoUpdateCheck }.distinctUntilChanged().collect { enabled ->
                if (enabled) UpdateCheckWorker.schedule(this@IDSheetApp) else UpdateCheckWorker.cancel(this@IDSheetApp)
            }
        }
    }
}

val Context.appContainer: AppContainer get() = (applicationContext as IDSheetApp).container
