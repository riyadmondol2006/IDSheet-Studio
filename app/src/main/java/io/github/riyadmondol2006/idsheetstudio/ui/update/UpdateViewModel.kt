package io.github.riyadmondol2006.idsheetstudio.ui.update

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.riyadmondol2006.idsheetstudio.AppContainer
import io.github.riyadmondol2006.idsheetstudio.BuildConfig
import io.github.riyadmondol2006.idsheetstudio.data.update.InstallEvent
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateInfo
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateInstaller
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateNotifier
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdatePolicy
import io.github.riyadmondol2006.idsheetstudio.data.update.UpdateVerificationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

sealed interface UpdatePhase {
    data object Idle : UpdatePhase
    data class Downloading(val progress: Float) : UpdatePhase
    data object Installing : UpdatePhase
    data object NeedsPermission : UpdatePhase
    data class Failed(val message: String) : UpdatePhase
}

data class UpdateUiState(
    val info: UpdateInfo? = null,
    val visible: Boolean = false,
    val phase: UpdatePhase = UpdatePhase.Idle,
    val checking: Boolean = false,
)

class UpdateViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private var downloaded: File? = null
    private var job: Job? = null

    init {
        viewModelScope.launch {
            UpdateInstaller.installEvents.collect { event ->
                when (event) {
                    InstallEvent.Success -> _state.update { it.copy(visible = false, phase = UpdatePhase.Idle) }
                    is InstallEvent.Failure -> _state.update { it.copy(phase = UpdatePhase.Failed(event.message)) }
                }
            }
        }
    }

    /** Called whenever the app comes to the foreground; throttled by [UpdatePolicy]. */
    fun autoCheck() = viewModelScope.launch {
        if (_state.value.checking || _state.value.visible) return@launch
        if (!UpdatePolicy.shouldCheckInForeground(container.settings.current(), System.currentTimeMillis())) return@launch
        check(manual = false)
    }

    /** "Check for updates" in Settings. */
    fun checkNow() = viewModelScope.launch { check(manual = true) }

    /** The user tapped the "new version available" notification. */
    fun openFromNotification() = viewModelScope.launch {
        UpdateNotifier.cancel(container.appContext)
        check(manual = false, userInitiated = true)
    }

    private suspend fun check(manual: Boolean, userInitiated: Boolean = manual) {
        _state.update { it.copy(checking = true) }
        val result = runCatching { container.updateChecker.findUpdate(BuildConfig.VERSION_NAME.removeSuffix("-debug"), Build.VERSION.SDK_INT) }
        _state.update { it.copy(checking = false) }
        container.settings.setLastUpdateCheck(System.currentTimeMillis())
        val info = result.getOrElse {
            if (manual) _messages.emit("Couldn't check for updates. Check your connection.")
            return
        }
        when {
            info == null -> if (manual) _messages.emit("You're on the latest version (${BuildConfig.VERSION_NAME}).")
            UpdatePolicy.shouldShowPopup(info, container.settings.current(), System.currentTimeMillis(), userInitiated) -> {
                UpdateNotifier.cancel(container.appContext)
                _state.update { it.copy(info = info, visible = true, phase = UpdatePhase.Idle) }
            }
        }
    }

    /** Hides the popup and snoozes automatic reminders for a day. */
    fun later() {
        job?.cancel()
        _state.update { it.copy(visible = false, phase = UpdatePhase.Idle) }
        viewModelScope.launch { container.settings.snoozeUpdates(System.currentTimeMillis() + UpdatePolicy.SNOOZE_MS) }
    }

    /** Never remind about this version again (a newer one will still be offered). */
    fun skipVersion() = viewModelScope.launch {
        _state.value.info?.let { container.settings.setSkippedVersion(it.version.toString()) }
        job?.cancel()
        _state.update { it.copy(visible = false, phase = UpdatePhase.Idle) }
    }

    fun update() {
        val info = _state.value.info ?: return
        val installer = container.updateInstaller
        if (!installer.canInstallPackages()) {
            _state.update { it.copy(phase = UpdatePhase.NeedsPermission) }
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            try {
                val file = downloaded?.takeIf { it.exists() && it.name.contains(info.version.toString()) }
                    ?: installer.download(info) { p -> _state.update { s -> s.copy(phase = UpdatePhase.Downloading(p)) } }
                downloaded = file
                _state.update { it.copy(phase = UpdatePhase.Installing) }
                installer.install(file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: UpdateVerificationException) {
                _state.update { it.copy(phase = UpdatePhase.Failed(e.message ?: "Verification failed.")) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = UpdatePhase.Failed(e.message ?: "Update failed.")) }
            }
        }
    }

    /** After returning from the "install unknown apps" settings screen. */
    fun onPermissionResult() {
        if (_state.value.phase == UpdatePhase.NeedsPermission && container.updateInstaller.canInstallPackages()) update()
    }

}
