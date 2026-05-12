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
    /** Шаблон, который пользователь выбрал для install — открывает диалог с env-полями. */
    val pendingInstall: MarketplaceTemplateDto? = null,
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

    /**
     * Открыть install dialog для шаблона. Если у шаблона нет env_schema — сразу инстолируем
     * без диалога с дефолтами.
     */
    fun beginInstall(template: MarketplaceTemplateDto) {
        if (template.envSchema.isEmpty()) {
            val defaults = template.envSchema
                .mapNotNull { f -> f.default?.let { f.key to it } }
                .toMap()
            installInternal(template, defaults)
        } else {
            _state.update { it.copy(pendingInstall = template) }
        }
    }

    /** Пользователь нажал Cancel в install dialog. */
    fun cancelInstall() {
        _state.update { it.copy(pendingInstall = null) }
    }

    /** Пользователь заполнил env-поля и нажал Install. */
    fun confirmInstall(template: MarketplaceTemplateDto, env: Map<String, String>) {
        _state.update { it.copy(pendingInstall = null) }
        installInternal(template, env)
    }

    private fun installInternal(template: MarketplaceTemplateDto, env: Map<String, String>) {
        viewModelScope.launch {
            _state.update { it.copy(installing = template.id, installedMessage = null) }
            val req = MarketplaceInstallRequest(
                appId = template.id,
                port = template.defaultPort,
                env = env,
            )
            val result = runCatching { repo.install(template.id, req) }
            _state.update {
                it.copy(
                    installing = null,
                    installedMessage = if (result.isSuccess) "Установлено: ${template.name}"
                                       else "Ошибка: ${result.exceptionOrNull()?.localizedMessage}",
                )
            }
        }
    }

    fun dismissMessage() {
        _state.update { it.copy(installedMessage = null) }
    }
}
