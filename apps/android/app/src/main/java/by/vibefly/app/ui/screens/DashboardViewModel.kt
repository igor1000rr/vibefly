package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * Состояние главного экрана.
 */
data class DashboardState(
    val metrics: SystemMetricsDto? = null,
    val apps: List<AppItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * DashboardViewModel — тянет метрики и список приложений с агента.
 *
 * Критично ловить все ошибки в observeMetrics()/refresh() и в toggle/restart, иначе
 * при недоступном агенте (первый запуск, без demo-mode) корутина падает с
 * необработанным исключением — Android выкидывает пользователя из приложения.
 */
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

    /**
     * Один-тап toggle на дашборде: если приложение running — стоп, иначе старт.
     * Затем рефреш состояния.
     */
    fun toggle(item: AppItem) {
        viewModelScope.launch {
            runCatching {
                if (item.status == AppStatus.Running) apps.stop(item.id)
                else apps.start(item.id)
            }
            refresh()
        }
    }

    private fun observeMetrics() {
        viewModelScope.launch {
            // catch на Flow и try внутри collect — две линии защиты на случай если
            // SystemRepository.stream бросит при первом вызове (нет сети на агент).
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
