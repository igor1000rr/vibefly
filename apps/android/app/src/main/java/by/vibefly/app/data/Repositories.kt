package by.vibefly.app.data

import by.vibefly.app.agent.AgentApi
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.SystemMetricsDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Бизнес-модель приложения в UI-слое. Отвязана от DTO, чтобы API агента можно было
 * эволюционировать без сломки экранов.
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
 * AppsRepository — фасад над AgentApi для экранов.
 *
 * Важно: принимает () -> AgentApi, а не AgentApi. Это позволяет после смены
 * настроек (URL/token/demo-mode) взять актуального клиента, а не закэшированного
 * (который уже close()-нут в ServiceLocator).
 */
class AppsRepository(private val clientProvider: () -> AgentApi) {

    suspend fun list(): List<AppItem> = clientProvider().listApps().map { it.toItem() }

    suspend fun start(id: String) { clientProvider().startApp(id) }

    suspend fun restart(id: String) { clientProvider().restartApp(id) }

    suspend fun stop(id: String) { clientProvider().stopApp(id) }
}

/**
 * SystemRepository — метрики устройства.
 */
class SystemRepository(private val clientProvider: () -> AgentApi) {

    suspend fun snapshot(): SystemMetricsDto = clientProvider().systemMetrics()

    /**
     * Стрим метрик с фиксированным интервалом опроса. Исключения из snapshot()
     * ловятся внутри цикла, поток продолжает жить — иначе одна сетевая ошибка
     * убьёт поток навсегда, даже если агент потом восстановится.
     */
    fun stream(intervalMs: Long = 2_000): Flow<SystemMetricsDto> = flow {
        while (true) {
            try {
                emit(snapshot())
            } catch (_: Throwable) {
                // Нет связи / таймаут — пропускаем этот тик, ждём, трайпаем снова.
            }
            delay(intervalMs)
        }
    }
}
