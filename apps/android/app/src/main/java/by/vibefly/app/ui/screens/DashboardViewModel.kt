package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.InstallAppRequest
import by.vibefly.app.data.AppItem
import by.vibefly.app.data.AppStatus
import by.vibefly.app.data.AppsRepository
import by.vibefly.app.data.ServiceLocator
import by.vibefly.app.data.SystemRepository
import by.vibefly.app.agent.SystemMetricsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DashboardState(
    val metrics: SystemMetricsDto? = null,
    val apps: List<AppItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val deploying: Boolean = false,
    val deployError: String? = null,
)

class DashboardViewModel(
    private val apps: AppsRepository = ServiceLocator.apps(),
    private val system: SystemRepository = ServiceLocator.system(),
) : ViewModel() {

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        refresh()
        observeMetrics()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val list = apps.list()
                _state.update { it.copy(apps = list, loading = false, error = null) }
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = t.localizedMessage ?: "Агент недоступен",
                    )
                }
            }
        }
    }

    fun restart(id: String) {
        viewModelScope.launch {
            runCatching { apps.restart(id) }
            refresh()
        }
    }

    fun toggle(item: AppItem) {
        viewModelScope.launch {
            runCatching {
                if (item.status == AppStatus.Running) apps.stop(item.id)
                else apps.start(item.id)
            }
            refresh()
        }
    }

    /**
     * Развернуть новое приложение. При успехе onSuccess вызывается из main thread
     * для закрытия диалога. При ошибке deployError попадает в state.
     */
    fun deploy(req: InstallAppRequest, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(deploying = true, deployError = null) }
            try {
                apps.install(req)
                _state.update { it.copy(deploying = false, deployError = null) }
                refresh()
                onSuccess()
            } catch (t: Throwable) {
                _state.update {
                    it.copy(
                        deploying = false,
                        deployError = t.localizedMessage ?: "Не удалось развернуть",
                    )
                }
            }
        }
    }

    fun clearDeployError() {
        _state.update { it.copy(deployError = null) }
    }

    private fun observeMetrics() {
        viewModelScope.launch {
            system.stream(intervalMs = 3_000)
                .catch { t ->
                    _state.update {
                        it.copy(error = t.localizedMessage ?: "Нет связи с агентом")
                    }
                }
                .collect { snap ->
                    _state.update { it.copy(metrics = snap, error = null) }
                }
        }
    }
}
