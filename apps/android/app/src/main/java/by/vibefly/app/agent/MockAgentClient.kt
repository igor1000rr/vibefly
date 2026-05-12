package by.vibefly.app.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * MockAgentClient — возвращает хардкоженные demo-данные без сети. Предназначен
 * для демо/каталога/скриншотов и для случаев когда агент ещё не развёрнут (rootfs
 * не в комплекте APK в фазе 1).
 *
 * Переключается в SettingsStore.demoMode = true. Данные подобраны так, чтобы экраны
 * выглядели как на мокапах (amina-bot работает, azcrm-staging деплоится и т.д.).
 *
 * Команды restart/stop/start/install обновляют внутреннее состояние, чтобы UI
 * вел себя правдоподобно (toggle дёрнул — статус стал stopped).
 */
class MockAgentClient : AgentApi {

    override val baseUrl: String = "mock://"

    // Изменяемое состояние — имитирует in-memory базу агента.
    private val apps = mutableListOf(
        AppDto(
            id = "amina-bot",
            name = "amina-bot",
            status = "running",
            repo = "antsincgame/Amina-bot",
            branch = "main",
            port = 3001,
            domain = "@AIAMINABOT",
            memoryMb = 124,
            startedAt = "2026-05-08T03:58:00Z",
            lastDeploy = "2026-05-12T01:30:00Z",
        ),
        AppDto(
            id = "tonforge-api",
            name = "tonforge-api",
            status = "running",
            repo = "antsincgame/tonforge",
            branch = "main",
            port = 3002,
            domain = "api.tonforge.org",
            memoryMb = 89,
            startedAt = "2026-05-09T12:00:00Z",
            lastDeploy = "2026-05-11T22:15:00Z",
        ),
        AppDto(
            id = "azcrm-staging",
            name = "azcrm-staging",
            status = "deploying",
            repo = "igor1000rr/azcrm",
            branch = "staging",
            port = 3003,
            domain = "staging.crm.azgroupcompany.net",
            memoryMb = null,
            startedAt = null,
            lastDeploy = "2026-05-12T03:50:00Z",
        ),
        AppDto(
            id = "analytics-cron",
            name = "analytics-cron",
            status = "stopped",
            repo = "igor1000rr/analytics",
            branch = "main",
            port = null,
            domain = null,
            memoryMb = null,
            startedAt = null,
            lastDeploy = "2026-05-10T08:00:00Z",
        ),
    )

    override suspend fun health(): HealthDto {
        simulateLatency()
        return HealthDto(status = "ok", version = "demo-0.3", time = nowIso())
    }

    override suspend fun systemMetrics(): SystemMetricsDto {
        simulateLatency()
        return SystemMetricsDto(
            timestamp = nowIso(),
            batteryLevel = 78,
            batteryStatus = "discharging",
            temperatureC = 38.0,
            cpuPercent = 23.0,
            ramUsedMb = 2_150,
            ramTotalMb = 6_144,
            uptimeSeconds = 308_200,
        )
    }

    override suspend fun listApps(): List<AppDto> {
        simulateLatency()
        return apps.toList()
    }

    override suspend fun getApp(id: String): AppDto {
        simulateLatency()
        return apps.firstOrNull { it.id == id }
            ?: throw NoSuchElementException("App not found: $id")
    }

    override suspend fun startApp(id: String): CommandResultDto = mutateStatus(id, "running")
    override suspend fun stopApp(id: String): CommandResultDto = mutateStatus(id, "stopped")
    override suspend fun restartApp(id: String): CommandResultDto = mutateStatus(id, "running")

    override suspend fun uninstallApp(id: String): CommandResultDto {
        simulateLatency()
        apps.removeAll { it.id == id }
        return CommandResultDto(status = "ok", id = id)
    }

    override suspend fun recentLogs(id: String, lines: Int): List<LogEntryDto> {
        simulateLatency()
        return demoLogs(id).takeLast(lines)
    }

    override fun streamLogs(id: String): Flow<LogEntryDto> = flow {
        // Эмитируем по одной строке раз в секунду для живого эффекта.
        val backlog = demoLogs(id)
        backlog.forEach { entry ->
            emit(entry)
        }
        // После backlog — медленный ритм новых событий.
        var tick = 0
        while (true) {
            delay(1_500)
            tick++
            emit(
                LogEntryDto(
                    time = nowIso(),
                    app = id,
                    level = "info",
                    source = "mock",
                    message = "heartbeat #$tick \u2014 всё оказалось хорошо",
                )
            )
        }
    }

    override suspend fun marketplaceList(): List<MarketplaceTemplateDto> {
        simulateLatency()
        return demoMarketplace()
    }

