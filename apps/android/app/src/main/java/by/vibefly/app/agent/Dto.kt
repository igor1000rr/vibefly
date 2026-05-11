package by.vibefly.app.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * DTO объекты, зеркальные типам из apps/agent/internal/server.
 * Имена полей жёстко привязаны к JSON-ключам агента.
 */

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
    val time: String,
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

@Serializable
data class CommandResultDto(
    val status: String,
    val id: String,
)

@Serializable
data class ErrorDto(
    val error: String,
)

/**
 * Запись лога. Зеркальный type для apps/agent/internal/logs.Entry.
 */
@Serializable
data class LogEntryDto(
    val time: String,
    val app: String,
    val level: String,
    val source: String,
    val message: String,
)
