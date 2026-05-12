package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.AiClient
import by.vibefly.app.agent.AiStreamEvent
import by.vibefly.app.agent.ChatMessage
import by.vibefly.app.agent.StubAiClient
import by.vibefly.app.data.ServiceLocator
import by.vibefly.app.data.ToolRegistry
import by.vibefly.app.data.ToolResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Состояние экрана чата.
 *
 * messages — иммутабельный список сообщений (актуальный snapshot).
 * pendingText — текст, который AI стримит прямо сейчас (растёт по букве),
 *               отображается как последний "живой" пузырь.
 */
data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val pendingText: String = "",
    val sending: Boolean = false,
    val error: String? = null,
)

/**
 * ChatViewModel — хранилище состояния чата и оркестратор обмена с AiClient + ToolRegistry.
 *
 * Жизненный цикл: переживает rotation, привязан к навигационной composable.
 * В фазе 4 StubAiClient заменится на реальный CloudflareProxyAiClient — список
 * tool definitions при этом останется тем же, так как ToolRegistry уже работает.
 *
 * applyApproval сейчас реально дёргает ToolRegistry.execute(), и результат
 * летит обратно AI как следующее сообщение от пользователя (для multi-turn
 * tool-use). С StubAiClient мульти-тёрн не запускается, но интерфейс уже есть.
 */
