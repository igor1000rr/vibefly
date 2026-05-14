package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.LogEntryDto
import by.vibefly.app.data.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppDetailState(
    val app: AppDto? = null,
    val logs: List<LogEntryDto> = emptyList(),
    val loading: Boolean = true,
    val streaming: Boolean = false,
    val error: String? = null,
    val uninstalling: Boolean = false,
    val publishing: Boolean = false,
    val publishError: String? = null,
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
                _state.update { it.copy(app = app, logs = backlog, loading = false, error = null) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = t.localizedMessage ?: "Не удалось загрузить приложение",
                    )
                }
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

    fun uninstall(onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(uninstalling = true, error = null) }
            try {
                ServiceLocator.agent().uninstallApp(appId)
                _state.update { it.copy(uninstalling = false) }
                onDone()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        uninstalling = false,
                        error = t.localizedMessage ?: "Не удалось удалить",
                    )
                }
            }
        }
    }

    /**
     * Стартует cloudflared для этого приложения. Операция занимает 5-30s.
     */
    fun publish() {
        viewModelScope.launch {
            _state.update { it.copy(publishing = true, publishError = null) }
            try {
                ServiceLocator.agent().publishApp(appId)
                load() // перезагрузит app.publicUrl
                _state.update { it.copy(publishing = false) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        publishing = false,
                        publishError = t.localizedMessage ?: "Не удалось опубликовать",
                    )
                }
            }
        }
    }

    fun unpublish() {
        viewModelScope.launch {
            _state.update { it.copy(publishing = true, publishError = null) }
            try {
                ServiceLocator.agent().unpublishApp(appId)
                load()
                _state.update { it.copy(publishing = false) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        publishing = false,
                        publishError = t.localizedMessage ?: "Не удалось снять публикацию",
                    )
                }
            }
        }
    }

    fun clearPublishError() {
        _state.update { it.copy(publishError = null) }
    }

    private fun observeLogs() {
        viewModelScope.launch {
            _state.update { it.copy(streaming = true) }
            ServiceLocator.agent().streamLogs(appId)
                .catch { t ->
                    _state.update {
                        it.copy(
                            streaming = false,
                            error = t.localizedMessage ?: "Потеряли связь с логами",
                        )
                    }
                }
                .collect { entry ->
                    _state.update { snap ->
                        val list = (snap.logs + entry).takeLast(300)
                        snap.copy(logs = list, error = null)
                    }
                }
        }
    }

    class Factory(private val appId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppDetailViewModel(appId) as T
        }
    }
}
