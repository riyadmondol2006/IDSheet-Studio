package io.github.riyadmondol2006.idsheetstudio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.riyadmondol2006.idsheetstudio.data.update.PendingInstallConfirmation
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateNotifier
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import io.github.riyadmondol2006.idsheetstudio.ui.theme.isDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import io.github.riyadmondol2006.idsheetstudio.data.settings.AppSettings
import io.github.riyadmondol2006.idsheetstudio.ui.appViewModel
import io.github.riyadmondol2006.idsheetstudio.ui.navigation.AppNavHost
import io.github.riyadmondol2006.idsheetstudio.ui.theme.IDSheetTheme
import io.github.riyadmondol2006.idsheetstudio.ui.update.UpdateDialog
import io.github.riyadmondol2006.idsheetstudio.ui.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val initialSettings = MutableStateFlow<AppSettings?>(null)

    /** Incremented whenever the update notification opens the app. */
    private val updateRequests = MutableStateFlow(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(UpdateNotifier.EXTRA_SHOW_UPDATE, false) == true) {
            intent.removeExtra(UpdateNotifier.EXTRA_SHOW_UPDATE)
            updateRequests.value++
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { initialSettings.value == null }
        if (savedInstanceState == null) handleIntent(intent)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                appContainer.settings.settings.collect { initialSettings.value = it }
            }
        }

        setContent {
            val settings by initialSettings.collectAsStateWithLifecycle()
            val current = settings ?: return@setContent
            // System bar icons must follow the in-app theme, not only the system dark mode.
            val dark = isDarkTheme(current.themeMode)
            DisposableEffect(dark) {
                val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose { }
            }
            IDSheetTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor) {
                val updates = appViewModel { c, _ -> UpdateViewModel(c) }
                val updateState by updates.state.collectAsStateWithLifecycle()
                val snackbar = remember { SnackbarHostState() }
                val nav = rememberNavController()
                // Decide the start destination once; later setting changes must not reset navigation.
                val startOnboarding = remember { !current.onboardingDone }

                val updateRequest by updateRequests.collectAsStateWithLifecycle()
                LaunchedEffect(updateRequest) { if (updateRequest > 0) updates.openFromNotification() }
                LaunchedEffect(Unit) { updates.messages.collect { snackbar.showSnackbar(it) } }
                // Check for new releases each time the app comes to the foreground (throttled).
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    updates.onPermissionResult()
                    updates.autoCheck()
                }
                NotificationPermissionRequest(current)
                InstallConfirmationLauncher()

                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
                        AppNavHost(
                            nav = nav,
                            startOnboarding = startOnboarding,
                            onCheckUpdates = updates::checkNow,
                            checkingUpdates = updateState.checking,
                        )
                        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
                    }
                }
                if (updateState.visible) {
                    UpdateDialog(updateState, onUpdate = updates::update, onLater = updates::later, onSkip = updates::skipVersion)
                }
            }
        }
    }
}

/**
 * Asks once (Android 13+) for permission to post update notifications, after onboarding and only
 * while automatic update checks are on. Declining is respected; the in-app popup still works.
 */
@Composable
private fun NotificationPermissionRequest(settings: AppSettings) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val container = context.appContainer
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(settings.onboardingDone, settings.autoUpdateCheck, settings.notificationPermissionAsked) {
        if (!settings.onboardingDone || !settings.autoUpdateCheck || settings.notificationPermissionAsked) return@LaunchedEffect
        container.settings.setNotificationPermissionAsked()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Starts the system install confirmation once the app is in the foreground. */
@Composable
private fun InstallConfirmationLauncher() {
    val context = LocalContext.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            PendingInstallConfirmation.intent.collect { intent ->
                if (intent != null) {
                    PendingInstallConfirmation.intent.value = null
                    runCatching { context.startActivity(intent) }
                }
            }
        }
    }
}
