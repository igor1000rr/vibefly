package by.vibefly.app.data

import by.vibefly.app.agent.AgentApi
import by.vibefly.app.agent.AppDto
import by.vibefly.app.agent.InstallAppRequest
import by.vibefly.app.agent.SystemMetricsDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

class AppsRepository(private val clientProvider: () -> AgentApi) {

    suspend fun list(): List<AppItem> = clientProvider().listApps().map { it.toItem() }

    suspend fun install(req: InstallAppRequest): AppItem = clientProvider().installApp(req).toItem()

    suspend fun start(id: String) { clientProvider().startApp(id) }

    suspend fun restart(id: String) { clientProvider().restartApp(id) }

    suspend fun stop(id: String) { clientProvider().stopApp(id) }

    suspend fun uninstall(id: String) { clientProvider().uninstallApp(id) }
}

class SystemRepository(private val clientProvider: () -> AgentApi) {

    suspend fun snapshot(): SystemMetricsDto = clientProvider().systemMetrics()

    fun stream(intervalMs: Long = 2_000): Flow<SystemMetricsDto> = flow {
        while (true) {
            try {
                emit(snapshot())
            } catch (_: Throwable) {
            }
            delay(intervalMs)
        }
    }
}
