package by.vibefly.app.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * AiClient — абстракция над AI-провайдером (Claude/GPT/Llama).
 *
 * Реальная реализация в фазе 4 будет ходить в Cloudflare Worker proxy,
 * который скрывает API-ключ от мобильного клиента и проксирует к OpenRouter.
 * Worker URL и токен пользователя приходят из SettingsStore.
 *
 * Сейчас работает StubAiClient — отвечает echo'м, имитируя задержку, чтобы
 * UI ChatScreen можно было тестировать без backend'а.
 */
interface AiClient {
    /**
     * Отправить запрос с историей сообщений и доступными инструментами.
     * Возвращает Flow с инкрементальными событиями (текст, tool calls, approval).
     */
    fun chat(
        history: List<ChatMessage>,
        availableTools: List<ToolDefinition>,
    ): Flow<AiStreamEvent>
}

/**
 * Заглушка AI-клиента. Никуда не ходит — генерирует echo-ответ с фиксированной
 * задержкой между чанками, чтобы можно было визуально отлаживать стриминг.
 */
class StubAiClient : AiClient {
    override fun chat(
        history: List<ChatMessage>,
        availableTools: List<ToolDefinition>,
    ): Flow<AiStreamEvent> = flow {
        val lastUser = history.findLast { it is ChatMessage.User } as? ChatMessage.User
            ?: run {
                emit(AiStreamEvent.Done)
                return@flow
            }

        val reply = stubReplyFor(lastUser.text)
        // Имитируем стриминг — посимвольно по словам
        val words = reply.split(" ")
        for (word in words) {
            emit(AiStreamEvent.TextDelta("$word "))
            delay(40)
        }
        emit(AiStreamEvent.Done)
    }

    private fun stubReplyFor(userText: String): String {
        val lower = userText.lowercase()
        return when {
            "лагает" in lower || "lag" in lower ->
                "Похоже на проблему с производительностью. Покажу логи и медленные запросы. Полноценный AI включится в фазе 4 — пока я только UI-заглушка."
            "deploy" in lower || "задеплой" in lower ->
                "Деплой требует подтверждения. AI включится в фазе 4."
            "привет" in lower || "hello" in lower || "hi" in lower ->
                "Привет! Я Vibe AI — пока в режиме заглушки. Реальный ассистент со tool-calling появится в фазе 4."
            else -> "Принял: «$userText». Я пока stub-реализация AI — реальные ответы и tool-calling в фазе 4."
        }
    }
}
