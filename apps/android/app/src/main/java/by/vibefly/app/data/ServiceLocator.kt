package by.vibefly.app.data

import android.app.Application
import by.vibefly.app.agent.AgentClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Service locator + реактивное пересоздание AgentClient при изменении настроек.
 *
 * Клиент пересоздаётся лениво — при первом вызове репозитория после смены URL/токена.
 * Это проще и безопаснее, чем дёргать HttpClient в рантайме.
 */
object ServiceLocator {

    private lateinit var appContext: Application
    private lateinit var settingsStore: SettingsStore
    private val scope = CoroutineScope(SupervisorJob())

    private val _agentClient = MutableStateFlow<AgentClient?>(null)
    val agentClient: StateFlow<AgentClient?> = _agentClient.asStateFlow()

    fun init(app: Application) {
        if (this::appContext.isInitialized) return
        appContext = app
        settingsStore = SettingsStore(app)

        // Наблюдаем за настройками; при любом изменении — пересобираем клиента.
        scope.launch {
            settingsStore.state.collect { snap ->
                _agentClient.value?.close()
                _agentClient.value = AgentClient(
                    baseUrl = snap.baseUrl,
                    tokenProvider = { snap.authToken.ifBlank { null } },
                )
            }
        }
    }

    fun settings(): SettingsStore = settingsStore

    /**
     * Актуальный клиент агента. Никогда не кэшируй его в полевой переменной в ViewModel —
     * после смены настроек он будет уже закрыт.
     */
    fun agent(): AgentClient = checkNotNull(_agentClient.value) { "AgentClient ещё не создан" }

    fun apps(): AppsRepository = AppsRepository(agent())
    fun system(): SystemRepository = SystemRepository(agent())
}
