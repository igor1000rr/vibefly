package by.vibefly.app.agent

import kotlinx.coroutines.flow.Flow

/**
 * Публичный API агента с точки зрения Android-клиента.
 *
 * Две реализации:
 *  • RemoteAgentClient — ходит в Go-агент по HTTP/WebSocket
 *  • MockAgentClient   — возвращает хардкоженные demo-данные, без сети
 *
 * Интерфейс держит все исходящие вызовы к агенту — ToolRegistry, AppsRepository,
 * SystemRepository, MarketplaceRepository упираются именно в этот интерфейс, не в класс.
 */
interface AgentApi {
    val baseUrl: String

    suspend fun health(): HealthDto
    suspend fun systemMetrics(): SystemMetricsDto

    suspend fun listApps(): List<AppDto>
    suspend fun getApp(id: String): AppDto
    suspend fun startApp(id: String): CommandResultDto
    suspend fun restartApp(id: String): CommandResultDto
    suspend fun stopApp(id: String): CommandResultDto
    suspend fun uninstallApp(id: String): CommandResultDto

    suspend fun recentLogs(id: String, lines: Int = 100): List<LogEntryDto>
    fun streamLogs(id: String): Flow<LogEntryDto>

    suspend fun marketplaceList(): List<MarketplaceTemplateDto>
    suspend fun marketplaceGet(id: String): MarketplaceTemplateDto
    suspend fun marketplaceInstall(templateId: String, req: MarketplaceInstallRequest)

    /** Закрыть все сетевые ресурсы. Неидемпотентно — повторные вызовы игнорируются. */
    fun close()
}
