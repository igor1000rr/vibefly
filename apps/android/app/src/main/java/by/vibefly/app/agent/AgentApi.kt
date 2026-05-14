package by.vibefly.app.agent

import kotlinx.coroutines.flow.Flow

/**
 * Публичный API агента с точки зрения Android-клиента.
 */
interface AgentApi {
    val baseUrl: String

    suspend fun health(): HealthDto
    suspend fun systemMetrics(): SystemMetricsDto

    suspend fun listApps(): List<AppDto>
    suspend fun getApp(id: String): AppDto
    suspend fun installApp(req: InstallAppRequest): AppDto
    suspend fun startApp(id: String): CommandResultDto
    suspend fun restartApp(id: String): CommandResultDto
    suspend fun stopApp(id: String): CommandResultDto
    suspend fun uninstallApp(id: String): CommandResultDto

    suspend fun recentLogs(id: String, lines: Int = 100): List<LogEntryDto>
    fun streamLogs(id: String): Flow<LogEntryDto>

    suspend fun marketplaceList(): List<MarketplaceTemplateDto>
    suspend fun marketplaceGet(id: String): MarketplaceTemplateDto
    suspend fun marketplaceInstall(templateId: String, req: MarketplaceInstallRequest)

    /** Cloudflare Tunnel на сам агент (127.0.0.1:3001). */
    suspend fun tunnelStatus(): TunnelStatusDto
    suspend fun tunnelStart(): TunnelStatusDto
    suspend fun tunnelStop(): TunnelStatusDto

    /** Per-app tunnel на port приложения. Для Publish/Unpublish из AppDetail. */
    suspend fun appTunnelStatus(id: String): TunnelStatusDto
    suspend fun publishApp(id: String): TunnelStatusDto
    suspend fun unpublishApp(id: String): CommandResultDto

    fun close()
}
