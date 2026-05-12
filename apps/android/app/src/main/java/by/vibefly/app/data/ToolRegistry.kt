package by.vibefly.app.data

import by.vibefly.app.agent.AgentApi
import by.vibefly.app.agent.LogEntryDto
import by.vibefly.app.agent.SystemMetricsDto
import by.vibefly.app.agent.ToolDefinition
import by.vibefly.app.agent.ToolParameters
import by.vibefly.app.agent.ToolPropertySchema

/**
 * Результат выполнения tool — то, что AI получит как tool_result в следующей итерации.
 * JSON-сериализуется в простую строку при отправке обратно AI.
 */
sealed class ToolResult {
    data class Ok(val payload: String) : ToolResult()
    data class Failed(val reason: String) : ToolResult()
    /** Tool требует approval и был отклонён пользователем. */
    object Rejected : ToolResult()
}

/**
 * Запись в реестре: описание для AI (function calling JSON) + исполнитель.
 *
 * executor берёт сырые args, валидирует их, дёргает AgentApi, возвращает ToolResult.
 * Подпись suspend — большинство tool-ов делают сетевой вызов.
 */
data class RegisteredTool(
    val definition: ToolDefinition,
    val executor: suspend (args: Map<String, String>) -> ToolResult,
)

/**
 * Реестр инструментов для AI. Регистрирует пары (определение → executor) и выдаёт
 * их по имени. ChatViewModel получает список definitions для запроса к AiClient,
 * а при возврате tool_call дёргает `execute(name, args)`.
 */
class ToolRegistry(private val agentProvider: () -> AgentApi) {

    private val tools: Map<String, RegisteredTool> = buildMap {
        register(listAppsTool())
        register(getLogsTool())
        register(systemMetricsTool())
        register(restartAppTool())
        register(stopAppTool())
    }

    fun definitions(): List<ToolDefinition> = tools.values.map { it.definition }
    fun has(name: String): Boolean = tools.containsKey(name)
    fun requiresApproval(name: String): Boolean = tools[name]?.definition?.requiresApproval ?: false

    suspend fun execute(name: String, args: Map<String, String>): ToolResult {
        val tool = tools[name] ?: return ToolResult.Failed("Unknown tool: $name")
        return runCatching { tool.executor(args) }
            .getOrElse { t -> ToolResult.Failed(t.localizedMessage ?: t.javaClass.simpleName) }
    }

    // ─── Tool definitions + executors ─────────────────────────────────────────

    private fun listAppsTool() = RegisteredTool(
        definition = ToolDefinition(
            name = "list_apps",
            description = "Перечислить все приложения, развёрнутые на сервере, с их статусом, " +
                "потреблением памяти и доменом. Возвращает массив объектов.",
            parameters = ToolParameters(),
            requiresApproval = false,
        ),
        executor = { _ ->
            val apps = agentProvider().listApps()
            val payload = apps.joinToString(separator = "\n") { a ->
                "${a.id} status=${a.status} mem=${a.memoryMb ?: "?"}MB domain=${a.domain ?: "\u2014"}"
            }
            ToolResult.Ok(payload.ifEmpty { "no apps" })
        },
    )

    private fun getLogsTool() = RegisteredTool(
        definition = ToolDefinition(
            name = "get_logs",
            description = "Получить последние строки логов конкретного приложения по его id.",
            parameters = ToolParameters(
                properties = mapOf(
                    "app_id" to ToolPropertySchema(
                        type = "string",
                        description = "Идентификатор приложения, как в list_apps.",
                    ),
                    "lines" to ToolPropertySchema(
                        type = "integer",
                        description = "Сколько последних строк вернуть (default 50, max 500).",
                    ),
                ),
                required = listOf("app_id"),
            ),
            requiresApproval = false,
        ),
        executor = { args ->
            val id = args["app_id"]
                ?: return@RegisteredTool ToolResult.Failed("app_id is required")
            val lines = args["lines"]?.toIntOrNull()?.coerceIn(1, 500) ?: 50
            val logs = agentProvider().recentLogs(id, lines = lines)
            ToolResult.Ok(formatLogs(logs))
        },
    )

    private fun systemMetricsTool() = RegisteredTool(
        definition = ToolDefinition(
            name = "system_metrics",
            description = "Прочитать текущее состояние устройства: батарея, температура, CPU, RAM.",
            parameters = ToolParameters(),
            requiresApproval = false,
        ),
        executor = { _ ->
            val m = agentProvider().systemMetrics()
            ToolResult.Ok(formatMetrics(m))
        },
    )

    private fun restartAppTool() = RegisteredTool(
        definition = ToolDefinition(
            name = "restart_app",
            description = "Перезапустить приложение по id. Требует подтверждения пользователя, " +
                "так как прерывает текущие соединения.",
            parameters = ToolParameters(
                properties = mapOf(
                    "app_id" to ToolPropertySchema(
                        type = "string",
                        description = "Идентификатор приложения.",
                    ),
                ),
                required = listOf("app_id"),
            ),
            requiresApproval = true,
        ),
        executor = { args ->
            val id = args["app_id"]
                ?: return@RegisteredTool ToolResult.Failed("app_id is required")
            val result = agentProvider().restartApp(id)
            ToolResult.Ok("restarted ${result.id} \u2192 ${result.status}")
        },
    )

    private fun stopAppTool() = RegisteredTool(
        definition = ToolDefinition(
            name = "stop_app",
            description = "Остановить приложение по id. Требует подтверждения, так как приложение " +
                "перестанет отвечать на запросы.",
            parameters = ToolParameters(
                properties = mapOf(
                    "app_id" to ToolPropertySchema(
                        type = "string",
                        description = "Идентификатор приложения.",
                    ),
                ),
                required = listOf("app_id"),
            ),
            requiresApproval = true,
        ),
        executor = { args ->
            val id = args["app_id"]
                ?: return@RegisteredTool ToolResult.Failed("app_id is required")
            val result = agentProvider().stopApp(id)
            ToolResult.Ok("stopped ${result.id} \u2192 ${result.status}")
        },
    )

    // ─── Помощники форматирования ──────────────────────────────────────────

    private fun formatLogs(logs: List<LogEntryDto>): String {
        if (logs.isEmpty()) return "no log entries"
        return logs.joinToString("\n") { e ->
            "${e.time.substringAfter('T').take(8)} [${e.level}] ${e.source}: ${e.message}"
        }
    }

    private fun formatMetrics(m: SystemMetricsDto): String = buildString {
        append("battery=${m.batteryLevel}% (${m.batteryStatus}); ")
        append("temp=${m.temperatureC}\u00B0C; ")
        append("cpu=${m.cpuPercent}%; ")
        append("ram=${m.ramUsedMb}/${m.ramTotalMb}MB; ")
        append("uptime=${m.uptimeSeconds}s")
    }

    private fun MutableMap<String, RegisteredTool>.register(tool: RegisteredTool) {
        put(tool.definition.name, tool)
    }
}
