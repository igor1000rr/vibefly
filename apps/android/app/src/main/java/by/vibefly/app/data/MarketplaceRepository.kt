package by.vibefly.app.data

import by.vibefly.app.agent.AgentApi
import by.vibefly.app.agent.MarketplaceInstallRequest
import by.vibefly.app.agent.MarketplaceTemplateDto

/**
 * Репозиторий marketplace. Принимает поставщика AgentApi (а не конкретного класса),
 * чтобы в demo-mode шёл MockAgentClient без правок UI/ViewModel.
 */
class MarketplaceRepository(private val clientProvider: () -> AgentApi) {

    suspend fun list(): List<MarketplaceTemplateDto> = clientProvider().marketplaceList()

    suspend fun get(id: String): MarketplaceTemplateDto = clientProvider().marketplaceGet(id)

    suspend fun install(templateId: String, request: MarketplaceInstallRequest) {
        clientProvider().marketplaceInstall(templateId, request)
    }
}
