package by.vibefly.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.LogEntryDto
import by.vibefly.app.data.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние экрана деталей приложения.
 */
data class AppDetailState(
    val app: AppDto? = null,
    val logs: List<LogEntryDto> = emptyList(),
    val loading: Boolean = true,
    val streaming: Boolean = false,
    val error: String? = null,
)

class AppDetailViewModel(
    private val appId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(AppDetailState())
    val state: StateFlow<AppDetailState> = _state.asStateFlow()

    init {
        load()
        observeLogs()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val client = ServiceLocator.agent()
                val app = client.getApp(appId)
                val backlog = client.recentLogs(appId, lines = 100)
                _state.update { it.copy(app = app, logs = backlog, loading = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.localizedMessage) }
            }
        }
    }

    fun restart() {
        viewModelScope.launch {
            runCatching { ServiceLocator.agent().restartApp(appId) }
            load()
        }
    }

    fun stop() {
        viewModelScope.launch {
            runCatching { ServiceLocator.agent().stopApp(appId) }
            load()
        }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            try {
                _state.update { it.copy(streaming = true) }
                ServiceLocator.agent().streamLogs(appId).collect { entry ->
                    _state.update { snap ->
                        // Держим последние 300 строк, чтобы LazyColumn не разбухал.
                        val list = (snap.logs + entry).takeLast(300)
                        snap.copy(logs = list)
                    }
                }
            } catch (t: Throwable) {
                _state.update { it.copy(streaming = false, error = t.localizedMessage) }
            }
        }
    }

    /**
     * Factory для передачи appId из NavBackStackEntry.
     */
    class Factory(private val appId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppDetailViewModel(appId) as T
        }
    }
}
