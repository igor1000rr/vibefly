package by.vibefly.app.agent

import kotlinx.serialization.Serializable

/**
 * Доменная модель сообщения в чате с Vibe AI. Не сериализуется как DTO — это
 * UI/state-модель, которая существует в ViewModel и собирается из обмена с AiClient.
 *
 * Иерархия sealed класса соответствует тому, как сообщения отображаются:
 *  • TimeMarker — центровая метка времени "сегодня · 9:38"
 *  • Bot — серый пузырь от ассистента
 *  • User — синий пузырь от пользователя
 *  • Tools — список вызванных инструментов с галочками
 *  • Approval — жёлтая карточка с подтверждением опасного действия
 *
 * key — стабильный идентификатор для LazyColumn, уникальный в рамках сессии.
 */
sealed class ChatMessage {
    abstract val key: Long

    data class TimeMarker(val text: String, override val key: Long) : ChatMessage()
    data class Bot(val text: String, override val key: Long) : ChatMessage()
    data class User(val text: String, override val key: Long) : ChatMessage()
    data class Tools(
        val intro: String,
        val calls: List<String>,
        override val key: Long,
    ) : ChatMessage()
    data class Approval(
        val title: String,
        val code: String,
        val description: String,
        val toolName: String? = null,    // tool который будет вызван при apply
        val toolArgs: Map<String, String> = emptyMap(),
        override val key: Long,
    ) : ChatMessage()
}

/**
 * Описание AI-инструмента (function calling). Соответствует JSON Schema формату
 * OpenAI/Anthropic tools. В фазе 4 будет сериализоваться в API-запрос к AI.
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters,
    /** Если true — требует явного approval от пользователя (миграции, удаления, restart). */
    val requiresApproval: Boolean = false,
)

@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolPropertySchema> = emptyMap(),
    val required: List<String> = emptyList(),
)

@Serializable
data class ToolPropertySchema(
    val type: String,                  // string, number, integer, boolean
    val description: String,
    val enum: List<String>? = null,
)

/**
 * Событие в стриме от AiClient. Эмитится по мере ответа AI:
 *   TextDelta → TextDelta → … → ToolCall → ApprovalRequested → Final
 */
sealed class AiStreamEvent {
    data class TextDelta(val text: String) : AiStreamEvent()
    data class ToolCalled(val name: String, val args: Map<String, String>) : AiStreamEvent()
    data class ApprovalRequested(
        val title: String,
        val code: String,
        val description: String,
        val toolName: String,
        val toolArgs: Map<String, String>,
    ) : AiStreamEvent()
    data class Error(val message: String) : AiStreamEvent()
    object Done : AiStreamEvent()
}
