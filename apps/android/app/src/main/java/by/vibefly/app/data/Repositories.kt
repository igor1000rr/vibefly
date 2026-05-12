package by.vibefly.app.data

import by.vibefly.app.agent.AgentClient
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.SystemMetricsDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Бизнес-модель приложения в UI-слое. Отвязана от DTO, чтобы API агента можно было
 * эволюционировать без сломки экранов.
 *
 * compactLine: чистая строка для Dashboard ("api.tonforge.org · 89 MB"), без порта.
 * subtitle:    подробная строка для AppDetail ("api.tonforge.org · :3001"), без MB.
 *
 * Раздельные поля чтобы оба экрана могли иметь свой формат без if-else в UI.
 */
data class AppItem(
    val id: String,
    val name: String,
    val status: AppStatus,
    val subtitle: String,
    val compactLine: String,
    val memoryMb: Int?,
)

enum class AppStatus { Running, Deploying, Stopped, Failed, Unknown }

private fun String.toAppStatus(): AppStatus = when (lowercase()) {
    "running" -> AppStatus.Running
    "deploying" -> AppStatus.Deploying
    "stopped" -> AppStatus.Stopped
    "failed" -> AppStatus.Failed
    else -> AppStatus.Unknown
}

private fun AppDto.toItem(): AppItem {
    val host = domain ?: repo ?: id
    val subtitle = buildString {
        append(host)
        port?.let { append(" \u00B7 :").append(it) }
    }
    val compactLine = buildString {
        append(host)
        memoryMb?.let { append(" \u00B7 ").append(it).append(" MB") }
    }
    return AppItem(
        id = id,
        name = name,
        status = status.toAppStatus(),
        subtitle = subtitle,
        compactLine = compactLine,
        memoryMb = memoryMb,
    )
}

/**
 * AppsRepository — фасад над AgentClient для экранов.
 */
class AppsRepository(private val client: AgentClient) {

    suspend fun list(): List<AppItem> = client.listApps().map { it.toItem() }

    suspend fun start(id: String) { client.startApp(id) }

    suspend fun restart(id: String) { client.restartApp(id) }

    suspend fun stop(id: String) { client.stopApp(id) }
}

/**
 * SystemRepository — метрики устройства.
 */
class SystemRepository(private val client: AgentClient) {

    suspend fun snapshot(): SystemMetricsDto = client.systemMetrics()

    /**
     * Стрим метрик с фиксированным интервалом опроса.
     * В фазе 0.3 заменим на WebSocket /system/stream.
     */
    fun stream(intervalMs: Long = 2_000): Flow<SystemMetricsDto> = flow {
        while (true) {
            emit(snapshot())
            kotlinx.coroutines.delay(intervalMs)
        }
    }
}
