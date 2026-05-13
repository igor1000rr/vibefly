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
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json

/**
 * Ktor-клиент к Go-агенту на 127.0.0.1:3001 (или публичному хосту).
 *
 * Реальный transport: HTTP против агента + WebSocket для стриминга логов.
 */
class AgentClient(
    override val baseUrl: String = DEFAULT_BASE_URL,
    private val tokenProvider: () -> String? = { null },
) : AgentApi {
    private val jsonCodec = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    val http: HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(jsonCodec) }
        install(WebSockets)
        install(HttpTimeout) {
            // tunnel/start может ждать до 60с cloudflared startup,
            // поэтому request timeout высокий.
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 90_000
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

    override suspend fun health(): HealthDto = http.get("$baseUrl/health").body()
    override suspend fun systemMetrics(): SystemMetricsDto = http.get("$baseUrl/system").body()
    override suspend fun listApps(): List<AppDto> = http.get("$baseUrl/apps").body()
    override suspend fun getApp(id: String): AppDto = http.get("$baseUrl/apps/$id").body()

    override suspend fun restartApp(id: String): CommandResultDto =
        http.post("$baseUrl/apps/$id/restart").body()
    override suspend fun stopApp(id: String): CommandResultDto =
        http.post("$baseUrl/apps/$id/stop").body()
    override suspend fun startApp(id: String): CommandResultDto =
        http.post("$baseUrl/apps/$id/start").body()
    override suspend fun uninstallApp(id: String): CommandResultDto =
        http.post("$baseUrl/apps/$id").body()

    override suspend fun recentLogs(id: String, lines: Int): List<LogEntryDto> =
        http.get("$baseUrl/apps/$id/logs") {
            parameter("lines", lines)
        }.body()

    override fun streamLogs(id: String): Flow<LogEntryDto> = channelFlow {
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

    // ===== Marketplace =====

    override suspend fun marketplaceList(): List<MarketplaceTemplateDto> =
        http.get("$baseUrl/marketplace").body()

    override suspend fun marketplaceGet(id: String): MarketplaceTemplateDto =
        http.get("$baseUrl/marketplace/$id").body()

    override suspend fun marketplaceInstall(templateId: String, req: MarketplaceInstallRequest) {
        http.post("$baseUrl/marketplace/$templateId/install") {
            contentType(ContentType.Application.Json)
            setBody(req)
        }
    }

    // ===== Tunnel =====

    override suspend fun tunnelStatus(): TunnelStatusDto =
        http.get("$baseUrl/tunnel").body()

    override suspend fun tunnelStart(): TunnelStatusDto =
        http.post("$baseUrl/tunnel/start").body()

    override suspend fun tunnelStop(): TunnelStatusDto =
        http.post("$baseUrl/tunnel/stop").body()

    override fun close() {
        http.close()
    }

    /**
     * Шлёт значение в канал, тихо игнорируя случай переполнения буфера или закрытого канала.
     * trySend возвращает ChannelResult — проверяем флаг isSuccess вручную, потому что
     * onFailure требует opt-in в некоторых версиях kotlinx-coroutines.
     */
    private fun <T> ProducerScope<T>.sendOrSkip(value: T) {
        val result = trySend(value)
        if (!result.isSuccess) {
            // slow consumer или канал закрыт — пропускаем
        }
    }

    companion object {
        const val DEFAULT_BASE_URL: String = "http://127.0.0.1:3001"
    }
}
