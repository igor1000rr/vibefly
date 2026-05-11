package by.vibefly.app.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json

/**
 * Ktor-клиент к Go-агенту на 127.0.0.1:3001.
 *
 * Авторизация — Bearer токен. Хранится в EncryptedSharedPreferences на Android-стороне.
 */
class AgentClient(
    val baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: () -> String? = { null },
) {
    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(jsonCodec) }
        install(WebSockets)
        install(HttpTimeout) {
            requestTimeoutMillis = 5_000
            connectTimeoutMillis = 2_000
            socketTimeoutMillis = 5_000
        }
        install(HttpRequestRetry) {
            retryOnExceptionIf(maxRetries = 2) { _, cause ->
                cause !is io.ktor.client.plugins.HttpRequestTimeoutException
            }
            exponentialDelay()
        }
        defaultRequest {
            headers {
                tokenProvider()?.takeIf { it.isNotBlank() }?.let { token ->
                    append(HttpHeaders.Authorization, "Bearer $token")
                }
            }
        }
    }

    suspend fun health(): HealthDto = http.get("$baseUrl/health").body()

    suspend fun systemMetrics(): SystemMetricsDto = http.get("$baseUrl/system").body()

    suspend fun listApps(): List<AppDto> = http.get("$baseUrl/apps").body()

    suspend fun getApp(id: String): AppDto = http.get("$baseUrl/apps/$id").body()

    suspend fun restartApp(id: String): CommandResultDto =
        http.post("$baseUrl/apps/$id/restart").body()

    suspend fun stopApp(id: String): CommandResultDto =
        http.post("$baseUrl/apps/$id/stop").body()

    suspend fun recentLogs(id: String, lines: Int = 100): List<LogEntryDto> =
        http.get("$baseUrl/apps/$id/logs") {
            parameter("lines", lines)
        }.body()

    /**
     * Стрим логов по WebSocket. Возвращает cold Flow — подписка живёт пока есть коллектор.
     * baseUrl с http(s)://... автоматически переводится в ws(s)://...
     */
    fun streamLogs(id: String): Flow<LogEntryDto> = channelFlow {
        val wsUrl = baseUrl
            .replaceFirst("http://", "ws://")
            .replaceFirst("https://", "wss://")
        http.webSocket(urlString = "$wsUrl/apps/$id/logs/stream") {
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    runCatching {
                        jsonCodec.decodeFromString(LogEntryDto.serializer(), frame.readText())
                    }.onSuccess { entry -> sendOrSkip(entry) }
                }
            }
        }
    }

    fun close() {
        http.close()
    }

    private fun <T> ProducerScope<T>.sendOrSkip(value: T) {
        trySend(value).onFailure { /* slow consumer */ }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:3001"
    }
}