    override suspend fun marketplaceGet(id: String): MarketplaceTemplateDto {
        return demoMarketplace().firstOrNull { it.id == id }
            ?: throw NoSuchElementException("Template not found: $id")
    }

    override suspend fun marketplaceInstall(templateId: String, req: MarketplaceInstallRequest) {
        simulateLatency(800) // для эффекта deploy'инга
        val template = marketplaceGet(templateId)
        apps.add(
            AppDto(
                id = req.appId,
                name = req.appId,
                status = "deploying",
                repo = template.repo,
                branch = "main",
                port = req.port ?: template.defaultPort,
                domain = req.domain,
                memoryMb = null,
                startedAt = null,
                lastDeploy = nowIso(),
            )
        )
    }

    override fun close() {
        // Ничего освобождать не нужно.
    }

    // ─── Внутренние helper'ы ──────────────────────────────────────────────────────────

    private suspend fun mutateStatus(id: String, newStatus: String): CommandResultDto {
        simulateLatency()
        val index = apps.indexOfFirst { it.id == id }
        if (index >= 0) {
            val old = apps[index]
            apps[index] = old.copy(
                status = newStatus,
                startedAt = if (newStatus == "running") nowIso() else null,
            )
        }
        return CommandResultDto(status = "ok", id = id)
    }

    private suspend fun simulateLatency(ms: Long = 120) = delay(ms)

    private fun nowIso(): String {
        // Не тянем полный ISO с timezone — нам нужен формат для UI, не для бэка.
        // Просто прилепим millis поверх фиксированной даты.
        return "2026-05-12T06:50:00Z"
    }

    private fun demoLogs(appId: String): List<LogEntryDto> {
        val base = "2026-05-12T06:4"
        return listOf(
            LogEntryDto("${base}0:00Z", appId, "info", "systemd", "Started $appId"),
            LogEntryDto("${base}1:12Z", appId, "info", "app", "Listening on :3001"),
            LogEntryDto("${base}2:48Z", appId, "info", "app", "Connected to Appwrite"),
            LogEntryDto("${base}3:01Z", appId, "info", "app", "GET /health 200 4ms"),
            LogEntryDto("${base}4:15Z", appId, "warn", "app", "Slow query: SELECT * FROM messages (1.2s)"),
            LogEntryDto("${base}5:30Z", appId, "info", "app", "GET /api/users 200 12ms"),
            LogEntryDto("${base}6:42Z", appId, "error", "app", "OpenRouter timeout after 5s, retrying"),
            LogEntryDto("${base}7:01Z", appId, "info", "app", "Retry succeeded on attempt 2"),
        )
    }

    private fun demoMarketplace(): List<MarketplaceTemplateDto> = listOf(
        MarketplaceTemplateDto(
            id = "vaultwarden",
            name = "Vaultwarden",
            category = "privacy",
            description = "Самохостимый Bitwarden-совместимый менеджер паролей.",
            icon = "\uD83D\uDD10",
            startCmd = "vaultwarden",
            defaultPort = 8080,
            envSchema = listOf(
                MarketplaceEnvFieldDto(
                    key = "ADMIN_TOKEN",
                    label = "Admin token",
                    hint = "Для доступа к /admin панели",
                    secret = true,
                    required = true,
                ),
            ),
            tags = listOf("password-manager", "selfhost"),
        ),
        MarketplaceTemplateDto(
            id = "n8n",
            name = "n8n",
            category = "automation",
            description = "Workflow-автоматизация с нодовым редактором.",
            icon = "\u26A1",
            startCmd = "n8n start",
            defaultPort = 5678,
            tags = listOf("automation", "low-code"),
        ),
        MarketplaceTemplateDto(
            id = "uptime-kuma",
            name = "Uptime Kuma",
            category = "monitoring",
            description = "Мониторинг uptime для сайтов и API с красивым dashboard.",
            icon = "\uD83D\uDC93",
            startCmd = "uptime-kuma",
            defaultPort = 3001,
            tags = listOf("monitoring", "alerting"),
        ),
        MarketplaceTemplateDto(
            id = "memos",
            name = "Memos",
            category = "productivity",
            description = "Минималистичные заметки в духе Twitter.",
            icon = "\uD83D\uDCDD",
            startCmd = "memos",
            defaultPort = 5230,
            tags = listOf("notes", "selfhost"),
        ),
        MarketplaceTemplateDto(
            id = "code-server",
            name = "code-server",
            category = "devtools",
            description = "VS Code в браузере.",
            icon = "\uD83D\uDCBB",
            startCmd = "code-server",
            defaultPort = 8443,
            envSchema = listOf(
                MarketplaceEnvFieldDto(
                    key = "PASSWORD",
                    label = "Пароль",
                    secret = true,
                    required = true,
                ),
            ),
            tags = listOf("ide", "selfhost"),
        ),
    )
}
