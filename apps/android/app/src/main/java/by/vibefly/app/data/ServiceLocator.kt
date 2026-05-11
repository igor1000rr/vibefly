package by.vibefly.app.data

import android.app.Application
import by.vibefly.app.agent.AgentClient

/**
 * Простой service locator. Для фазы 1 этого достаточно; позже можно перевести на Hilt.
 */
object ServiceLocator {

    private var agentClient: AgentClient? = null
    private var appsRepository: AppsRepository? = null
    private var systemRepository: SystemRepository? = null

    fun init(app: Application) {
        if (agentClient != null) return
        agentClient = AgentClient()
        appsRepository = AppsRepository(agentClient!!)
        systemRepository = SystemRepository(agentClient!!)
    }

    fun apps(): AppsRepository = requireNotNull(appsRepository) { "ServiceLocator.init() не вызван" }
    fun system(): SystemRepository = requireNotNull(systemRepository) { "ServiceLocator.init() не вызван" }
}
