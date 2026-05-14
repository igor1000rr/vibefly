package by.vibefly.app.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class MockAgentClient : AgentApi {

    override val baseUrl: String = "mock://"

    private val apps = mutableListOf(
        AppDto("amina-bot", "amina-bot", "running", "antsincgame/Amina-bot", "main", 3001, "@AIAMINABOT",
            publicUrl = null, memoryMb = 124, startedAt = "2026-05-08T03:58:00Z", lastDeploy = "2026-05-12T01:30:00Z"),
        AppDto("tonforge-api", "tonforge-api", "running", "antsincgame/tonforge", "main", 3002, "api.tonforge.org",
            publicUrl = null, memoryMb = 89, startedAt = "2026-05-09T12:00:00Z", lastDeploy = "2026-05-11T22:15:00Z"),
        AppDto("azcrm-staging", "azcrm-staging", "deploying", "igor1000rr/azcrm", "staging", 3003, "staging.crm.azgroupcompany.net",
            publicUrl = null, memoryMb = null, startedAt = null, lastDeploy = "2026-05-12T03:50:00Z"),
        AppDto("analytics-cron", "analytics-cron", "stopped", "igor1000rr/analytics", "main", null, null,
            publicUrl = null, memoryMb = null, startedAt = null, lastDeploy = "2026-05-10T08:00:00Z"),
    )

    private val appTunnels = mutableMapOf<String, TunnelStatusDto>()

    @Volatile private var tunnel: TunnelStatusDto = TunnelStatusDto(provider = "trycloudflare")

    override suspend fun health(): HealthDto {
        simulateLatency()
        return HealthDto("ok", "demo-0.5", nowIso(), supervisorAvailable = false, tunnelAvailable = true)
    }

    override suspend fun systemMetrics(): SystemMetricsDto {
        simulateLatency()
        return SystemMetricsDto(nowIso(), 78, "discharging", 38.0, 23.0, 2_150, 6_144, 308_200)
    }

    override suspend fun listApps(): List<AppDto> {
        simulateLatency()
        return apps.map { withTunnel(it) }
    }

    override suspend fun getApp(id: String): AppDto {
        simulateLatency()
        return apps.firstOrNull { it.id == id }?.let { withTunnel(it) }
            ?: throw NoSuchElementException("App not found: $id")
    }

    private fun withTunnel(app: AppDto): AppDto {
        val st = appTunnels[app.id] ?: return app
        return if (st.active) app.copy(publicUrl = st.publicUrl) else app
    }

    override suspend fun installApp(req: InstallAppRequest): AppDto {
        simulateLatency(500)
        val newApp = AppDto(req.id, req.name.ifBlank { req.id }, "stopped",
            null, null, req.port, req.domain, null, null, null, nowIso())
        apps.add(newApp)
        return newApp
    }

    override suspend fun startApp(id: String): CommandResultDto = mutateStatus(id, "running")
    override suspend fun stopApp(id: String): CommandResultDto = mutateStatus(id, "stopped")
    override suspend fun restartApp(id: String): CommandResultDto = mutateStatus(id, "running")

    override suspend fun uninstallApp(id: String): CommandResultDto {
        simulateLatency()
        apps.removeAll { it.id == id }
        appTunnels.remove(id)
        return CommandResultDto("ok", id)
    }

    override suspend fun recentLogs(id: String, lines: Int): List<LogEntryDto> {
        simulateLatency()
        return demoLogs(id).takeLast(lines)
    }

    override fun streamLogs(id: String): Flow<LogEntryDto> = flow {
        demoLogs(id).forEach { emit(it) }
        var tick = 0
        while (true) {
            delay(1_500)
            tick++
            emit(LogEntryDto(nowIso(), id, "info", "mock", "heartbeat #$tick"))
        }
    }

    override suspend fun marketplaceList(): List<MarketplaceTemplateDto> {
        simulateLatency()
        return demoMarketplace()
    }
    override suspend fun marketplaceGet(id: String): MarketplaceTemplateDto =
        demoMarketplace().firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Template not found: $id")
    override suspend fun marketplaceInstall(templateId: String, req: MarketplaceInstallRequest) {
        simulateLatency(800)
        val template = marketplaceGet(templateId)
        apps.add(AppDto(req.appId, req.appId, "deploying", template.repo, "main",
            req.port ?: template.defaultPort, req.domain, null, null, null, nowIso()))
    }

    override suspend fun tunnelStatus(): TunnelStatusDto { simulateLatency(); return tunnel }
    override suspend fun tunnelStart(): TunnelStatusDto {
        if (tunnel.active) return tunnel
        delay(2_500)
        tunnel = TunnelStatusDto(true, "https://mock-quick-tunnel-demo.trycloudflare.com", nowIso(), "trycloudflare")
        return tunnel
    }
    override suspend fun tunnelStop(): TunnelStatusDto {
        delay(400)
        tunnel = TunnelStatusDto(provider = "trycloudflare")
        return tunnel
    }

    override suspend fun appTunnelStatus(id: String): TunnelStatusDto =
        appTunnels[id] ?: TunnelStatusDto(provider = "none")

    override suspend fun publishApp(id: String): TunnelStatusDto {
        delay(2_000)
        val st = TunnelStatusDto(true, "https://mock-app-$id.trycloudflare.com", nowIso(), "trycloudflare")
        appTunnels[id] = st
        return st
    }

    override suspend fun unpublishApp(id: String): CommandResultDto {
        delay(300)
        appTunnels.remove(id)
        return CommandResultDto("ok", id)
    }

    override fun close() {}

    private suspend fun mutateStatus(id: String, newStatus: String): CommandResultDto {
        simulateLatency()
        val index = apps.indexOfFirst { it.id == id }
        if (index >= 0) {
            apps[index] = apps[index].copy(
                status = newStatus,
                startedAt = if (newStatus == "running") nowIso() else null,
            )
        }
        return CommandResultDto("ok", id)
    }

    private suspend fun simulateLatency(ms: Long = 120) = delay(ms)
    private fun nowIso(): String = "2026-05-12T06:50:00Z"

    private fun demoLogs(appId: String): List<LogEntryDto> {
        val base = "2026-05-12T06:4"
        return listOf(
            LogEntryDto("${base}0:00Z", appId, "info", "systemd", "Started $appId"),
            LogEntryDto("${base}1:12Z", appId, "info", "app", "Listening on :3001"),
            LogEntryDto("${base}3:01Z", appId, "info", "app", "GET /health 200 4ms"),
        )
    }

    /**
     * MarketplaceTemplateDto порядок параметров:
     *   id, name, category, description, icon,
     *   homepage?, repo?, image?,
     *   startCmd (обязателен!),
     *   defaultPort?, memoryMax?, envSchema, tags
     *
     * Использую named args чтобы не путать позиционно — иначе при добавлении новых
     * полей в DTO мок сломается с непонятной ошибкой типа.
     */
    private fun demoMarketplace(): List<MarketplaceTemplateDto> = listOf(
        MarketplaceTemplateDto(
            id = "vaultwarden",
            name = "Vaultwarden",
            category = "privacy",
            description = "Менеджер паролей",
            icon = "\uD83D\uDD10",
            repo = "vaultwarden",
            startCmd = "./vaultwarden",
            defaultPort = 8080,
            envSchema = emptyList(),
            tags = listOf("password"),
        ),
    )
}
