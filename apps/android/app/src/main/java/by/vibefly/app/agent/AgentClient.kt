package by.vibefly.app.agent

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor-клиент к Go-агенту на 127.0.0.1:3001.
 *
 * Авторизация — Bearer токен. Токен хранится в EncryptedSharedPreferences на Android-стороне,
 * а агент его читает из agent.toml. На старте фазы 1 — пустой, auth отключён.
 */
class AgentClient(
    val baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: () -> String? = { null },
) {
    val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                isLenient = true
            })
        }
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

    fun close() {
        http.close()
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:3001"
    }
}
