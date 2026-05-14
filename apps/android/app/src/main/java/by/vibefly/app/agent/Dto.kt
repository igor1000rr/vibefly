package by.vibefly.app.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
    val time: String,
    @SerialName("supervisor_available") val supervisorAvailable: Boolean = false,
    @SerialName("tunnel_available") val tunnelAvailable: Boolean = false,
)

@Serializable
data class SystemMetricsDto(
    val timestamp: String,
    @SerialName("battery_level") val batteryLevel: Int,
    @SerialName("battery_status") val batteryStatus: String,
    @SerialName("temperature_c") val temperatureC: Double,
    @SerialName("cpu_percent") val cpuPercent: Double,
    @SerialName("ram_used_mb") val ramUsedMb: Int,
    @SerialName("ram_total_mb") val ramTotalMb: Int,
    @SerialName("uptime_seconds") val uptimeSeconds: Int,
)

@Serializable
data class AppDto(
    val id: String,
    val name: String,
    val status: String,
    val repo: String? = null,
    val branch: String? = null,
    val port: Int? = null,
    val domain: String? = null,
    @SerialName("memory_mb") val memoryMb: Int? = null,
    @SerialName("started_at") val startedAt: String? = null,
    @SerialName("last_deploy") val lastDeploy: String? = null,
)

/**
 * Запрос на создание приложения через POST /apps.
 *
 * autostart=true (по умолчанию) — восстанавливать приложение после перезапуска
 * агента (например после ребута телефона). Сними чекбокс в UI если нужен одноразовый
 * запуск (например для миграции базы).
 */
@Serializable
data class InstallAppRequest(
    val id: String,
    val name: String,
    @SerialName("start_cmd") val startCmd: String,
    val port: Int? = null,
    val domain: String? = null,
    @SerialName("binary_url") val binaryUrl: String? = null,
    val autostart: Boolean = true,
)

@Serializable
data class CommandResultDto(
    val status: String,
    val id: String,
)

@Serializable
data class ErrorDto(
    val error: String,
)

@Serializable
data class LogEntryDto(
    val time: String,
    val app: String,
    val level: String,
    val source: String,
    val message: String,
)

@Serializable
data class TunnelStatusDto(
    val active: Boolean = false,
    @SerialName("public_url") val publicUrl: String? = null,
    @SerialName("started_at") val startedAt: String? = null,
    val provider: String = "none",
    @SerialName("last_error") val lastError: String? = null,
)
