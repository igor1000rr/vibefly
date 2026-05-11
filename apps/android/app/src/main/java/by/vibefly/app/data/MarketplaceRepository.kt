package by.vibefly.app.data

import by.vibefly.app.agent.AgentClient
import by.vibefly.app.agent.MarketplaceInstallRequest
import by.vibefly.app.agent.MarketplaceTemplateDto

/**
 * Репозиторий marketplace.
 */
class MarketplaceRepository(private val clientProvider: () -> AgentClient) {

    suspend fun list(): List<MarketplaceTemplateDto> = clientProvider().marketplaceList()

    suspend fun get(id: String): MarketplaceTemplateDto = clientProvider().marketplaceGet(id)

    suspend fun install(templateId: String, request: MarketplaceInstallRequest) {
        clientProvider().marketplaceInstall(templateId, request)
    }
}
