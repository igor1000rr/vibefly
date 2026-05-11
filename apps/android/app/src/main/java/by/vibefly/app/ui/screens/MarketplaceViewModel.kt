package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.MarketplaceInstallRequest
import by.vibefly.app.agent.MarketplaceTemplateDto
import by.vibefly.app.data.MarketplaceRepository
import by.vibefly.app.data.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarketplaceState(
    val templates: List<MarketplaceTemplateDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val installing: String? = null,
    val installedMessage: String? = null,
)

class MarketplaceViewModel(
    private val repo: MarketplaceRepository = MarketplaceRepository { ServiceLocator.agent() },
) : ViewModel() {

    private val _state = MutableStateFlow(MarketplaceState())
    val state: StateFlow<MarketplaceState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val list = repo.list()
                _state.update { it.copy(templates = list, loading = false) }
            } catch (t: Throwable) {
                _state.update { it.copy(loading = false, error = t.localizedMessage) }
            }
        }
    }

    fun install(template: MarketplaceTemplateDto) {
        viewModelScope.launch {
            _state.update { it.copy(installing = template.id, installedMessage = null) }
            val req = MarketplaceInstallRequest(
                appId = template.id,
                port = template.defaultPort,
                env = template.envSchema
                    .mapNotNull { f -> f.default?.let { f.key to it } }
                    .toMap(),
            )
            val result = runCatching { repo.install(template.id, req) }
            _state.update {
                it.copy(
                    installing = null,
                    installedMessage = if (result.isSuccess) "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D\u043E: ${template.name}"
                                       else "\u041E\u0448\u0438\u0431\u043A\u0430: ${result.exceptionOrNull()?.localizedMessage}",
                )
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(installedMessage = null) }
    }
}
