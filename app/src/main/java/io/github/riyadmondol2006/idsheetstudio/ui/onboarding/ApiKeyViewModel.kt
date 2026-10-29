package io.github.riyadmondol2006.idsheetstudio.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.riyadmondol2006.idsheetstudio.AppContainer
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgAccount
import io.github.riyadmondol2006.idsheetstudio.data.remote.RemoveBgException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ApiKeyState(
    val verifying: Boolean = false,
    val account: RemoveBgAccount? = null,
    val error: String? = null,
    val savedKeyPresent: Boolean = false,
    val savedKeyHint: String? = null,
)

/** Verifies and stores the remove.bg key. The key itself is never exposed in UI state. */
class ApiKeyViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ApiKeyState())
    val state: StateFlow<ApiKeyState> = _state

    init {
        refreshSaved()
    }

    private fun refreshSaved() = viewModelScope.launch {
        val key = container.settings.apiKey()
        _state.update { it.copy(savedKeyPresent = key != null, savedKeyHint = key?.let(::mask)) }
    }

    /** Checks the key against the remove.bg account endpoint (free) and saves it when valid. */
    fun verifyAndSave(key: String, onSaved: () -> Unit = {}) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(error = "Paste your API key first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(verifying = true, error = null, account = null) }
            try {
                val account = container.removeBg.account(trimmed)
                container.settings.setApiKey(trimmed)
                _state.update { it.copy(verifying = false, account = account) }
                refreshSaved()
                onSaved()
            } catch (e: RemoveBgException) {
                _state.update { it.copy(verifying = false, error = e.message) }
            }
        }
    }

    /** Re-checks the saved key and shows the remaining balance. */
    fun checkSaved() = viewModelScope.launch {
        val key = container.settings.apiKey() ?: return@launch
        _state.update { it.copy(verifying = true, error = null) }
        try {
            val account = container.removeBg.account(key)
            _state.update { it.copy(verifying = false, account = account) }
        } catch (e: RemoveBgException) {
            _state.update { it.copy(verifying = false, error = e.message) }
        }
    }

    fun removeKey() = viewModelScope.launch {
        container.settings.setApiKey(null)
        _state.update { ApiKeyState() }
    }

    fun completeOnboarding(onDone: () -> Unit) = viewModelScope.launch {
        container.settings.completeOnboarding()
        onDone()
    }

    companion object {
        fun mask(key: String): String = if (key.length <= 6) "••••" else "••••••••" + key.takeLast(4)
    }
}
