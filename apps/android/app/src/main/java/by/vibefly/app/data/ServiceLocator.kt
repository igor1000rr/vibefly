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
import kotlinx.coroutines.launch

/**
 * Service locator + реактивное пересоздание AgentApi при изменении настроек.
 *
 * Клиент пересоздаётся лениво — при первом вызове репозитория после смены URL/токена.
 * Это проще и безопаснее, чем дёргать HttpClient в рантайме.
 *
 * Если SettingsStore.demoMode = true — возвращается MockAgentClient,
 * который никуда не ходит и отдаёт фейковые данные для демо/скриншотов.
 */
object ServiceLocator {

    private lateinit var appContext: Application
    private lateinit var settingsStore: SettingsStore
    private val scope = CoroutineScope(SupervisorJob())

    private val _agentClient = MutableStateFlow<AgentApi?>(null)
    val agentClient: StateFlow<AgentApi?> = _agentClient.asStateFlow()

    /**
     * ToolRegistry создаётся один раз и хранит замыкание на `agent()` — поэтому
     * после смены настроек его пересоздавать не надо: при следующем execute()
     * он возьмёт уже обновлённого клиента.
     */
    private val toolRegistry: ToolRegistry by lazy { ToolRegistry { agent() } }

    fun init(app: Application) {
        if (this::appContext.isInitialized) return
        appContext = app
        settingsStore = SettingsStore(app)

        // Наблюдаем за настройками; при любом изменении — пересобираем клиента.
        scope.launch {
            settingsStore.state.collect { snap ->
                _agentClient.value?.close()
                _agentClient.value = if (snap.demoMode) {
                    MockAgentClient()
                } else {
                    AgentClient(
                        baseUrl = snap.baseUrl,
                        tokenProvider = { snap.authToken.ifBlank { null } },
                    )
                }
            }
        }
    }

    fun settings(): SettingsStore = settingsStore

    /**
     * Актуальный клиент агента. Никогда не кэшируй его в полевой переменной в ViewModel —
     * после смены настроек он будет уже закрыт.
     */
    fun agent(): AgentApi = checkNotNull(_agentClient.value) { "AgentApi ещё не создан" }

    fun apps(): AppsRepository = AppsRepository(agent())
    fun system(): SystemRepository = SystemRepository(agent())
    fun tools(): ToolRegistry = toolRegistry
}
