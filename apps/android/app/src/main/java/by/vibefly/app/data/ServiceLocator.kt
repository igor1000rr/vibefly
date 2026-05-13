package by.vibefly.app.data

import android.app.Application
import by.vibefly.app.agent.AgentApi
import by.vibefly.app.agent.AgentClient
import by.vibefly.app.agent.MockAgentClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Service locator + реактивное пересоздание AgentApi при изменении настроек.
 *
 * Первый клиент создаётся СИНХРОННО в init() — это убирает гонку между
 * Application.onCreate и первым вызовом agent() из ViewModel при запуске.
 * Дальше в фоне следим за settingsStore и пересоздаём клиента при смене
 * URL/токена/demo-mode.
 */
object ServiceLocator {

    private lateinit var appContext: Application
    private lateinit var settingsStore: SettingsStore
    private val scope = CoroutineScope(SupervisorJob())

    private val _agentClient = MutableStateFlow<AgentApi?>(null)
    val agentClient: StateFlow<AgentApi?> = _agentClient.asStateFlow()

    private val toolRegistry: ToolRegistry by lazy { ToolRegistry { agent() } }

    fun init(app: Application) {
        if (this::appContext.isInitialized) return
        appContext = app
        settingsStore = SettingsStore(app)

        // СИНХРОННО создаём первого клиента по текущему состоянию настроек.
        // Без этого первый вызов agent() из ViewModel.init может случиться РАНЬШЕ,
        // чем корутина ниже успеет сделать первый emit — и приложение упадёт с
        // "AgentApi ещё не создан".
        _agentClient.value = buildClient(settingsStore.current())

        // Дальше наблюдаем за изменениями. drop(1) пропускает стартовое значение,
        // чтобы не пересоздавать же только что созданного клиента.
        scope.launch {
            settingsStore.state.drop(1).collect { snap ->
                _agentClient.value?.close()
                _agentClient.value = buildClient(snap)
            }
        }
    }

    private fun buildClient(snap: SettingsStore.Snapshot): AgentApi =
        if (snap.demoMode) {
            MockAgentClient()
        } else {
            AgentClient(
                baseUrl = snap.baseUrl,
                tokenProvider = { snap.authToken.ifBlank { null } },
            )
        }

    fun settings(): SettingsStore = settingsStore

    /**
     * Актуальный клиент агента. Никогда не кэшируй его в полевой переменной в ViewModel —
     * после смены настроек он будет уже закрыт.
     */
    fun agent(): AgentApi = checkNotNull(_agentClient.value) { "AgentApi ещё не создан" }

    fun apps(): AppsRepository = AppsRepository { agent() }
    fun system(): SystemRepository = SystemRepository { agent() }
    fun tools(): ToolRegistry = toolRegistry
}
