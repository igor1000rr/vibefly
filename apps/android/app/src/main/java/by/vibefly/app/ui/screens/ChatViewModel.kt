package by.vibefly.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import by.vibefly.app.agent.AiClient
import by.vibefly.app.agent.AiStreamEvent
import by.vibefly.app.agent.ChatMessage
import by.vibefly.app.agent.StubAiClient
import by.vibefly.app.agent.ToolDefinition
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
 * ChatViewModel — хранилище состояния чата и оркестратор обмена с AiClient.
 *
 * Жизненный цикл: переживает rotation, привязан к навигационной composable.
 * В фазе 4 StubAiClient заменится на реальный CloudflareProxyAiClient,
 * а ToolDefinition'ы появятся из ToolRegistry.
 */
class ChatViewModel(
    private val ai: AiClient = StubAiClient(),
    initialMessages: List<ChatMessage> = demoMessages(),
) : ViewModel() {

    private val _state = MutableStateFlow(ChatState(messages = initialMessages))
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val availableTools: List<ToolDefinition> = emptyList() // фаза 4 заполнит

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
                ai.chat(_state.value.messages, availableTools).collect { event ->
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
                _state.update {
                    val toolsMessage = ChatMessage.Tools(
                        intro = "Запускаю инструменты…",
                        calls = listOf(
                            "${event.name}(${event.args.entries.joinToString { "${it.key}=${it.value}" }})"
                        ),
                        key = nextKey(),
                    )
                    it.copy(messages = it.messages + toolsMessage)
                }
            }
            is AiStreamEvent.ApprovalRequested -> {
                _state.update {
                    val approval = ChatMessage.Approval(
                        title = event.title,
                        code = event.code,
                        description = event.description,
                        toolName = event.toolName,
                        toolArgs = event.toolArgs,
                        key = nextKey(),
                    )
                    it.copy(messages = it.messages + approval)
                }
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

    /**
     * Подтверждение опасного действия в Approval. В фазе 4 здесь будет вызов
     * соответствующего tool через AgentClient (например, миграция БД).
     */
    fun applyApproval(approval: ChatMessage.Approval) {
        viewModelScope.launch {
            _state.update { snap ->
                snap.copy(
                    messages = snap.messages + ChatMessage.Bot(
                        text = "✓ Применил: ${approval.title}. (заглушка — реальный tool call в фазе 4)",
                        key = nextKey(),
                    )
                )
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }
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
