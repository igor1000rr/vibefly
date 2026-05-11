package by.vibefly.app.agent

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor-клиент к Go-агенту внутри rootfs. Агент слушает на 127.0.0.1:3001.
 *
 * До фазы 1 реальные вызовы отсутствуют, UI питается синтетикой из fakes.
 */
class AgentClient(
    val baseUrl: String = DEFAULT_BASE_URL,
) {
    val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
        install(WebSockets)
    }

    fun close() {
        http.close()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:3001"
    }
}