class ChatViewModel(
    private val ai: AiClient = StubAiClient(),
    private val toolRegistry: ToolRegistry? = runCatching { ServiceLocator.tools() }.getOrNull(),
    initialMessages: List<ChatMessage> = demoMessages(),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(messages = initialMessages))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    fun send(text: String) {
        if (text.isBlank() || _state.value.sending) return

        val userMessage = ChatMessage.User(text = text.trim(), key = nextKey())
        _state.update {
            it.copy(
                messages = it.messages + userMessage,
                sending = true,
                pendingText = "",
                error = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                val tools = toolRegistry?.definitions() ?: emptyList()
                ai.chat(_state.value.messages, tools).collect { event ->
                    handleEvent(event)
                }
            }.onFailure { t ->
                _state.update {
                    it.copy(sending = false, error = t.localizedMessage)
                }
            }
        }
    }

    private fun handleEvent(event: AiStreamEvent) {
        when (event) {
            is AiStreamEvent.TextDelta -> {
                _state.update { it.copy(pendingText = it.pendingText + event.text) }
            }
            is AiStreamEvent.ToolCalled -> {
                // Безопасный тул — выполняем сразу, опасный — превращаем в Approval-карточку.
                if (toolRegistry?.requiresApproval(event.name) == true) {
                    pushApproval(
                        title = "⚒ ${event.name}",
                        code = formatArgsAsCode(event.args),
                        description = "AI просит запустить «${event.name}». Подтверди, если согласен.",
                        toolName = event.name,
                        toolArgs = event.args,
                    )
                } else {
                    pushToolsBubble(event.name, event.args)
                    runToolInBackground(event.name, event.args)
                }
            }
            is AiStreamEvent.ApprovalRequested -> {
                pushApproval(
                    title = event.title,
                    code = event.code,
                    description = event.description,
                    toolName = event.toolName,
                    toolArgs = event.toolArgs,
                )
            }
            is AiStreamEvent.Error -> {
                _state.update { it.copy(sending = false, error = event.message, pendingText = "") }
            }
            AiStreamEvent.Done -> {
                _state.update { snap ->
                    val pending = snap.pendingText.trim()
                    val updated = if (pending.isNotEmpty()) {
                        snap.messages + ChatMessage.Bot(text = pending, key = nextKey())
                    } else snap.messages
                    snap.copy(messages = updated, pendingText = "", sending = false)
                }
            }
        }
    }

    private fun pushToolsBubble(name: String, args: Map<String, String>) {
        _state.update {
            val toolsMessage = ChatMessage.Tools(
                intro = "Запускаю $name…",
                calls = listOf("$name(${args.entries.joinToString { "${it.key}=${it.value}" }})"),
                key = nextKey(),
            )
            it.copy(messages = it.messages + toolsMessage)
        }
    }

    private fun pushApproval(
        title: String,
        code: String,
        description: String,
        toolName: String,
        toolArgs: Map<String, String>,
    ) {
        _state.update {
            val approval = ChatMessage.Approval(
                title = title,
                code = code,
                description = description,
                toolName = toolName,
                toolArgs = toolArgs,
                key = nextKey(),
            )
            it.copy(messages = it.messages + approval)
        }
    }

    /**
     * Запустить tool в фоне (без approval). Результат бросается в чат
     * как Bot-сообщение, и в будущем должен лететь обратно AI как tool_result.
     */
    private fun runToolInBackground(name: String, args: Map<String, String>) {
        val registry = toolRegistry ?: return
        viewModelScope.launch {
            val result = registry.execute(name, args)
            appendToolResult(name, result)
        }
    }

    private fun appendToolResult(name: String, result: ToolResult) {
        val text = when (result) {
            is ToolResult.Ok -> "✓ $name:\n${result.payload}"
            is ToolResult.Failed -> "✗ $name failed: ${result.reason}"
            ToolResult.Rejected -> "⊘ $name отменён"
        }
        _state.update {
            it.copy(messages = it.messages + ChatMessage.Bot(text = text, key = nextKey()))
        }
    }

    /**
     * Подтверждение опасного действия в Approval — реально вызывает tool через ToolRegistry.
     * В фазе 4 результат будет улетать AI как next user-message, чтобы AI мог продолжить
     * рассуждение. Сейчас просто выводится в чат.
     */
    fun applyApproval(approval: ChatMessage.Approval) {
        val registry = toolRegistry
        if (registry == null || approval.toolName == null) {
            // Без registry или toolName — просто визуальная заглушка.
            _state.update {
                it.copy(
                    messages = it.messages + ChatMessage.Bot(
                        text = "✓ Применил: ${approval.title} (заглушка)",
                        key = nextKey(),
                    )
                )
            }
            return
        }
        viewModelScope.launch {
            val result = registry.execute(approval.toolName, approval.toolArgs)
            appendToolResult(approval.toolName, result)
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    private fun formatArgsAsCode(args: Map<String, String>): String =
        if (args.isEmpty()) "()"
        else args.entries.joinToString("\n") { "${it.key} = ${it.value}" }
}

// ─── ID-генератор и demo-наполнение ──────────────────────────────────────────

/**
 * Локальный счётчик ID для ChatMessage. Уникален в рамках процесса; стабильность
 * между запусками не требуется — история чата пока не персистится.
 */
private var chatKeyCounter: Long = 1_000L
fun nextKey(): Long = ++chatKeyCounter

fun demoMessages(): List<ChatMessage> = listOf(
    ChatMessage.TimeMarker("сегодня · 9:38", nextKey()),
    ChatMessage.Bot("Ом мане падме хунг.", nextKey()),
    ChatMessage.Bot("Я Vibe AI — ассистент твоего сервера. С чего начнём?", nextKey()),
    ChatMessage.TimeMarker("9:41", nextKey()),
    ChatMessage.User("У меня лагает azcrm последние полчаса", nextKey()),
    ChatMessage.User("Глянь, что не так", nextKey()),
    ChatMessage.Tools(
        intro = "Смотрю логи, метрики и медленные запросы…",
        calls = listOf(
            "get_logs(azcrm, 30m)",
            "query_metrics(cpu, ram)",
            "db_query(slow)",
        ),
        key = nextKey(),
    ),
    ChatMessage.Bot(
        "Таблица messages разрослась до 2.4M строк. Нет индекса по created_at — пагинация делает full scan.",
        nextKey(),
    ),
    ChatMessage.Approval(
        title = "⚒ предлагаю миграцию",
        code = "CREATE INDEX CONCURRENTLY\nidx_messages_created_at\nON messages(created_at);",
        description = "~40 секунд, без блокировок. Ускорит листинг в 50–100×.",
        toolName = "db_migrate",
        toolArgs = mapOf(
            "sql" to "CREATE INDEX CONCURRENTLY idx_messages_created_at ON messages(created_at);",
        ),
        key = nextKey(),
    ),
)
